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

        // THE FIX: Launch AddCardActivity instead of BottomSheet
        findViewById(R.id.fabAddCard).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddCardActivity.class);
            startActivity(intent);
        });

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
                // THE FIX: Launch AddCardActivity in EDIT MODE via Intent
                Intent intent = new Intent(this, AddCardActivity.class);
                intent.putExtra("CARD_ID", card.getCardId());
                intent.putExtra("CARD_NAME", card.getCardName());
                intent.putExtra("BANK_NAME", card.getBankName());
                intent.putExtra("CARD_TYPE", card.getCardType());
                intent.putExtra("LAST4", card.getLast4Digits());
                intent.putExtra("TOTAL_LIMIT", card.getTotalLimit());
                intent.putExtra("BILLING_DAY", card.getBillingDay());
                intent.putExtra("THEME_COLOR", card.getThemeColor());
                intent.putExtra("IS_CASHBACK", card.isCashbackCard());

                // --- NEW LOGIC: Extract the cashback rates and pass them as a double array ---
                List<Double> ratesList = card.getCashbackRates();
                if (ratesList != null && !ratesList.isEmpty()) {
                    double[] ratesArray = new double[ratesList.size()];
                    for (int i = 0; i < ratesList.size(); i++) {
                        ratesArray[i] = ratesList.get(i);
                    }
                    intent.putExtra("CASHBACK_RATES", ratesArray);
                }

                startActivity(intent);
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

    // --- THIS IS FOR THE MAIN ACTIVITY SEARCH BOX ONLY ---
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();

            if (v != null && v.getId() == R.id.etSearchCard) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);

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
}