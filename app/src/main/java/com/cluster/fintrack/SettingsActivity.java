package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

@SuppressWarnings("deprecation")
public class SettingsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // Contact Picker Variables
    private TextInputEditText currentEtFinMateName;
    private TextInputEditText currentEtWhatsAppNo;
    private androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher;
    private androidx.activity.result.ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize Contact Picker Launcher
        contactPickerLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        android.net.Uri contactUri = result.getData().getData();
                        extractContactInfo(contactUri);
                    }
                });

        // 2. Initialize Permission Launcher
        requestPermissionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchContactPicker();
                    } else {
                        Toast.makeText(this, "Permission required to import contacts", Toast.LENGTH_SHORT).show();
                    }
                });

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        drawerLayout = findViewById(R.id.drawerLayoutSettings);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            View mainContent = findViewById(R.id.mainSettingsContent);
            mainContent.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationViewSettings);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageView ivMenuDrawerSettings = findViewById(R.id.ivMenuDrawerSettings);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);
        TextView btnAddCardSettings = findViewById(R.id.btnAddCardSettings);
        TextView btnAddFinMateSettings = findViewById(R.id.btnAddFinMateSettings);
        LinearLayout btnCurrency = findViewById(R.id.btnCurrency);
        TextView btnExportData = findViewById(R.id.btnExportData);
        TextView btnSupport = findViewById(R.id.btnSupport);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        }

        ivMenuDrawerSettings.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_drawer_dashboard) {
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            } else if (id == R.id.nav_drawer_cards) {
                Intent intent = new Intent(SettingsActivity.this, CardsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            } else if (id == R.id.nav_drawer_ledger) {
                Intent intent = new Intent(SettingsActivity.this, FinMatesActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            } else if (id == R.id.nav_drawer_signout) {
                FirebaseAuth.getInstance().signOut();

                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

                GoogleSignIn.getClient(SettingsActivity.this, gso).signOut().addOnCompleteListener(task -> {
                    Toast.makeText(SettingsActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        btnAddCardSettings.setOnClickListener(v -> showAddCardBottomSheet());
        btnAddFinMateSettings.setOnClickListener(v -> showAddFinMateBottomSheet());

        btnCurrency.setOnClickListener(v -> Toast.makeText(this, "Currency selection coming soon", Toast.LENGTH_SHORT).show());
        btnExportData.setOnClickListener(v -> Toast.makeText(this, "Exporting Ledger to CSV...", Toast.LENGTH_SHORT).show());
        btnSupport.setOnClickListener(v -> Toast.makeText(this, "Opening Support Email...", Toast.LENGTH_SHORT).show());
    }

    // --- CONTACT PICKER HELPER METHODS ---
    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void extractContactInfo(android.net.Uri contactUri) {
        String[] projection = new String[]{
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (android.database.Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER);

                String name = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);

                if (number != null) {
                    number = number.replaceAll("[^0-9]", "");
                    if (number.length() >= 10) {
                        number = number.substring(number.length() - 10);
                    }
                }

                if (currentEtFinMateName != null) currentEtFinMateName.setText(name);
                if (currentEtWhatsAppNo != null) currentEtWhatsAppNo.setText(number);

                Toast.makeText(this, "Contact Imported!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read contact", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void showAddCardBottomSheet() {
        // 1. Create dialog and override touch events to keep keyboard open when switching between text boxes
        BottomSheetDialog dialog = new BottomSheetDialog(this) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    View v = getCurrentFocus();
                    if (v instanceof android.widget.EditText) {
                        android.graphics.Rect outRect = new android.graphics.Rect();
                        v.getGlobalVisibleRect(outRect);

                        // Check if the user touched outside the currently focused text box
                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            // Only close keyboard if the newly touched focus is NOT another EditText
                            View newFocus = getWindow() != null ? getWindow().getCurrentFocus() : null;
                            if (!(newFocus instanceof android.widget.EditText)) {
                                v.clearFocus();
                                if (getWindow() != null) {
                                    androidx.core.view.WindowInsetsControllerCompat controller =
                                            new androidx.core.view.WindowInsetsControllerCompat(getWindow(), v);
                                    controller.hide(androidx.core.view.WindowInsetsCompat.Type.ime());
                                }
                            }
                        }
                    }
                }
                return super.dispatchTouchEvent(event);
            }
        };

        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_card, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        // 2. DEFINE VIEWS
        TextView title = view.findViewById(R.id.tvCardSheetTitle);
        com.google.android.material.textfield.MaterialAutoCompleteTextView spinBankName = view.findViewById(R.id.spinSheetBankName);
        TextInputEditText etCardName = view.findViewById(R.id.etSheetCardName);
        TextInputEditText etTotalLimit = view.findViewById(R.id.etSheetTotalLimit);
        TextInputEditText etBillingDay = view.findViewById(R.id.etSheetBillingDay);

        com.google.android.material.card.MaterialCardView cardColorPreview = view.findViewById(R.id.cardColorPreview);
        ImageView btnResetColor = view.findViewById(R.id.btnResetColor);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveCard);

        title.setText("Add Credit Card");
        btnSave.setText("Save Card");

        // 3. SET AUTO-FOCUS & OPEN KEYBOARD IMMEDIATELY ON CARD NAME
        dialog.setOnShowListener(d -> {
            etCardName.requestFocus();
            if (dialog.getWindow() != null) {
                androidx.core.view.WindowInsetsControllerCompat controller =
                        new androidx.core.view.WindowInsetsControllerCompat(dialog.getWindow(), etCardName);
                controller.show(androidx.core.view.WindowInsetsCompat.Type.ime());
            }
        });

        // 4. SETUP DROPDOWN (Will NOT open automatically on click, only when typing)
        spinBankName.setAdapter(getBankArrayAdapter());
        spinBankName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (spinBankName.hasFocus()) {
                    spinBankName.showDropDown();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        final int[] currentColor = {android.graphics.Color.parseColor("#082561")};
        final int defaultColor = android.graphics.Color.parseColor("#082561");

        cardColorPreview.setOnClickListener(v -> {
            yuku.ambilwarna.AmbilWarnaDialog colorPickerDialog = new yuku.ambilwarna.AmbilWarnaDialog(this, currentColor[0], new yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onCancel(yuku.ambilwarna.AmbilWarnaDialog dialog) {}

                @Override
                public void onOk(yuku.ambilwarna.AmbilWarnaDialog dialog, int color) {
                    currentColor[0] = color;
                    cardColorPreview.setCardBackgroundColor(color);
                }
            });
            colorPickerDialog.show();
        });

        btnResetColor.setOnClickListener(v -> {
            currentColor[0] = defaultColor;
            cardColorPreview.setCardBackgroundColor(defaultColor);
        });

        btnSave.setOnClickListener(v -> {
            String cardName = String.valueOf(etCardName.getText()).trim();
            String bankName = String.valueOf(spinBankName.getText()).trim();
            String limitStr = String.valueOf(etTotalLimit.getText()).trim();
            String billingDayStr = String.valueOf(etBillingDay.getText()).trim();

            if (TextUtils.isEmpty(cardName)) { etCardName.setError("Required"); return; }
            if (TextUtils.isEmpty(bankName)) { spinBankName.setError("Required"); return; }

            // --- STRICT BANK VALIDATION ---
            String[] validBanks = new String[]{
                    "AU Small Finance Bank", "American Express", "Axis Bank", "Bandhan Bank",
                    "Bank of Baroda", "Bank of India", "Bank of Maharashtra", "Barclays Bank",
                    "Baroda Gujarat Gramin Bank", "Baroda Rajasthan Kshetriya Gramin Bank",
                    "Baroda U.P. Bank", "CSB Bank", "Canara Bank", "Capital Small Finance Bank",
                    "Central Bank of India", "City Union Bank", "Cosmos Co-operative Bank",
                    "DBS Bank", "DCB Bank", "Deutsche Bank", "Dhanlaxmi Bank",
                    "Equitas Small Finance Bank", "ESAF Small Finance Bank", "Federal Bank",
                    "First Abu Dhabi Bank", "HDFC Bank", "HSBC Bank", "ICICI Bank Limited",
                    "IDFC FIRST Bank", "Indian Bank", "Indian Overseas Bank", "IndusInd Bank",
                    "Jammu & Kashmir Bank", "Jana Small Finance Bank", "Karnataka Bank",
                    "Karur Vysya Bank", "Kerala Gramin Bank", "Kotak Mahindra Bank",
                    "Nainital Bank", "Punjab & Sind Bank", "Punjab National Bank", "RBL Bank",
                    "Saraswat Co-operative Bank", "SBM Bank India", "South Indian Bank",
                    "Standard Chartered Bank", "State Bank of India", "Suryoday Small Finance Bank",
                    "SVC Co-operative Bank", "Tamilnad Mercantile Bank", "UCO Bank",
                    "Ujjivan Small Finance Bank", "Union Bank of India", "Utkarsh Small Finance Bank",
                    "YES Bank"
            };

            boolean isValidBank = false;
            for (String bank : validBanks) {
                if (bank.equalsIgnoreCase(bankName)) {
                    isValidBank = true;
                    break;
                }
            }

            if (!isValidBank) {
                spinBankName.setError("Please select a valid bank from the list");
                spinBankName.requestFocus();
                return;
            }
            // ------------------------------

            if (TextUtils.isEmpty(limitStr)) { etTotalLimit.setError("Required"); return; }
            if (TextUtils.isEmpty(billingDayStr)) { etBillingDay.setError("Required"); return; }

            double totalLimit = Double.parseDouble(limitStr);
            int billingDay = Integer.parseInt(billingDayStr);

            String themeColorHex = String.format("#%06X", (0xFFFFFF & currentColor[0]));

            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String newCardId = db.collection("Users").document(userId).collection("Cards").document().getId();

            Card newCard = new Card(newCardId, bankName, cardName, totalLimit, billingDay, themeColorHex, System.currentTimeMillis());

            btnSave.setEnabled(false);
            btnSave.setText("Saving...");

            db.collection("Users").document(userId).collection("Cards").document(newCardId)
                    .set(newCard)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Card Saved Successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                            btnSave.setText("Save Card");
                        }
                    });
        });

        dialog.show();
    }
    private android.widget.ArrayAdapter<String> getBankArrayAdapter() {
        java.util.List<String> bankList = java.util.Arrays.asList(
                "AU Small Finance Bank", "American Express", "Axis Bank", "Bandhan Bank",
                "Bank of Baroda", "Bank of India", "Bank of Maharashtra", "Barclays Bank",
                "Baroda Gujarat Gramin Bank", "Baroda Rajasthan Kshetriya Gramin Bank",
                "Baroda U.P. Bank", "CSB Bank", "Canara Bank", "Capital Small Finance Bank",
                "Central Bank of India", "City Union Bank", "Cosmos Co-operative Bank",
                "DBS Bank", "DCB Bank", "Deutsche Bank", "Dhanlaxmi Bank",
                "Equitas Small Finance Bank", "ESAF Small Finance Bank", "Federal Bank",
                "First Abu Dhabi Bank", "HDFC Bank", "HSBC Bank", "ICICI Bank Limited",
                "IDFC FIRST Bank", "Indian Bank", "Indian Overseas Bank", "IndusInd Bank",
                "Jammu & Kashmir Bank", "Jana Small Finance Bank", "Karnataka Bank",
                "Karur Vysya Bank", "Kerala Gramin Bank", "Kotak Mahindra Bank",
                "Nainital Bank", "Punjab & Sind Bank", "Punjab National Bank", "RBL Bank",
                "Saraswat Co-operative Bank", "SBM Bank India", "South Indian Bank",
                "Standard Chartered Bank", "State Bank of India", "Suryoday Small Finance Bank",
                "SVC Co-operative Bank", "Tamilnad Mercantile Bank", "UCO Bank",
                "Ujjivan Small Finance Bank", "Union Bank of India", "Utkarsh Small Finance Bank",
                "YES Bank"
        );

        // We create a custom adapter to enable "Contains" matching instead of just "Starts With"
        return new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new java.util.ArrayList<>(bankList)) {
            @androidx.annotation.NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        if (constraint == null || constraint.length() == 0) {
                            results.values = bankList; // Show all if search is empty
                            results.count = bankList.size();
                        } else {
                            java.util.List<String> filteredList = new java.util.ArrayList<>();
                            String filterPattern = constraint.toString().toLowerCase().trim();

                            // Check if the bank name contains whatever the user typed
                            for (String bank : bankList) {
                                if (bank.toLowerCase().contains(filterPattern)) {
                                    filteredList.add(bank);
                                }
                            }
                            results.values = filteredList;
                            results.count = filteredList.size();
                        }
                        return results;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results.values != null) {
                            addAll((java.util.List<String>) results.values);
                        }
                        notifyDataSetChanged(); // Update the dropdown instantly!
                    }
                };
            }
        };
    }

    @SuppressLint("SetTextI18n")
    private void showAddFinMateBottomSheet() {
        // Create dialog and override touch events to close keyboard when clicking out-side of EditTexts
        BottomSheetDialog dialog = new BottomSheetDialog(this) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    View v = getCurrentFocus();
                    if (v instanceof android.widget.EditText) {
                        android.graphics.Rect outRect = new android.graphics.Rect();
                        v.getGlobalVisibleRect(outRect);
                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            v.clearFocus();
                            android.view.inputmethod.InputMethodManager imm =
                                    (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                            }
                        }
                    }
                }
                return super.dispatchTouchEvent(event);
            }
        };
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_finmate, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvSheetTitle);
        TextView btnImportContact = view.findViewById(R.id.btnImportContact);

        currentEtFinMateName = view.findViewById(R.id.etSheetFinMateName);
        currentEtWhatsAppNo = view.findViewById(R.id.etSheetContactNo);

        TextInputEditText etEmail = view.findViewById(R.id.etSheetEmail);
        TextInputEditText etAddress = view.findViewById(R.id.etSheetAddress);
        RadioGroup radioGroupWhatsApp = view.findViewById(R.id.radioGroupWhatsApp);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveFinMate);

        title.setText("Add FinMate");
        btnSave.setText("Save FinMate");

        btnImportContact.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
            }
        });

        btnSave.setOnClickListener(v -> {
            String name = String.valueOf(currentEtFinMateName.getText()).trim();
            String contactNo = String.valueOf(currentEtWhatsAppNo.getText()).trim();
            String email = String.valueOf(etEmail.getText()).trim();
            String address = String.valueOf(etAddress.getText()).trim();

            if (TextUtils.isEmpty(name)) {
                currentEtFinMateName.setError("Enter Name");
                return;
            }

            if (TextUtils.isEmpty(contactNo) || contactNo.length() < 10) {
                currentEtWhatsAppNo.setError("Enter valid 10-digit number");
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
            String newFinMateId = db.collection("Users").document(userId).collection("FinMates").document().getId();

            FinMate newFinMate = new FinMate(newFinMateId, name, contactNo, finalWhatsAppNo, email, address, System.currentTimeMillis());

            btnSave.setEnabled(false);
            btnSave.setText("Saving...");

            db.collection("Users").document(userId).collection("FinMates").document(newFinMateId)
                    .set(newFinMate)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "FinMate Saved Successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                            btnSave.setText("Save FinMate");
                        }
                    });
        });

        dialog.show();
    }


}