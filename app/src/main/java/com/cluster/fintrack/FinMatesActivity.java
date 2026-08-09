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
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class FinMatesActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // RecyclerView & Adapter Variables
    private FinMateAdapter finMateAdapter;
    private List<FinMate> finMateList;
    private TextView tvEmptyFinMatesList;
    private SwipeRefreshLayout swipeRefreshFinMates;

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
        setContentView(R.layout.activity_finmates);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        drawerLayout = findViewById(R.id.drawerLayoutFinMates);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            findViewById(R.id.mainFinMates).setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationViewFinMates);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageView ivMenuDrawerFinMates = findViewById(R.id.ivMenuDrawerFinMates);
        ivMenuDrawerFinMates.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // 3. Initialize RecyclerView & UI Components
        RecyclerView recyclerViewFinMates = findViewById(R.id.recyclerViewFinMates);
        tvEmptyFinMatesList = findViewById(R.id.tvEmptyFinMatesList);
        swipeRefreshFinMates = findViewById(R.id.swipeRefreshFinMates);

        recyclerViewFinMates.setLayoutManager(new LinearLayoutManager(this));
        finMateList = new ArrayList<>();

        // Pass long click listener for Edit/Delete action menu
        finMateAdapter = new FinMateAdapter(this, finMateList, this::showEditDeleteMenu);
        recyclerViewFinMates.setAdapter(finMateAdapter);

        // Swipe to Refresh listener
        swipeRefreshFinMates.setOnRefreshListener(this::fetchFinMatesFromFirestore);

        // Bottom Navigation Listeners
        LinearLayout navItemDashboard = findViewById(R.id.navItemDashboard);
        LinearLayout navItemCards = findViewById(R.id.navItemCards);
        LinearLayout navSetings = findViewById(R.id.navSetings);

        navItemDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        navItemCards.setOnClickListener(v -> {
            Intent intent = new Intent(this, CardsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        navSetings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.fabAddFinMate).setOnClickListener(v -> showAddEditFinMateBottomSheet(null));

        // Initial Fetch
        fetchFinMatesFromFirestore();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchFinMatesFromFirestore();
    }

    // --- FIRESTORE FETCH LOGIC ---
    private void fetchFinMatesFromFirestore() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshFinMates.setRefreshing(false);
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        swipeRefreshFinMates.setRefreshing(true);

        FirebaseFirestore.getInstance()
                .collection("Users")
                .document(userId)
                .collection("FinMates")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    int previousSize = finMateList.size();
                    finMateList.clear();

                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            FinMate finMate = doc.toObject(FinMate.class);
                            if (finMate != null) {
                                finMateList.add(finMate);
                            }
                        }
                        tvEmptyFinMatesList.setVisibility(View.GONE);
                    } else {
                        tvEmptyFinMatesList.setVisibility(View.VISIBLE);
                    }

                    int newSize = finMateList.size();
                    if (previousSize > 0) {
                        finMateAdapter.notifyItemRangeRemoved(0, previousSize);
                    }
                    if (newSize > 0) {
                        finMateAdapter.notifyItemRangeInserted(0, newSize);
                    }

                    swipeRefreshFinMates.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshFinMates.setRefreshing(false);
                    Toast.makeText(this, "Failed to load FinMates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // --- EDIT & DELETE POPUP MENU (LONG PRESS LOGIC) ---
    private void showEditDeleteMenu(FinMate finMate, View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenu().add(0, 1, 0, "Edit");
        popupMenu.getMenu().add(0, 2, 1, "Delete");

        popupMenu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showAddEditFinMateBottomSheet(finMate);
                return true;
            } else if (item.getItemId() == 2) {
                deleteFinMate(finMate);
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    private void deleteFinMate(FinMate finMate) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("Users")
                .document(userId)
                .collection("FinMates")
                .document(finMate.getFinMateId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    int index = finMateList.indexOf(finMate);
                    if (index != -1) {
                        finMateList.remove(index);
                        finMateAdapter.notifyItemRemoved(index);
                    }

                    if (finMateList.isEmpty()) {
                        tvEmptyFinMatesList.setVisibility(View.VISIBLE);
                    }

                    Toast.makeText(this, "FinMate Deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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
    private void showAddEditFinMateBottomSheet(FinMate finMateToEdit) {
        boolean isEditing = (finMateToEdit != null);
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_finmate, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        // Bindings
        TextView title = view.findViewById(R.id.tvSheetTitle);
        TextView btnImportContact = view.findViewById(R.id.btnImportContact);

        // Link to class-level variables so the Contact Picker can fill them when returning
        currentEtFinMateName = view.findViewById(R.id.etSheetFinMateName);
        currentEtWhatsAppNo = view.findViewById(R.id.etSheetContactNo);

        TextInputEditText etEmail = view.findViewById(R.id.etSheetEmail);
        TextInputEditText etAddress = view.findViewById(R.id.etSheetAddress);
        RadioGroup radioGroupWhatsApp = view.findViewById(R.id.radioGroupWhatsApp);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveFinMate);

        if (isEditing) {
            title.setText("Edit FinMate");
            btnSave.setText("Update FinMate");
            currentEtFinMateName.setText(finMateToEdit.getName());
            currentEtWhatsAppNo.setText(finMateToEdit.getPhoneNo());
            etEmail.setText(finMateToEdit.getEmail());
            etAddress.setText(finMateToEdit.getAddress());

            if (finMateToEdit.getWhatsappNo() != null && !finMateToEdit.getWhatsappNo().isEmpty()) {
                radioGroupWhatsApp.check(R.id.radioYes);
            } else {
                radioGroupWhatsApp.check(R.id.radioNo);
            }
        } else {
            title.setText("Add FinMate");
            btnSave.setText("Save FinMate");
        }

        // Contact Picker Logic
        btnImportContact.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
            }
        });

        // Save / Update Logic
        btnSave.setOnClickListener(v -> {
            String name = String.valueOf(currentEtFinMateName.getText()).trim();
            String contactNo = String.valueOf(currentEtWhatsAppNo.getText()).trim();
            String email = String.valueOf(etEmail.getText()).trim();
            String address = String.valueOf(etAddress.getText()).trim();

            // 1. Name Validation
            if (TextUtils.isEmpty(name)) {
                currentEtFinMateName.setError("Enter Name");
                return;
            }

            // 2. Contact Number Validation
            if (TextUtils.isEmpty(contactNo) || contactNo.length() < 10) {
                currentEtWhatsAppNo.setError("Enter valid 10-digit number");
                return;
            }

            // 3. WhatsApp Choice Validation
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

            String finMateId = isEditing ? finMateToEdit.getFinMateId() : db.collection("Users").document(userId).collection("FinMates").document().getId();
            long timestamp = isEditing ? finMateToEdit.getTimestamp() : System.currentTimeMillis();

            FinMate finMate = new FinMate(finMateId, name, contactNo, finalWhatsAppNo, email, address, timestamp);

            // Preserve existing financials if editing
            if (isEditing) {
                finMate.setReceivableCardAmount(finMateToEdit.getReceivableCardAmount());
                finMate.setReceivableCashAmount(finMateToEdit.getReceivableCashAmount());
                finMate.setPayableAmount(finMateToEdit.getPayableAmount());
            }

            btnSave.setEnabled(false);
            btnSave.setText(isEditing ? "Updating..." : "Saving...");

            db.collection("Users").document(userId).collection("FinMates").document(finMateId)
                    .set(finMate)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, isEditing ? "FinMate Updated!" : "FinMate Saved Successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();

                            if (isEditing) {
                                int index = finMateList.indexOf(finMateToEdit);
                                if (index != -1) {
                                    finMateList.set(index, finMate);
                                    finMateAdapter.notifyItemChanged(index);
                                } else {
                                    fetchFinMatesFromFirestore();
                                }
                            } else {
                                finMateList.add(0, finMate);
                                finMateAdapter.notifyItemInserted(0);
                                tvEmptyFinMatesList.setVisibility(View.GONE);
                            }
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(this, "Failed: " + errorMessage, Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                            btnSave.setText(isEditing ? "Update FinMate" : "Save FinMate");
                        }
                    });
        });

        dialog.show();
    }
}