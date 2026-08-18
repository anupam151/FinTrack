package com.cluster.fintrack;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

@SuppressLint("SetTextI18n")
public class AddFinMateActivity extends AppCompatActivity {

    private TextInputEditText etFinMateName, etWhatsAppNo, etEmail, etAddress;
    private RadioGroup radioGroupWhatsApp;
    private MaterialButton btnSave;
    private TextView tvActivityTitle;

    // Launchers for Permissions and Contact Picker
    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<Intent> contactPickerLauncher;

    private String editFinMateId = null;
    private long editTimestamp = 0;

    // Financial preserves for edit mode
    private double editRecCard = 0.0;
    private double editRecCash = 0.0;
    private double editPayable = 0.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize Launchers
        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        extractContactInfo(result.getData().getData());
                    }
                });

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) launchContactPicker();
                    else Toast.makeText(this, "Permission required to import contacts", Toast.LENGTH_SHORT).show();
                });

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_finmate);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAddFinMate), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initializeViews();
        checkForEditMode();
        setupClickListeners();
    }

    private void initializeViews() {
        tvActivityTitle = findViewById(R.id.tvActivityTitle);
        etFinMateName = findViewById(R.id.etSheetFinMateName);
        etWhatsAppNo = findViewById(R.id.etSheetContactNo);
        etEmail = findViewById(R.id.etSheetEmail);
        etAddress = findViewById(R.id.etSheetAddress);
        radioGroupWhatsApp = findViewById(R.id.radioGroupWhatsApp);
        btnSave = findViewById(R.id.btnSaveFinMate);
    }

    private void checkForEditMode() {
        if (getIntent() != null && getIntent().hasExtra("FINMATE_ID")) {
            editFinMateId = getIntent().getStringExtra("FINMATE_ID");
            tvActivityTitle.setText("Edit FinMate");
            btnSave.setText("Update FinMate");

            etFinMateName.setText(getIntent().getStringExtra("NAME"));
            etWhatsAppNo.setText(getIntent().getStringExtra("PHONE_NO"));
            etEmail.setText(getIntent().getStringExtra("EMAIL"));
            etAddress.setText(getIntent().getStringExtra("ADDRESS"));

            String waNo = getIntent().getStringExtra("WHATSAPP_NO");
            if (waNo != null && !waNo.isEmpty()) {
                ((RadioButton) findViewById(R.id.radioYes)).setChecked(true);
            } else {
                ((RadioButton) findViewById(R.id.radioNo)).setChecked(true);
            }

            editTimestamp = getIntent().getLongExtra("TIMESTAMP", System.currentTimeMillis());
            editRecCard = getIntent().getDoubleExtra("REC_CARD", 0.0);
            editRecCash = getIntent().getDoubleExtra("REC_CASH", 0.0);
            editPayable = getIntent().getDoubleExtra("PAYABLE", 0.0);
        } else {
            // Focus and pop keyboard automatically on new addition
            etFinMateName.requestFocus();
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), etFinMateName);
            controller.show(WindowInsetsCompat.Type.ime());
        }
    }

    private void setupClickListeners() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        findViewById(R.id.btnImportContact).setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            } else {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS);
            }
        });

        btnSave.setOnClickListener(v -> validateAndSave());
    }

    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void extractContactInfo(Uri contactUri) {
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                String name = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);

                if (number != null) {
                    number = number.replaceAll("[^0-9]", "");
                    if (number.length() >= 10) {
                        number = number.substring(number.length() - 10);
                    }
                }

                etFinMateName.setText(name);
                etWhatsAppNo.setText(number);
                Toast.makeText(this, "Contact Imported!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read contact", Toast.LENGTH_SHORT).show();
        }
    }

    private void validateAndSave() {
        String name = String.valueOf(etFinMateName.getText()).trim();
        String contactNo = String.valueOf(etWhatsAppNo.getText()).trim();
        String email = String.valueOf(etEmail.getText()).trim();
        String address = String.valueOf(etAddress.getText()).trim();

        if (TextUtils.isEmpty(name)) {
            etFinMateName.setError("Enter Name");
            return;
        }

        if (TextUtils.isEmpty(contactNo) || contactNo.length() < 10) {
            etWhatsAppNo.setError("Enter valid 10-digit number");
            return;
        }

        int selectedId = radioGroupWhatsApp.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Please select if this number is on WhatsApp", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean isWhatsApp = (selectedId == R.id.radioYes);
        String finalWhatsAppNo = isWhatsApp ? contactNo : "";

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String finMateId = editFinMateId != null ? editFinMateId : db.collection("Users").document(userId).collection("FinMates").document().getId();
        long timestamp = editFinMateId != null ? editTimestamp : System.currentTimeMillis();

        FinMate finMate = new FinMate(finMateId, name, contactNo, finalWhatsAppNo, email, address, timestamp);

        // Restore financial amounts in case of edit
        if (editFinMateId != null) {
            finMate.setReceivableCardAmount(editRecCard);
            finMate.setReceivableCashAmount(editRecCash);
            finMate.setPayableAmount(editPayable);
        }

        btnSave.setEnabled(false);
        btnSave.setText(editFinMateId != null ? "Updating..." : "Saving...");

        db.collection("Users").document(userId).collection("FinMates").document(finMateId)
                .set(finMate)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, editFinMateId != null ? "FinMate Updated!" : "FinMate Saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                        Toast.makeText(this, "Failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        btnSave.setText(editFinMateId != null ? "Update FinMate" : "Save FinMate");
                    }
                });
    }

    // Bulletproof Keyboard Dispatch for clearing focus when tapping outside EditTexts
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof android.widget.EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    boolean clickedAnotherEditText = false;
                    int[] editIds = {
                            R.id.etSheetFinMateName, R.id.etSheetContactNo,
                            R.id.etSheetEmail, R.id.etSheetAddress
                    };
                    Rect rect = new Rect();
                    for (int id : editIds) {
                        View editView = findViewById(id);
                        if (editView != null && editView.isShown()) {
                            editView.getGlobalVisibleRect(rect);
                            if (rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                                clickedAnotherEditText = true;
                                break;
                            }
                        }
                    }
                    if (!clickedAnotherEditText) {
                        v.clearFocus();
                        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), v);
                        controller.hide(WindowInsetsCompat.Type.ime());
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}