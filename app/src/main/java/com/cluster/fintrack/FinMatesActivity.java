package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
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

@SuppressWarnings("deprecation")
public class FinMatesActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // UI Elements
    private RecyclerView recyclerViewFinMates;
    private TextView tvEmptyFinMatesList;
    private SwipeRefreshLayout swipeRefreshFinMates;
    private TextInputEditText etSearchFinMate;
    private ImageView ivSortFinMates;
    private ImageView ivSortOrder;

    // Adapter & Data Lists
    private FinMateAdapter finMateAdapter;
    private final List<FinMate> masterFinMateList = new ArrayList<>();
    private final List<FinMate> displayedFinMateList = new ArrayList<>();

    // Sorting State (0 = Recent, 1 = Name, 2 = Receivable, 3 = Payable)
    private int currentSortMode = 0;
    private boolean isAscending = false;

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
        navigationView.setItemIconTintList(null);

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        // Find and set up the Header Transaction Button
        ImageButton btnAddTransactionHeader = findViewById(R.id.btnAddTransactionHeader);
        if (btnAddTransactionHeader != null) {
            btnAddTransactionHeader.setOnClickListener(v -> {
                Intent intent = new Intent(FinMatesActivity.this, AddTransactionActivity.class);
                startActivity(intent);
            });
        }

        ImageView ivMenuDrawerFinMates = findViewById(R.id.ivMenuDrawerFinMates);
        ivMenuDrawerFinMates.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            drawerLayout.postDelayed(() -> {
                if (id == R.id.nav_drawer_dashboard) {
                    Intent intent = new Intent(FinMatesActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    disableWindowAnimations();
                    finish();
                } else if (id == R.id.nav_drawer_cards) {
                    Intent intent = new Intent(FinMatesActivity.this, CardsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    disableWindowAnimations();
                    finish();
                } else if (id == R.id.nav_drawer_ledger) {
                    Toast.makeText(FinMatesActivity.this, "Already on FinMates", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_drawer_signout) {
                    FirebaseAuth.getInstance().signOut();
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build();

                    GoogleSignIn.getClient(FinMatesActivity.this, gso).signOut().addOnCompleteListener(task -> {
                        Toast.makeText(FinMatesActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(FinMatesActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                }
            }, 250);
            return false;
        });

        // 3. Initialize UI Components
        recyclerViewFinMates = findViewById(R.id.recyclerViewFinMates);
        tvEmptyFinMatesList = findViewById(R.id.tvEmptyFinMatesList);
        swipeRefreshFinMates = findViewById(R.id.swipeRefreshFinMates);

        // Ensure these IDs match your XML layout exactly
        etSearchFinMate = findViewById(R.id.etSearchFinMate);
        ivSortFinMates = findViewById(R.id.ivSortFinMates);
        ivSortOrder = findViewById(R.id.ivSortOrderFinMates);

        recyclerViewFinMates.setLayoutManager(new LinearLayoutManager(this));

        // Use displayedFinMateList for the adapter
        finMateAdapter = new FinMateAdapter(this, displayedFinMateList, this::showEditDeleteMenu);
        recyclerViewFinMates.setAdapter(finMateAdapter);

        swipeRefreshFinMates.setOnRefreshListener(this::fetchFinMatesFromFirestore);

        // Setup Search and Sort UI Logic
        setupSearchAndSort();

        // Bottom Navigation Listeners
        LinearLayout navItemDashboard = findViewById(R.id.navItemDashboard);
        LinearLayout navItemCards = findViewById(R.id.navItemCards);
        LinearLayout navSetings = findViewById(R.id.navSetings);

        navItemDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            disableWindowAnimations();
            finish();
        });

        navItemCards.setOnClickListener(v -> {
            Intent intent = new Intent(this, CardsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            disableWindowAnimations();
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

    // --- ACTIVITY-LEVEL TOUCH EVENT TO DISMISS KEYBOARD ON SEARCH BOX ---
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            // Specifically targets the search box in FinMates Activity
            if (v != null && v.getId() == R.id.etSearchFinMate) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                // If tapped outside search box bounds
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    if (getWindow() != null) {
                        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), v);
                        controller.hide(WindowInsetsCompat.Type.ime());
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void disableWindowAnimations() {
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else {
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchFinMatesFromFirestore();
    }

    // --- SETUP SEARCH & SORT ---
    private void setupSearchAndSort() {
        if (etSearchFinMate == null || ivSortFinMates == null || ivSortOrder == null) return;

        etSearchFinMate.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFinMates(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        ivSortFinMates.setOnClickListener(v -> {
            androidx.appcompat.view.ContextThemeWrapper wrapper =
                    new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);
            PopupMenu popup = new PopupMenu(wrapper, v);

            popup.getMenu().add(0, 0, 0, "Recent Activity");
            popup.getMenu().add(0, 1, 0, "Name (A-Z)");
            popup.getMenu().add(0, 2, 0, "Total Receivable");
            popup.getMenu().add(0, 3, 0, "Total Payable");

            popup.setOnMenuItemClickListener(item -> {
                currentSortMode = item.getItemId();
                filterFinMates(etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
                return true;
            });
            popup.show();
        });

        ivSortOrder.setOnClickListener(v -> {
            isAscending = !isAscending;
            ivSortOrder.animate().rotation(isAscending ? 180f : 0f).setDuration(200).start();
            filterFinMates(etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
        });
    }

    // --- FILTER & SORT LOGIC ---
    @SuppressLint("NotifyDataSetChanged")
    private void filterFinMates(String query) {
        List<FinMate> tempFilteredList = new ArrayList<>();

        if (TextUtils.isEmpty(query)) {
            tempFilteredList.addAll(masterFinMateList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (FinMate finMate : masterFinMateList) {
                if (finMate.getName().toLowerCase().contains(lowerCaseQuery) ||
                        finMate.getPhoneNo().contains(lowerCaseQuery)) {
                    tempFilteredList.add(finMate);
                }
            }
        }

        if (currentSortMode == 0) { // Recent Activity
            tempFilteredList.sort((f1, f2) -> isAscending ?
                    Long.compare(f1.getTimestamp(), f2.getTimestamp()) :
                    Long.compare(f2.getTimestamp(), f1.getTimestamp()));
        } else if (currentSortMode == 1) { // Name
            tempFilteredList.sort((f1, f2) -> isAscending ?
                    f1.getName().compareToIgnoreCase(f2.getName()) :
                    f2.getName().compareToIgnoreCase(f1.getName()));
        } else if (currentSortMode == 2) { // Total Receivable (Card + Cash)
            tempFilteredList.sort((f1, f2) -> {
                double r1 = f1.getReceivableCardAmount() + f1.getReceivableCashAmount();
                double r2 = f2.getReceivableCardAmount() + f2.getReceivableCashAmount();
                return isAscending ? Double.compare(r1, r2) : Double.compare(r2, r1);
            });
        } else if (currentSortMode == 3) { // Total Payable
            tempFilteredList.sort((f1, f2) -> isAscending ?
                    Double.compare(f1.getPayableAmount(), f2.getPayableAmount()) :
                    Double.compare(f2.getPayableAmount(), f1.getPayableAmount()));
        }

        displayedFinMateList.clear();
        displayedFinMateList.addAll(tempFilteredList);
        finMateAdapter.notifyDataSetChanged();

        if (displayedFinMateList.isEmpty()) {
            tvEmptyFinMatesList.setVisibility(View.VISIBLE);
            recyclerViewFinMates.setVisibility(View.GONE);
        } else {
            tvEmptyFinMatesList.setVisibility(View.GONE);
            recyclerViewFinMates.setVisibility(View.VISIBLE);
        }
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
                    masterFinMateList.clear();

                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            FinMate finMate = doc.toObject(FinMate.class);
                            if (finMate != null) {
                                masterFinMateList.add(finMate);
                            }
                        }
                    }

                    // Push master list through the filter to render it
                    filterFinMates(etSearchFinMate != null && etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
                    swipeRefreshFinMates.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshFinMates.setRefreshing(false);
                    if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                    Toast.makeText(this, "Failed to load FinMates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // --- EDIT & DELETE POPUP MENU ---
    private void showEditDeleteMenu(FinMate finMate, View anchor) {
        androidx.appcompat.view.ContextThemeWrapper wrapper =
                new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);

        androidx.appcompat.widget.PopupMenu popup =
                new androidx.appcompat.widget.PopupMenu(wrapper, anchor, android.view.Gravity.END);

        popup.getMenu().add(0, 0, 0, "Edit FinMate");
        popup.getMenu().add(0, 1, 0, "Delete FinMate");

        popup.setForceShowIcon(true);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                showAddEditFinMateBottomSheet(finMate);
            } else if (item.getItemId() == 1) {
                showDeleteConfirmationDialog(finMate);
            }
            return true;
        });

        popup.show();
    }

    // --- DELETE CONFIRMATION DIALOG ---
    private void showDeleteConfirmationDialog(FinMate finMate) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);

        builder.setTitle("Delete FinMate");
        builder.setMessage("Type '" + finMate.getName() + "' to permanently delete this FinMate. This action cannot be undone.");

        android.graphics.drawable.GradientDrawable dialogBackground = new android.graphics.drawable.GradientDrawable();
        dialogBackground.setColor(android.graphics.Color.WHITE);
        dialogBackground.setCornerRadius(48f);
        builder.setBackground(dialogBackground);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, (int) (8 * getResources().getDisplayMetrics().density), padding, 0);

        com.google.android.material.textfield.TextInputLayout textInputLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        textInputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        textInputLayout.setBoxCornerRadii(16f, 16f, 16f, 16f);
        textInputLayout.setHint("Enter FinMate name");
        textInputLayout.setErrorEnabled(true);

        com.google.android.material.textfield.TextInputEditText input =
                new com.google.android.material.textfield.TextInputEditText(textInputLayout.getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setTextColor(android.graphics.Color.parseColor("#082561"));

        textInputLayout.addView(input);
        container.addView(textInputLayout);
        builder.setView(container);

        builder.setPositiveButton("Delete", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                androidx.core.view.WindowInsetsControllerCompat controller =
                        new androidx.core.view.WindowInsetsControllerCompat(dialog.getWindow(), input);
                controller.show(androidx.core.view.WindowInsetsCompat.Type.ime());
            }

            android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(android.graphics.Color.parseColor("#D32F2F"));

            android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(android.graphics.Color.parseColor("#667085"));

            positiveButton.setOnClickListener(v -> {
                String typedText = input.getText() != null ? input.getText().toString().trim() : "";
                if (typedText.equals(finMate.getName())) {
                    deleteFinMate(finMate);
                    dialog.dismiss();
                } else {
                    textInputLayout.setError("Name does not match.");
                }
            });

            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    textInputLayout.setError(null);
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        });

        dialog.show();
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
                    // Update the master list and run the filter to refresh the UI cleanly
                    masterFinMateList.remove(finMate);
                    filterFinMates(etSearchFinMate != null && etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
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

                            // Update Master List manually to avoid full re-fetch delay, then filter instantly
                            if (isEditing) {
                                int index = masterFinMateList.indexOf(finMateToEdit);
                                if (index != -1) {
                                    masterFinMateList.set(index, finMate);
                                }
                            } else {
                                masterFinMateList.add(0, finMate);
                            }
                            filterFinMates(etSearchFinMate != null && etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");

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