package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
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
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class CardsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // UI Elements
    private SwipeRefreshLayout swipeRefreshCards;
    private RecyclerView recyclerViewCards;
    private TextView tvEmptyCardsList;
    private TextInputEditText etSearchCard;
    private ImageView ivSortCards;

    // Data lists & Adapter
    private CardAdapter cardAdapter;
    private final List<Card> masterCardList = new ArrayList<>();
    private final List<Card> displayedCardList = new ArrayList<>();
    private ListenerRegistration firestoreListener;

    // Sorting state (0 = Recent, 1 = Name, 2 = Limit)
    private int currentSortMode = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cards);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        drawerLayout = findViewById(R.id.drawerLayoutCards);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            findViewById(R.id.mainCards).setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationViewCards);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        // Initialize UI Elements
        swipeRefreshCards = findViewById(R.id.swipeRefreshCards);
        recyclerViewCards = findViewById(R.id.recyclerViewCards);
        tvEmptyCardsList = findViewById(R.id.tvEmptyCardsList);
        etSearchCard = findViewById(R.id.etSearchCard);
        ivSortCards = findViewById(R.id.ivSortCards);

        // Setup RecyclerView with the Long-Click Listener passed in!
        recyclerViewCards.setLayoutManager(new LinearLayoutManager(this));
        cardAdapter = new CardAdapter(this, displayedCardList, this::showCardOptionsPopup);
        recyclerViewCards.setAdapter(cardAdapter);

        // Setup Clicks & Actions
        setupNavigation();
        setupSearchAndSort();
        loadCardsFromFirestore();

        swipeRefreshCards.setOnRefreshListener(() ->swipeRefreshCards.postDelayed(() -> swipeRefreshCards.setRefreshing(false), 800));

        // If 'null' is passed, it acts as a brand-new card.
        findViewById(R.id.fabAddCard).setOnClickListener(v -> showAddEditCardBottomSheet(null));
    }

    private void setupNavigation() {
        ImageView ivMenuDrawerCards = findViewById(R.id.ivMenuDrawerCards);
        ivMenuDrawerCards.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        findViewById(R.id.navItemDashboard).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navItemLedger).setOnClickListener(v -> {
            Intent intent = new Intent(this, FinMatesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        findViewById(R.id.navSetings).setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void setupSearchAndSort() {
        etSearchCard.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCards(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        ivSortCards.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(CardsActivity.this, v);
            popup.getMenu().add(0, 0, 0, "Recently Added");
            popup.getMenu().add(0, 1, 0, "Card Name (A-Z)");
            popup.getMenu().add(0, 2, 0, "Total Limit (High to Low)");

            popup.setOnMenuItemClickListener(item -> {
                currentSortMode = item.getItemId();
                filterCards(etSearchCard.getText() != null ? etSearchCard.getText().toString() : "");
                return true;
            });
            popup.show();
        });
    }

    // --- NEW: Options Popup Menu ---
    private void showCardOptionsPopup(Card card, View anchor) {
        // 1. Use the new Material 3 Theme Wrapper
        androidx.appcompat.view.ContextThemeWrapper wrapper =
                new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);

        // 2. Use the AndroidX PopupMenu
        androidx.appcompat.widget.PopupMenu popup =
                new androidx.appcompat.widget.PopupMenu(wrapper, anchor, android.view.Gravity.END);

        // 3. Add text and native icons
        popup.getMenu().add(0, 0, 0, "Edit Card");
        popup.getMenu().add(0, 1, 0, "Delete Card");

        // 4. Force icons to show
        popup.setForceShowIcon(true);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                showAddEditCardBottomSheet(card);
            } else if (item.getItemId() == 1) {
                showDeleteConfirmationDialog(card);
            }
            return true;
        });

        popup.show();
    }

    // --- NEW: Delete Confirmation Dialog ---
    private void showDeleteConfirmationDialog(Card card) {
        // 1. Use MaterialAlertDialogBuilder for a modern UI
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);

        builder.setTitle("Delete Card");
        builder.setMessage("Type '" + card.getCardName() + "' to permanently delete this card. This action cannot be undone.");

        // 2. Force perfectly rounded corners with a custom background
        android.graphics.drawable.GradientDrawable dialogBackground = new android.graphics.drawable.GradientDrawable();
        dialogBackground.setColor(android.graphics.Color.WHITE);
        dialogBackground.setCornerRadius(48f); // Beautiful smooth rounded corners
        builder.setBackground(dialogBackground);

        // 3. Create a layout container with proper Material spacing
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, (int) (8 * getResources().getDisplayMetrics().density), padding, 0);

        // 4. Use TextInputLayout for a professional outlined text box
        com.google.android.material.textfield.TextInputLayout textInputLayout =
                new com.google.android.material.textfield.TextInputLayout(this);
        textInputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        textInputLayout.setBoxCornerRadii(16f, 16f, 16f, 16f); // Rounded text box
        textInputLayout.setHint("Enter card name");
        textInputLayout.setErrorEnabled(true); // Allows us to show inline errors

        com.google.android.material.textfield.TextInputEditText input =
                new com.google.android.material.textfield.TextInputEditText(textInputLayout.getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS); // Capitalize first letter
        input.setTextColor(android.graphics.Color.parseColor("#082561")); // App Theme Navy

        textInputLayout.addView(input);
        container.addView(textInputLayout);
        builder.setView(container);

        // 5. Set buttons (We set Positive to null here so it doesn't auto-close if they type the wrong name)
        builder.setPositiveButton("Delete", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // 6. Customizing Button Colors, Auto-Focus, Keyboard, and Click Logic
        dialog.setOnShowListener(d -> {
            // --- AUTO-FOCUS & OPEN KEYBOARD IMMEDIATELY ---
            input.requestFocus();
            if (dialog.getWindow() != null) {
                androidx.core.view.WindowInsetsControllerCompat controller =
                        new androidx.core.view.WindowInsetsControllerCompat(dialog.getWindow(), input);
                controller.show(androidx.core.view.WindowInsetsCompat.Type.ime());
            }
            // ---------------------------------------------

            android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(android.graphics.Color.parseColor("#D32F2F")); // Professional Alert Red

            android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(android.graphics.Color.parseColor("#667085")); // Subtle Grey

            // Validate text on click
            positiveButton.setOnClickListener(v -> {
                String typedText = input.getText() != null ? input.getText().toString().trim() : "";
                if (typedText.equals(card.getCardName())) {
                    deleteCardFromFirestore(card);
                    dialog.dismiss();
                } else {
                    // Show error directly on the text box instead of a Toast
                    textInputLayout.setError("Name does not match.");
                }
            });

            // Automatically clear the error red highlight as soon as they start typing again
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

    private void deleteCardFromFirestore(Card card) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        FirebaseFirestore.getInstance().collection("Users").document(userId).collection("Cards")
                .document(card.getCardId())
                .delete()
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Card deleted successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting card", Toast.LENGTH_SHORT).show());
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadCardsFromFirestore() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        swipeRefreshCards.setRefreshing(true);

        firestoreListener = db.collection("Users").document(userId).collection("Cards")
                .addSnapshotListener((snapshot, error) -> {
                    swipeRefreshCards.setRefreshing(false);

                    if (error != null) {
                        Toast.makeText(CardsActivity.this, "Failed to load cards: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    masterCardList.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Card card = doc.toObject(Card.class);
                            if (card != null) {
                                masterCardList.add(card);
                            }
                        }
                    }
                    filterCards(etSearchCard.getText() != null ? etSearchCard.getText().toString() : "");
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterCards(String query) {
        List<Card> tempFilteredList = new ArrayList<>();

        if (TextUtils.isEmpty(query)) {
            tempFilteredList.addAll(masterCardList);
        } else {
            String lowerCaseQuery = query.toLowerCase();
            for (Card card : masterCardList) {
                if (card.getCardName().toLowerCase().contains(lowerCaseQuery) ||
                        card.getBankName().toLowerCase().contains(lowerCaseQuery)) {
                    tempFilteredList.add(card);
                }
            }
        }

        if (currentSortMode == 1) { // Name (A-Z)
            tempFilteredList.sort((c1, c2) -> c1.getCardName().compareToIgnoreCase(c2.getCardName()));
        } else if (currentSortMode == 2) { // Limit (High-Low)
            tempFilteredList.sort((c1, c2) -> Double.compare(c2.getTotalLimit(), c1.getTotalLimit()));
        } else { // Recently Added (Default)
            tempFilteredList.sort((c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));
        }

        displayedCardList.clear();
        displayedCardList.addAll(tempFilteredList);
        cardAdapter.notifyDataSetChanged();

        if (displayedCardList.isEmpty()) {
            tvEmptyCardsList.setVisibility(View.VISIBLE);
            recyclerViewCards.setVisibility(View.GONE);
        } else {
            tvEmptyCardsList.setVisibility(View.GONE);
            recyclerViewCards.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (firestoreListener != null) {
            firestoreListener.remove();
        }
    }

    // --- REFACTORED: Now accepts a Card object to pre-fill data for editing! ---
    @SuppressLint("SetTextI18n")
    private void showAddEditCardBottomSheet(Card cardToEdit) {
        // 1. Create dialog and override touch events to keep keyboard open when switching between text boxes
        BottomSheetDialog dialog = new BottomSheetDialog(this) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    View v = getCurrentFocus();
                    if (v instanceof android.widget.EditText) {
                        android.graphics.Rect outRect = new android.graphics.Rect();
                        v.getGlobalVisibleRect(outRect);
                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            // Only close keyboard if the newly touched focus is NOT another EditText
                            View newFocus = getWindow() != null ? getWindow().getCurrentFocus() : null;
                            if (!(newFocus instanceof android.widget.EditText)) {
                                v.clearFocus();
                                // Modern, non-deprecated way to hide the keyboard
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

        // 2. DEFINE THE VIEWS
        TextView title = view.findViewById(R.id.tvCardSheetTitle);
        com.google.android.material.textfield.MaterialAutoCompleteTextView spinBankName = view.findViewById(R.id.spinSheetBankName);
        TextInputEditText etCardName = view.findViewById(R.id.etSheetCardName);
        TextInputEditText etTotalLimit = view.findViewById(R.id.etSheetTotalLimit);
        TextInputEditText etBillingDay = view.findViewById(R.id.etSheetBillingDay);

        com.google.android.material.card.MaterialCardView cardColorPreview = view.findViewById(R.id.cardColorPreview);
        ImageView btnResetColor = view.findViewById(R.id.btnResetColor);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveCard);

        // 3. SET AUTO-FOCUS & OPEN KEYBOARD
        dialog.setOnShowListener(d -> {
            etCardName.requestFocus();
            // Modern, non-deprecated way to show the keyboard without needing SHOW_IMPLICIT
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

        // PRE-FILL DATA IF EDITING
        if (cardToEdit != null) {
            title.setText("Edit Credit Card");
            btnSave.setText("Update Card");

            etCardName.setText(cardToEdit.getCardName());
            spinBankName.setText(cardToEdit.getBankName(), false);
            etTotalLimit.setText(String.valueOf((int) cardToEdit.getTotalLimit())); // Cast to int to remove .0
            etBillingDay.setText(String.valueOf(cardToEdit.getBillingDay()));

            try {
                currentColor[0] = android.graphics.Color.parseColor(cardToEdit.getThemeColor());
                cardColorPreview.setCardBackgroundColor(currentColor[0]);
            } catch (Exception e) {
                // Keep default if parse fails
            }
        } else {
            title.setText("Add Credit Card");
            btnSave.setText("Save Card");
        }

        // COLOR PICKER LOGIC
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

        // SAVE BUTTON LOGIC
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

            String cardIdToSave = cardToEdit != null ? cardToEdit.getCardId() : db.collection("Users").document(userId).collection("Cards").document().getId();
            long timestamp = cardToEdit != null ? cardToEdit.getTimestamp() : System.currentTimeMillis();

            Card updatedCard = new Card(cardIdToSave, bankName, cardName, totalLimit, billingDay, themeColorHex, timestamp);

            btnSave.setEnabled(false);
            btnSave.setText(cardToEdit != null ? "Updating..." : "Saving...");

            db.collection("Users").document(userId).collection("Cards").document(cardIdToSave)
                    .set(updatedCard)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, cardToEdit != null ? "Card Updated!" : "Card Saved!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                            btnSave.setText(cardToEdit != null ? "Update Card" : "Save Card");
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
                            (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}