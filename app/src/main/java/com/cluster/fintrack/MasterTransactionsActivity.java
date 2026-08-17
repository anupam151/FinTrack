package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MasterTransactionsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewMaster;
    private LinearLayout layoutEmptyState;
    private TextInputEditText etSearchTransactions;
    private MaterialCardView btnSort, btnFilter;

    private MasterAdapter adapter;
    private final List<Transaction> allTransactionsList = new ArrayList<>();
    private final List<Transaction> filteredList = new ArrayList<>();
    private final Map<String, String> userCardsMap = new HashMap<>();

    private String currentSearchQuery = "";
    private String currentSortOption = "DATE_DESC";
    private String currentFilterSourceId = "ALL";

    // --- MULTI-SELECT VARIABLES ---
    private boolean isSelectionMode = false;
    private final Set<Transaction> selectedTransactions = new HashSet<>();
    private LinearLayout bottomSelectionBar;
    private TextView tvSelectionCount;
    private ImageView ivEditSelection;

    // Modern Back Button Handler
    private final OnBackPressedCallback selectionBackCallback = new OnBackPressedCallback(false) {
        @Override
        public void handleOnBackPressed() {
            clearSelection();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_master_transactions);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainMasterTransactions), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        // Register the back button callback to the Activity
        getOnBackPressedDispatcher().addCallback(this, selectionBackCallback);

        initializeViews();
        setupListeners();
        fetchUserCards();
    }

    private void initializeViews() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        etSearchTransactions = findViewById(R.id.etSearchTransactions);
        btnSort = findViewById(R.id.btnSort);
        btnFilter = findViewById(R.id.btnFilter);

        recyclerViewMaster = findViewById(R.id.recyclerViewMaster);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        recyclerViewMaster.setLayoutManager(new LinearLayoutManager(this));

        adapter = new MasterAdapter(filteredList, userCardsMap, selectedTransactions, new TransactionSelectionListener() {
            @Override
            public void onTransactionClick(Transaction tx, View anchor) {
                if (isSelectionMode) {
                    toggleSelection(tx);
                } else {
                    showTransactionDetailsSheet(tx);
                }
            }

            @Override
            public void onTransactionLongClick(Transaction tx, View anchor) {
                toggleSelection(tx);
            }
        });
        recyclerViewMaster.setAdapter(adapter);
    }

    private void setupListeners() {
        etSearchTransactions.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                applyFiltersAndSort();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSort.setOnClickListener(v -> showSortBottomSheet());
        btnFilter.setOnClickListener(v -> showFilterBottomSheet());
    }

    // =========================================================================
    // NATIVE IN-LAYOUT BOTTOM SELECTION BAR (Fixes Touch Blocking)
    // =========================================================================
    @SuppressLint("SetTextI18n")
    private void showBottomSelectionBar() {
        if (bottomSelectionBar == null) {
            // Build the view dynamically and inject it safely into the root frame
            bottomSelectionBar = new LinearLayout(this);
            bottomSelectionBar.setOrientation(LinearLayout.HORIZONTAL);

            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(Color.parseColor("#082561")); // App Theme Dark Blue
            gd.setCornerRadius(100f); // Make it a beautiful floating pill
            bottomSelectionBar.setBackground(gd);
            bottomSelectionBar.setPadding(60, 40, 60, 40);
            bottomSelectionBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
            bottomSelectionBar.setElevation(20f); // Material shadow

            int iconSize = (int) (24 * getResources().getDisplayMetrics().density);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);

            ImageView ivClose = new ImageView(this);
            ivClose.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
            ivClose.setColorFilter(Color.WHITE);
            ivClose.setLayoutParams(iconParams);
            ivClose.setOnClickListener(v -> clearSelection());

            tvSelectionCount = new TextView(this);
            tvSelectionCount.setTextColor(Color.WHITE);
            tvSelectionCount.setTextSize(16f);
            tvSelectionCount.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            tvParams.setMargins(40, 0, 0, 0);
            tvSelectionCount.setLayoutParams(tvParams);

            ivEditSelection = new ImageView(this);
            ivEditSelection.setImageResource(android.R.drawable.ic_menu_edit);
            ivEditSelection.setColorFilter(Color.WHITE);
            LinearLayout.LayoutParams editParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            editParams.setMargins(0, 0, 60, 0);
            ivEditSelection.setLayoutParams(editParams);
            ivEditSelection.setOnClickListener(v -> {
                Toast.makeText(this, "Edit feature coming soon!", Toast.LENGTH_SHORT).show();
                clearSelection();
            });

            ImageView ivDelete = new ImageView(this);
            ivDelete.setImageResource(android.R.drawable.ic_menu_delete);
            ivDelete.setColorFilter(Color.parseColor("#FF5252"));
            ivDelete.setLayoutParams(iconParams);
            ivDelete.setOnClickListener(v -> confirmBulkDelete());

            bottomSelectionBar.addView(ivClose);
            bottomSelectionBar.addView(tvSelectionCount);
            bottomSelectionBar.addView(ivEditSelection);
            bottomSelectionBar.addView(ivDelete);

            // Inject it into the Android root content frame
            FrameLayout contentFrame = findViewById(android.R.id.content);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.gravity = android.view.Gravity.BOTTOM;

            int horizontalMargin = (int) (16 * getResources().getDisplayMetrics().density);
            int bottomMargin = (int) (32 * getResources().getDisplayMetrics().density); // Lift it up slightly
            params.setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin);

            contentFrame.addView(bottomSelectionBar, params);
        }

        bottomSelectionBar.setVisibility(View.VISIBLE);
    }

    private void clearSelection() {
        isSelectionMode = false;
        selectionBackCallback.setEnabled(false); // Give back button to Android

        List<Transaction> previousSelection = new ArrayList<>(selectedTransactions);
        selectedTransactions.clear();

        if (bottomSelectionBar != null) {
            bottomSelectionBar.setVisibility(View.GONE);
        }

        for (Transaction tx : previousSelection) {
            int index = filteredList.indexOf(tx);
            if (index != -1) adapter.notifyItemChanged(index);
        }
    }

    @SuppressLint("SetTextI18n")
    public void toggleSelection(Transaction tx) {
        if (selectedTransactions.contains(tx)) {
            selectedTransactions.remove(tx);
        } else {
            selectedTransactions.add(tx);
        }

        if (selectedTransactions.isEmpty()) {
            clearSelection();
        } else {
            if (!isSelectionMode) {
                isSelectionMode = true;
                selectionBackCallback.setEnabled(true); // Capture back button
                showBottomSelectionBar();
            }
            if (tvSelectionCount != null) {
                tvSelectionCount.setText(selectedTransactions.size() + " Selected");
            }
            if (ivEditSelection != null) {
                ivEditSelection.setVisibility(selectedTransactions.size() == 1 ? View.VISIBLE : View.GONE);
            }
        }

        int index = filteredList.indexOf(tx);
        if (index != -1) {
            adapter.notifyItemChanged(index);
        }
    }

    // =========================================================================
    // SEQUENTIAL BULK DELETION ENGINE
    // =========================================================================
    private void confirmBulkDelete() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete " + selectedTransactions.size() + " Transactions")
                .setMessage("Are you sure you want to permanently delete the selected transactions? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> executeBulkDelete())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeBulkDelete() {
        List<Transaction> listToDelete = new ArrayList<>(selectedTransactions);
        clearSelection();

        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setPadding(0, 50, 0, 50);

        androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Deleting Transactions...")
                .setView(progressBar)
                .setCancelable(false)
                .create();

        progressDialog.show();

        deleteTransactionSequentially(listToDelete, 0, progressDialog);
    }

    private void deleteTransactionSequentially(List<Transaction> list, int index, androidx.appcompat.app.AlertDialog dialog) {
        if (index >= list.size()) {
            dialog.dismiss();
            Toast.makeText(this, "Successfully deleted " + list.size() + " transactions.", Toast.LENGTH_SHORT).show();
            return;
        }

        Transaction tx = list.get(index);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || tx.getTransactionId() == null) {
            dialog.dismiss();
            return;
        }

        String userId = currentUser.getUid();
        FirebaseFirestore.getInstance().collection("Users").document(userId)
                .collection("Transactions").document(tx.getTransactionId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (tx.getSplits() != null && !tx.getSplits().isEmpty()) {
                        List<String> finMates = new ArrayList<>(tx.getSplits().keySet());
                        processRipplesSequentially(userId, tx, finMates, 0, () -> deleteTransactionSequentially(list, index + 1, dialog));
                    } else {
                        deleteTransactionSequentially(list, index + 1, dialog);
                    }
                })
                .addOnFailureListener(e -> deleteTransactionSequentially(list, index + 1, dialog));
    }

    private void processRipplesSequentially(String userId, Transaction deletedTx, List<String> finMates, int index, Runnable onComplete) {
        if (index >= finMates.size()) {
            onComplete.run();
            return;
        }
        String finMateId = finMates.get(index);
        handleDeletionRippleEffect(userId, finMateId, deletedTx, () -> processRipplesSequentially(userId, deletedTx, finMates, index + 1, onComplete));
    }

    private void handleDeletionRippleEffect(String userId, String finMateId, Transaction deletedTx, Runnable onComplete) {
        Transaction.TransactionSplit deletedSplit = deletedTx.getSplits().get(finMateId);
        if (deletedSplit == null) {
            onComplete.run();
            return;
        }

        double amountToReverse = deletedSplit.getPaidAmount();

        if (amountToReverse <= 0.01) {
            if (!finMateId.equals("self")) {
                recalculateFinMateBalance(userId, finMateId, onComplete);
            } else {
                onComplete.run();
            }
            return;
        }

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(snap -> {
            WriteBatch batch = db.batch();
            String deletedType = deletedTx.getTransactionType();

            List<DocumentSnapshot> docs = new ArrayList<>(snap.getDocuments());
            docs.sort((d1, d2) -> {
                Long t1 = d1.getLong("timestamp");
                Long t2 = d2.getLong("timestamp");
                if (t1 == null || t2 == null) return 0;
                return Long.compare(t2, t1); // Newest first
            });

            double remainingToReverse = amountToReverse;

            for (DocumentSnapshot doc : docs) {
                if (remainingToReverse <= 0.01) break;
                if (doc.getId().equals(deletedTx.getTransactionId())) continue;

                Transaction tx = doc.toObject(Transaction.class);
                if (tx == null || tx.getSplits() == null) continue;

                Transaction.TransactionSplit sp = tx.getSplits().get(finMateId);
                if (sp == null || sp.getPaidAmount() <= 0.01) continue;

                if (shouldReverseFromTransaction(deletedType, tx.getTransactionType())) {
                    double deduction = Math.min(remainingToReverse, sp.getPaidAmount());
                    double newPaid = sp.getPaidAmount() - deduction;
                    batch.update(doc.getReference(), "splits." + finMateId + ".paidAmount", newPaid);
                    remainingToReverse -= deduction;
                }
            }

            batch.commit().addOnCompleteListener(task -> {
                if (!finMateId.equals("self")) {
                    recalculateFinMateBalance(userId, finMateId, onComplete);
                } else {
                    onComplete.run();
                }
            });
        }).addOnFailureListener(e -> onComplete.run());
    }

    private boolean shouldReverseFromTransaction(String deletedType, String targetType) {
        if ("SETTLEMENT".equals(deletedType)) {
            return "CASH_SPEND".equals(targetType) || "CARD_SPEND".equals(targetType);
        } else if ("PAY_CREDIT".equals(deletedType)) {
            return "TAKE_CREDIT".equals(targetType);
        } else if ("CASH_SPEND".equals(deletedType) || "CARD_SPEND".equals(deletedType)) {
            return "SETTLEMENT".equals(targetType) || "TAKE_CREDIT".equals(targetType);
        } else if ("TAKE_CREDIT".equals(deletedType)) {
            return "PAY_CREDIT".equals(targetType) || "CASH_SPEND".equals(targetType) || "CARD_SPEND".equals(targetType);
        }
        return false;
    }

    private void recalculateFinMateBalance(String userId, String finMateId, Runnable onComplete) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(snap -> {

            double cardSpend = 0.0;
            double cashSpend = 0.0;
            double cardPaid = 0.0;
            double cashPaid = 0.0;
            double inbound = 0.0;
            double outbound = 0.0;

            for (DocumentSnapshot doc : snap.getDocuments()) {
                Transaction tx = doc.toObject(Transaction.class);
                if (tx != null && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
                    Transaction.TransactionSplit split = tx.getSplits().get(finMateId);
                    if (split != null) {
                        double amt = split.getCombinedStealthAmount();
                        double paid = split.getPaidAmount();
                        String type = tx.getTransactionType();

                        if ("CARD_SPEND".equals(type)) { cardSpend += amt; cardPaid += paid; }
                        else if ("CASH_SPEND".equals(type)) { cashSpend += amt; cashPaid += paid; }
                        else if ("SETTLEMENT".equals(type) || "TAKE_CREDIT".equals(type)) { inbound += amt; }
                        else if ("PAY_CREDIT".equals(type)) { outbound += amt; }
                    }
                }
            }

            double netBalance = (cardSpend + cashSpend + outbound) - inbound;

            double finalReceivable = 0.0;
            double finalPayable = 0.0;
            double finalCard = 0.0;
            double finalCash = 0.0;

            if (netBalance > 0.01) {
                finalReceivable = netBalance;
                double calcCard = Math.max(0, cardSpend - cardPaid);
                double calcCash = Math.max(0, cashSpend - cashPaid);

                if (Math.abs((calcCard + calcCash) - netBalance) < 0.1) {
                    finalCard = calcCard;
                    finalCash = calcCash;
                } else {
                    double totalSpends = cardSpend + cashSpend;
                    if (totalSpends > 0) {
                        finalCard = netBalance * (cardSpend / totalSpends);
                        finalCash = netBalance * (cashSpend / totalSpends);
                    }
                }
            } else if (netBalance < -0.01) {
                finalPayable = Math.abs(netBalance);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("totalReceivable", finalReceivable);
            data.put("payableAmount", finalPayable);
            data.put("receivableCardAmount", finalCard);
            data.put("receivableCashAmount", finalCash);

            db.collection("Users").document(userId).collection("FinMates").document(finMateId)
                    .set(data, SetOptions.merge())
                    .addOnCompleteListener(t -> onComplete.run());
        }).addOnFailureListener(e -> onComplete.run());
    }

    // =========================================================================
    // STANDARD DATA FETCHING
    // =========================================================================
    @SuppressLint("NotifyDataSetChanged")
    private void fetchUserCards() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("Cards")
                .get()
                .addOnSuccessListener(snapshot -> {
                    userCardsMap.clear();
                    for (DocumentSnapshot doc : snapshot) {
                        Card card = doc.toObject(Card.class);
                        if (card != null) {
                            String shortBankName = getBankInitials(card.getBankName());
                            String displayName = card.getCardName() + " - " + shortBankName + " (" + card.getLast4Digits() + ")";
                            userCardsMap.put(card.getCardId(), displayName);
                        }
                    }
                    fetchMasterLedgerData();
                });
    }

    private void fetchMasterLedgerData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("Transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load master ledger", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    allTransactionsList.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null) {
                                allTransactionsList.add(tx);
                            }
                        }
                    }
                    applyFiltersAndSort();
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void applyFiltersAndSort() {
        filteredList.clear();
        String query = currentSearchQuery.toLowerCase().trim();

        for (Transaction tx : allTransactionsList) {
            boolean matchesSearch = matchesSearchFilter(tx, query);
            boolean matchesSource = matchesSourceFilter(tx);

            if (matchesSearch && matchesSource) {
                filteredList.add(tx);
            }
        }

        filteredList.sort((t1, t2) -> {
            switch (currentSortOption) {
                case "DATE_ASC":
                    return Long.compare(t1.getTimestamp(), t2.getTimestamp());
                case "AMT_DESC":
                    return Double.compare(t2.getTotalAmount(), t1.getTotalAmount());
                case "AMT_ASC":
                    return Double.compare(t1.getTotalAmount(), t2.getTotalAmount());
                case "DATE_DESC":
                default:
                    return Long.compare(t2.getTimestamp(), t1.getTimestamp());
            }
        });

        if (filteredList.isEmpty()) {
            layoutEmptyState.setVisibility(View.VISIBLE);
            recyclerViewMaster.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            recyclerViewMaster.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private double calculateTransactionTotal(Transaction tx) {
        if (tx.getTotalAmount() > 0) return tx.getTotalAmount();
        double total = 0;
        if (tx.getSplits() != null) {
            for (Transaction.TransactionSplit split : tx.getSplits().values()) {
                if (split != null) total += split.getCombinedStealthAmount();
            }
        }
        return total;
    }

    private boolean matchesSearchFilter(Transaction tx, String query) {
        if (query.isEmpty()) return true;

        String title = tx.getTitle() != null ? tx.getTitle().toLowerCase() : "";
        String txId = tx.getTransactionId() != null ? tx.getTransactionId().toLowerCase() : "";

        String cardName;
        if ("SETTLEMENT".equals(tx.getTransactionType())) {
            cardName = "settlement";
        } else if ("TAKE_CREDIT".equals(tx.getTransactionType())) {
            cardName = "credit";
        } else if ("PAY_CREDIT".equals(tx.getTransactionType())) {
            cardName = "credit paid back";
        } else if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
            cardName = "cash";
        } else {
            String fetchedName = userCardsMap.get(tx.getCardId());
            cardName = fetchedName != null ? fetchedName.toLowerCase() : "";
        }

        String amountStr = String.valueOf(calculateTransactionTotal(tx));

        return title.contains(query) || txId.contains(query) || cardName.contains(query) || amountStr.contains(query);
    }

    private boolean matchesSourceFilter(Transaction tx) {
        if ("ALL".equals(currentFilterSourceId)) {
            return true;
        } else if ("SETTLEMENT".equals(currentFilterSourceId)) {
            return "SETTLEMENT".equals(tx.getTransactionType()) || "TAKE_CREDIT".equals(tx.getTransactionType());
        } else if ("CASH".equals(currentFilterSourceId)) {
            return tx.getCardId() == null || "CASH".equals(tx.getCardId());
        } else {
            return currentFilterSourceId.equals(tx.getCardId());
        }
    }

    private void showSortBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ledger_filter, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        ChipGroup cgOptions = view.findViewById(R.id.cgTransactionType);
        cgOptions.removeAllViews();
        cgOptions.setSingleSelection(true);

        String[] sortKeys = {"DATE_DESC", "DATE_ASC", "AMT_DESC", "AMT_ASC"};
        String[] sortLabels = {
                getString(R.string.sort_newest),
                getString(R.string.sort_oldest),
                getString(R.string.sort_high_low),
                getString(R.string.sort_low_high)
        };

        for (int i = 0; i < sortKeys.length; i++) {
            Chip chip = new Chip(this);
            chip.setText(sortLabels[i]);
            chip.setCheckable(true);
            chip.setTag(sortKeys[i]);
            if (currentSortOption.equals(sortKeys[i])) {
                chip.setChecked(true);
            }
            cgOptions.addView(chip);
        }

        view.findViewById(R.id.ivCloseFilter).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnClearFilter).setVisibility(View.GONE);

        view.findViewById(R.id.btnApplyFilter).setOnClickListener(v -> {
            int checkedId = cgOptions.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip selectedChip = cgOptions.findViewById(checkedId);
                currentSortOption = (String) selectedChip.getTag();
                applyFiltersAndSort();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showFilterBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ledger_filter, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        ChipGroup cgOptions = view.findViewById(R.id.cgTransactionType);
        cgOptions.removeAllViews();
        cgOptions.setSingleSelection(true);

        addChipToGroup(cgOptions, getString(R.string.filter_all_sources), "ALL");
        addChipToGroup(cgOptions, getString(R.string.filter_cash), "CASH");
        addChipToGroup(cgOptions, "Received / Credits", "SETTLEMENT");

        for (Map.Entry<String, String> entry : userCardsMap.entrySet()) {
            addChipToGroup(cgOptions, entry.getValue(), entry.getKey());
        }

        view.findViewById(R.id.ivCloseFilter).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnClearFilter).setOnClickListener(v -> {
            currentFilterSourceId = "ALL";
            applyFiltersAndSort();
            dialog.dismiss();
        });

        view.findViewById(R.id.btnApplyFilter).setOnClickListener(v -> {
            int checkedId = cgOptions.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip selectedChip = cgOptions.findViewById(checkedId);
                currentFilterSourceId = (String) selectedChip.getTag();
                applyFiltersAndSort();
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addChipToGroup(ChipGroup group, String label, String tagId) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(tagId);
        if (currentFilterSourceId.equals(tagId)) {
            chip.setChecked(true);
        }
        group.addView(chip);
    }

    private String getBankInitials(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) return "BANK";

        Map<String, String> shortNameMap = new HashMap<>();
        shortNameMap.put("AU Small Finance Bank", "AUSFB");
        shortNameMap.put("American Express", "AMEX");
        shortNameMap.put("Axis Bank", "AXIS");
        shortNameMap.put("Bank of Baroda", "BOB");
        shortNameMap.put("HDFC Bank", "HDFC");
        shortNameMap.put("ICICI Bank Limited", "ICICI");
        shortNameMap.put("Kotak Mahindra Bank", "KOTAK");
        shortNameMap.put("State Bank of India", "SBI");

        if (shortNameMap.containsKey(bankName)) {
            return shortNameMap.get(bankName);
        }

        String[] words = bankName.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].length() > 4 ? words[0].substring(0, 4).toUpperCase() : words[0].toUpperCase();
        } else {
            StringBuilder initials = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) initials.append(word.charAt(0));
            }
            return initials.toString().toUpperCase();
        }
    }

    // =========================================================================
    // MULTI-SELECT ADAPTER
    // =========================================================================
    public interface TransactionSelectionListener {
        void onTransactionClick(Transaction tx, View anchor);
        void onTransactionLongClick(Transaction tx, View anchor);
    }

    public static class MasterAdapter extends RecyclerView.Adapter<MasterAdapter.ViewHolder> {
        private final List<Transaction> transactions;
        private final Map<String, String> userCardsMap;
        private final Set<Transaction> selectedTransactions;
        private final TransactionSelectionListener selectionListener;

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final NumberFormat currencyFormatter;

        public MasterAdapter(List<Transaction> transactions, Map<String, String> userCardsMap, Set<Transaction> selectedTransactions, TransactionSelectionListener selectionListener) {
            this.transactions = transactions;
            this.userCardsMap = userCardsMap;
            this.selectedTransactions = selectedTransactions;
            this.selectionListener = selectionListener;
            Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
            currencyFormatter = NumberFormat.getCurrencyInstance(indianLocale);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_master_transaction, parent, false);
            return new ViewHolder(view);
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction tx = transactions.get(position);

            holder.tvTxTitle.setText(tx.getTitle());
            holder.tvTxDate.setText(dateFormat.format(new Date(tx.getTimestamp())));

            double displayAmount = tx.getTotalAmount() > 0 ? tx.getTotalAmount() : getSplitTotalForSettlement(tx);
            holder.tvTxTotalAmount.setText(currencyFormatter.format(displayAmount));

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                    ? tx.getTransactionId().substring(0, 6).toUpperCase()
                    : "UNKNOWN";
            holder.tvTxNumber.setText(holder.itemView.getContext().getString(R.string.txn_number_format, shortId));

            if ("SETTLEMENT".equals(tx.getTransactionType()) || "TAKE_CREDIT".equals(tx.getTransactionType())) {
                if ("TAKE_CREDIT".equals(tx.getTransactionType())) {
                    holder.tvTxSource.setText("Credit Received");
                } else {
                    holder.tvTxSource.setText(holder.itemView.getContext().getString(R.string.filter_settlements));
                }

                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1abcab"));

                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
                holder.tvTxType.setText("Received");
                holder.tvTxType.setTextColor(Color.parseColor("#1abcab"));
            } else {
                if ("PAY_CREDIT".equals(tx.getTransactionType())) {
                    holder.tvTxSource.setText("Credit Paid Back");
                } else if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
                    holder.tvTxSource.setText(holder.itemView.getContext().getString(R.string.paid_via_cash));
                } else {
                    String cardName = userCardsMap.get(tx.getCardId());
                    holder.tvTxSource.setText(holder.itemView.getContext().getString(R.string.paid_via_card_name, cardName != null ? cardName : "Card"));
                }

                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1565C0"));

                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E3F2FD"));

                if ("PAY_CREDIT".equals(tx.getTransactionType())) {
                    holder.tvTxType.setText("Paid Credit");
                } else {
                    holder.tvTxType.setText("Spend");
                }
                holder.tvTxType.setTextColor(Color.parseColor("#1565C0"));
            }

            // VISUAL SELECTION UI
            if (selectedTransactions.contains(tx)) {
                holder.itemView.setBackgroundColor(Color.parseColor("#D0E3F5")); // Light Blue Tint
                holder.itemView.setAlpha(0.8f);
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                holder.itemView.setAlpha(1.0f);
            }

            // CLICK LISTENERS
            holder.itemView.setOnClickListener(v -> selectionListener.onTransactionClick(tx, v));
            holder.itemView.setOnLongClickListener(v -> {
                selectionListener.onTransactionLongClick(tx, v);
                return true;
            });
        }

        private double getSplitTotalForSettlement(Transaction tx) {
            double total = 0;
            if (tx.getSplits() != null) {
                for (Transaction.TransactionSplit split : tx.getSplits().values()) {
                    total += split.getCombinedStealthAmount();
                }
            }
            return total;
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTxTitle, tvTxDate, tvTxNumber, tvTxSource, tvTxTotalAmount, tvTxType;
            MaterialCardView badgeStatus;
            CardView cardIconContainer;
            ImageView ivTxIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTxTitle = itemView.findViewById(R.id.tvTxTitle);
                tvTxDate = itemView.findViewById(R.id.tvTxDate);
                tvTxNumber = itemView.findViewById(R.id.tvTxNumber);
                tvTxSource = itemView.findViewById(R.id.tvTxSource);
                tvTxTotalAmount = itemView.findViewById(R.id.tvTxTotalAmount);
                tvTxType = itemView.findViewById(R.id.tvTxType);
                badgeStatus = itemView.findViewById(R.id.badgeStatus);
                cardIconContainer = itemView.findViewById(R.id.cardIconContainer);
                ivTxIcon = itemView.findViewById(R.id.ivTxIcon);
            }
        }
    }

    @SuppressLint({"SetTextI18n", "InflateParams"})
    private void showTransactionDetailsSheet(Transaction tx) {
        BottomSheetDialog sheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_transaction_details, new android.widget.FrameLayout(this), false);
        sheetDialog.setContentView(sheetView);

        TextView tvSheetTxTitle = sheetView.findViewById(R.id.tvSheetTxTitle);
        TextView tvSheetTxDate = sheetView.findViewById(R.id.tvSheetTxDate);
        TextView tvSheetTxId = sheetView.findViewById(R.id.tvSheetTxId);
        TextView tvSheetSource = sheetView.findViewById(R.id.tvSheetSource);
        TextView tvSheetTotalAmount = sheetView.findViewById(R.id.tvSheetTotalAmount);
        LinearLayout layoutSplitsContainer = sheetView.findViewById(R.id.layoutSplitsContainer);
        ImageView ivCloseSheet = sheetView.findViewById(R.id.ivCloseSheet);
        ImageView ivCopyTxId = sheetView.findViewById(R.id.ivCopyTxId);

        ivCloseSheet.setOnClickListener(v -> sheetDialog.dismiss());

        ivCopyTxId.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Transaction ID", tx.getTransactionId());
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Transaction ID copied", Toast.LENGTH_SHORT).show();
            }
        });

        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        tvSheetTxTitle.setText(tx.getTitle());
        tvSheetTxDate.setText(dateTimeFormat.format(new Date(tx.getTimestamp())));

        String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                ? tx.getTransactionId().substring(0, 6).toUpperCase()
                : "UNKNOWN";
        tvSheetTxId.setText("#" + shortId);

        if ("SETTLEMENT".equals(tx.getTransactionType())) {
            tvSheetSource.setText("Settlement Payment");
        } else if ("TAKE_CREDIT".equals(tx.getTransactionType())) {
            tvSheetSource.setText("Credit Received");
        } else if ("PAY_CREDIT".equals(tx.getTransactionType())) {
            tvSheetSource.setText("Credit Paid Back");
        } else if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
            tvSheetSource.setText("Cash");
        } else {
            String cName = userCardsMap.get(tx.getCardId());
            tvSheetSource.setText(cName != null ? cName : "Card");
        }

        double totalAmt = tx.getTotalAmount() > 0 ? tx.getTotalAmount() : 0.0;
        Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(indianLocale);
        tvSheetTotalAmount.setText(currencyFormatter.format(totalAmt));

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && tx.getSplits() != null) {
            FirebaseFirestore.getInstance().collection("Users").document(user.getUid()).collection("FinMates")
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        Map<String, String> mateNames = new HashMap<>();
                        mateNames.put("self", "Self (You)");
                        for (DocumentSnapshot doc : snapshot) {
                            FinMate fm = doc.toObject(FinMate.class);
                            if (fm != null) {
                                mateNames.put(fm.getFinMateId(), fm.getName());
                            }
                        }

                        layoutSplitsContainer.removeAllViews();
                        for (Map.Entry<String, Transaction.TransactionSplit> entry : tx.getSplits().entrySet()) {
                            String mateId = entry.getKey();
                            Transaction.TransactionSplit sp = entry.getValue();
                            if (sp == null) continue;

                            String personName = mateNames.getOrDefault(mateId, "Unknown Person");

                            View splitRow = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, layoutSplitsContainer, false);
                            splitRow.setMinimumHeight(0);
                            int verticalPadding = (int) (2 * getResources().getDisplayMetrics().density);
                            splitRow.setPadding(0, verticalPadding, 0, verticalPadding);

                            TextView text1 = splitRow.findViewById(android.R.id.text1);
                            TextView text2 = splitRow.findViewById(android.R.id.text2);

                            text1.setText(personName);
                            text1.setTextColor(Color.parseColor("#082561"));
                            text1.setTextSize(14f);

                            text2.setText("Share: " + currencyFormatter.format(sp.getCombinedStealthAmount()) + " | Paid: " + currencyFormatter.format(sp.getPaidAmount()));
                            text2.setTextColor(Color.parseColor("#667085"));
                            text2.setTextSize(12f);

                            layoutSplitsContainer.addView(splitRow);
                        }
                    });
        }
        sheetDialog.show();
    }
}