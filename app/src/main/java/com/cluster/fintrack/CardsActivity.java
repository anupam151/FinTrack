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
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation") // Added to handle GoogleSignIn deprecation warnings safely
public class CardsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // UI Elements
    private SwipeRefreshLayout swipeRefreshCards;
    private RecyclerView recyclerViewCards;
    private TextView tvEmptyCardsList;
    private TextInputEditText etSearchCard;
    private ImageView ivSortCards;
    private ImageView ivSortOrder;

    // Data lists & Adapter
    private CardAdapter cardAdapter;
    private final List<Card> masterCardList = new ArrayList<>();
    private final List<Card> displayedCardList = new ArrayList<>();
    private ListenerRegistration firestoreListener;

    // Sorting state (0 = Recent Activity / Use, 1 = Recently Added, 2 = Name, 3 = Limit)
    private int currentSortMode = 0;
    private boolean isAscending = false;

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

        // --- THIS FIXES THE GREY ICONS PROGRAMMATICALLY ---
        navigationView.setItemIconTintList(null);

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
        ivSortOrder = findViewById(R.id.ivSortOrder);

        // Setup RecyclerView
        recyclerViewCards.setLayoutManager(new LinearLayoutManager(this));
        cardAdapter = new CardAdapter(this, displayedCardList, this::showCardOptionsPopup);
        recyclerViewCards.setAdapter(cardAdapter);

        // Setup Clicks & Actions
        setupNavigation(navigationView);
        setupSearchAndSort();
        loadCardsFromFirestore();

        swipeRefreshCards.setOnRefreshListener(() -> swipeRefreshCards.postDelayed(() -> swipeRefreshCards.setRefreshing(false), 800));

        findViewById(R.id.fabAddCard).setOnClickListener(v -> showAddEditCardBottomSheet(null));

        // Find and set up the Header Transaction Button
        ImageButton btnAddTransactionHeader = findViewById(R.id.btnAddTransactionHeader);
        if (btnAddTransactionHeader != null) {
            btnAddTransactionHeader.setOnClickListener(v -> {
                Intent intent = new Intent(CardsActivity.this, AddTransactionActivity.class);
                startActivity(intent);
            });
        }
    }

    // Helper method to handle Android 14's new transition API while supporting older devices
    private void disableWindowAnimations() {
        if (android.os.Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34) and above
            overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
        } else { // Android 13 and below
            overridePendingTransition(0, 0);
        }
    }

    private void setupNavigation(NavigationView navigationView) {
        ImageView ivMenuDrawerCards = findViewById(R.id.ivMenuDrawerCards);
        ivMenuDrawerCards.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            drawerLayout.postDelayed(() -> {
                if (id == R.id.nav_drawer_dashboard) {
                    Intent intent = new Intent(CardsActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    disableWindowAnimations();
                    finish();
                } else if (id == R.id.nav_drawer_cards) {
                    Toast.makeText(CardsActivity.this, "Already on Credit Cards", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_drawer_ledger) {
                    Intent intent = new Intent(CardsActivity.this, FinMatesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    disableWindowAnimations();
                    finish();
                } else if (id == R.id.nav_drawer_signout) {
                    FirebaseAuth.getInstance().signOut();
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build();

                    GoogleSignIn.getClient(CardsActivity.this, gso).signOut().addOnCompleteListener(task -> {
                        Toast.makeText(CardsActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(CardsActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                }
            }, 250);
            return false;
        });

        findViewById(R.id.navItemDashboard).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            disableWindowAnimations();
            finish();
        });

        findViewById(R.id.navItemLedger).setOnClickListener(v -> {
            Intent intent = new Intent(this, FinMatesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            disableWindowAnimations();
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
            androidx.appcompat.view.ContextThemeWrapper wrapper =
                    new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);
            PopupMenu popup = new PopupMenu(wrapper, v);

            popup.getMenu().add(0, 0, 0, "Recent Activity / Use");
            popup.getMenu().add(0, 1, 0, "Recently Added");
            popup.getMenu().add(0, 2, 0, "Card Name (A-Z)");
            popup.getMenu().add(0, 3, 0, "Total Limit (High to Low)");

            popup.setOnMenuItemClickListener(item -> {
                currentSortMode = item.getItemId();
                filterCards(etSearchCard.getText() != null ? etSearchCard.getText().toString() : "");
                return true;
            });
            popup.show();
        });

        ivSortOrder.setOnClickListener(v -> {
            isAscending = !isAscending;
            ivSortOrder.animate().rotation(isAscending ? 180f : 0f).setDuration(200).start();
            filterCards(etSearchCard.getText() != null ? etSearchCard.getText().toString() : "");
        });
    }

    private void showCardOptionsPopup(Card card, View anchor) {
        androidx.appcompat.view.ContextThemeWrapper wrapper =
                new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);

        androidx.appcompat.widget.PopupMenu popup =
                new androidx.appcompat.widget.PopupMenu(wrapper, anchor, android.view.Gravity.END);

        popup.getMenu().add(0, 0, 0, "Edit Card");
        popup.getMenu().add(0, 1, 0, "Delete Card");

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

    private void showDeleteConfirmationDialog(Card card) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);

        builder.setTitle("Delete Card");
        builder.setMessage("Type '" + card.getCardName() + "' to permanently delete this card. This action cannot be undone.");

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
        textInputLayout.setHint("Enter card name");
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
                if (typedText.equals(card.getCardName())) {
                    deleteCardFromFirestore(card);
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
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
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

        if (currentSortMode == 0) {
            tempFilteredList.sort((c1, c2) -> isAscending ?
                    Long.compare(c1.getTimestamp(), c2.getTimestamp()) :
                    Long.compare(c2.getTimestamp(), c1.getTimestamp()));
        } else if (currentSortMode == 1) {
            tempFilteredList.sort((c1, c2) -> isAscending ?
                    Long.compare(c1.getTimestamp(), c2.getTimestamp()) :
                    Long.compare(c2.getTimestamp(), c1.getTimestamp()));
        } else if (currentSortMode == 2) {
            tempFilteredList.sort((c1, c2) -> isAscending ?
                    c1.getCardName().compareToIgnoreCase(c2.getCardName()) :
                    c2.getCardName().compareToIgnoreCase(c1.getCardName()));
        } else if (currentSortMode == 3) {
            tempFilteredList.sort((c1, c2) -> isAscending ?
                    Double.compare(c1.getTotalLimit(), c2.getTotalLimit()) :
                    Double.compare(c2.getTotalLimit(), c1.getTotalLimit()));
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

    @SuppressLint("SetTextI18n")
    private void showAddEditCardBottomSheet(Card cardToEdit) {
        com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this) {
            @Override
            public boolean dispatchTouchEvent(@androidx.annotation.NonNull android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    android.view.View v = getCurrentFocus();
                    if (v instanceof android.widget.EditText) {
                        android.graphics.Rect outRect = new android.graphics.Rect();
                        v.getGlobalVisibleRect(outRect);

                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            boolean clickedAnotherEditText = false;

                            if (getWindow() != null) {
                                int[] editIds = {
                                        R.id.etSheetCardName, R.id.spinSheetBankName,
                                        R.id.spinSheetCardType, R.id.etSheetLast4Digits,
                                        R.id.etSheetTotalLimit, R.id.etSheetBillingDay
                                };
                                android.graphics.Rect rect = new android.graphics.Rect();
                                for (int id : editIds) {
                                    android.view.View editView = getWindow().findViewById(id);
                                    if (editView != null && editView.isShown()) {
                                        editView.getGlobalVisibleRect(rect);
                                        if (rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                                            clickedAnotherEditText = true;
                                            break;
                                        }
                                    }
                                }
                            }

                            if (!clickedAnotherEditText) {
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

        android.view.View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_card, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        android.widget.TextView title = view.findViewById(R.id.tvCardSheetTitle);
        com.google.android.material.textfield.MaterialAutoCompleteTextView spinBankName = view.findViewById(R.id.spinSheetBankName);
        com.google.android.material.textfield.TextInputLayout tilBankName = view.findViewById(R.id.tilSheetBankName);

        com.google.android.material.textfield.MaterialAutoCompleteTextView spinCardType = view.findViewById(R.id.spinSheetCardType);
        com.google.android.material.textfield.TextInputLayout tilCardType = view.findViewById(R.id.tilSheetCardType);

        com.google.android.material.textfield.TextInputEditText etCardName = view.findViewById(R.id.etSheetCardName);
        com.google.android.material.textfield.TextInputEditText etLast4Digits = view.findViewById(R.id.etSheetLast4Digits);
        com.google.android.material.textfield.TextInputEditText etTotalLimit = view.findViewById(R.id.etSheetTotalLimit);
        com.google.android.material.textfield.TextInputEditText etBillingDay = view.findViewById(R.id.etSheetBillingDay);

        com.google.android.material.card.MaterialCardView cardColorPreview = view.findViewById(R.id.cardColorPreview);
        android.widget.ImageView btnResetColor = view.findViewById(R.id.btnResetColor);
        android.widget.ImageView btnClearAllFields = view.findViewById(R.id.btnClearAllFields);
        com.google.android.material.button.MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveCard);

        dialog.setOnShowListener(d -> {
            etCardName.requestFocus();
            if (dialog.getWindow() != null) {
                androidx.core.view.WindowInsetsControllerCompat controller =
                        new androidx.core.view.WindowInsetsControllerCompat(dialog.getWindow(), etCardName);
                controller.show(androidx.core.view.WindowInsetsCompat.Type.ime());
            }
        });

        spinBankName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        spinBankName.setAdapter(getBankArrayAdapter());
        spinBankName.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (spinBankName.hasFocus() && s.length() > 0) {
                    spinBankName.showDropDown();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        spinCardType.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        String[] cardTypes = new String[]{"RuPay", "Visa", "MasterCard", "Amex", "Discover", "Other"};
        android.widget.ArrayAdapter<String> cardTypeAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, cardTypes);
        spinCardType.setAdapter(cardTypeAdapter);
        spinCardType.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (spinCardType.hasFocus() && s.length() > 0) {
                    spinCardType.showDropDown();
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        final int[] currentColor = {android.graphics.Color.parseColor("#082561")};
        final int defaultColor = android.graphics.Color.parseColor("#082561");

        if (cardToEdit != null) {
            title.setText("Edit Credit Card");
            btnSave.setText("Update Card");

            tilBankName.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT);
            tilBankName.setEndIconTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#667085")));

            tilCardType.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_CLEAR_TEXT);
            tilCardType.setEndIconTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#667085")));

            etCardName.setText(cardToEdit.getCardName());
            spinBankName.setText(cardToEdit.getBankName(), false);
            spinCardType.setText(cardToEdit.getCardType(), false);
            etLast4Digits.setText(cardToEdit.getLast4Digits());
            etTotalLimit.setText(String.valueOf((int) cardToEdit.getTotalLimit()));
            etBillingDay.setText(String.valueOf(cardToEdit.getBillingDay()));

            try {
                currentColor[0] = android.graphics.Color.parseColor(cardToEdit.getThemeColor());
                cardColorPreview.setCardBackgroundColor(currentColor[0]);
            } catch (Exception e) {
                android.util.Log.e("CardBottomSheet", "Failed to parse theme color: " + e.getMessage());
            }
        } else {
            title.setText("Add Credit Card");
            btnSave.setText("Save Card");

            tilBankName.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_NONE);
            tilCardType.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_NONE);
        }

        btnClearAllFields.setOnClickListener(v -> {
            etCardName.setText("");
            spinBankName.setText("", false);
            spinCardType.setText("", false);
            etLast4Digits.setText("");
            etTotalLimit.setText("");
            etBillingDay.setText("");

            currentColor[0] = defaultColor;
            cardColorPreview.setCardBackgroundColor(defaultColor);

            etCardName.setError(null);
            spinBankName.setError(null);
            spinCardType.setError(null);
            etLast4Digits.setError(null);
            etTotalLimit.setError(null);
            etBillingDay.setError(null);

            etCardName.requestFocus();
        });

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
            String cardType = String.valueOf(spinCardType.getText()).trim();
            String last4 = String.valueOf(etLast4Digits.getText()).trim();
            String limitStr = String.valueOf(etTotalLimit.getText()).trim();
            String billingDayStr = String.valueOf(etBillingDay.getText()).trim();

            if (android.text.TextUtils.isEmpty(cardName)) { etCardName.setError("Required"); return; }
            if (android.text.TextUtils.isEmpty(bankName)) { spinBankName.setError("Required"); return; }

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

            if (android.text.TextUtils.isEmpty(cardType)) { spinCardType.setError("Required"); return; }

            String[] validCardTypes = new String[]{"RuPay", "Visa", "MasterCard", "Amex", "Discover", "Other"};
            boolean isValidCardType = false;
            for (String type : validCardTypes) {
                if (type.equalsIgnoreCase(cardType)) {
                    isValidCardType = true;
                    break;
                }
            }

            if (!isValidCardType) {
                spinCardType.setError("Please select a valid card type from the list");
                spinCardType.requestFocus();
                return;
            }

            if (android.text.TextUtils.isEmpty(last4) || last4.length() != 4) { etLast4Digits.setError("Enter 4 digits"); return; }
            if (android.text.TextUtils.isEmpty(limitStr)) { etTotalLimit.setError("Required"); return; }
            if (android.text.TextUtils.isEmpty(billingDayStr)) { etBillingDay.setError("Required"); return; }

            double totalLimit = Double.parseDouble(limitStr);
            int billingDay = Integer.parseInt(billingDayStr);
            if (billingDay < 1 || billingDay > 31) {
                etBillingDay.setError("Enter 1-31");
                return;
            }

            String themeColorHex = String.format("#%06X", (0xFFFFFF & currentColor[0]));

            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();

            String cardIdToSave = cardToEdit != null ? cardToEdit.getCardId() : db.collection("Users").document(userId).collection("Cards").document().getId();
            long timestamp = System.currentTimeMillis();

            Card updatedCard = new Card(cardIdToSave, bankName, cardName, cardType, last4, totalLimit, billingDay, themeColorHex, timestamp);

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

        return new android.widget.ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new java.util.ArrayList<>(bankList)) {
            @androidx.annotation.NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        if (constraint == null || constraint.length() == 0) {
                            results.values = bankList;
                            results.count = bankList.size();
                        } else {
                            java.util.List<String> filteredList = new java.util.ArrayList<>();
                            String filterPattern = constraint.toString().toLowerCase().trim();

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
                        notifyDataSetChanged();
                    }
                };
            }
        };
    }
    // --- THIS IS FOR THE MAIN ACTIVITY SEARCH BOX ONLY ---
    // Place this at the bottom of CardsActivity.java (outside any other method)
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();

            // Check if the currently focused view is the SEARCH BOX
            if (v != null && v.getId() == R.id.etSearchCard) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);

                // If you clicked OUTSIDE the search box
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus(); // Remove the cursor from the search box

                    if (getWindow() != null) {
                        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), v);
                        controller.hide(WindowInsetsCompat.Type.ime()); // Hide the keyboard
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}