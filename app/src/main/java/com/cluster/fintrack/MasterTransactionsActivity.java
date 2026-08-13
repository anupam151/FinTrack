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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
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

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        adapter = new MasterAdapter(filteredList, userCardsMap, this::showTransactionOptions);
        recyclerViewMaster.setAdapter(adapter);
    }

    private void setupListeners() {
        etSearchTransactions.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = s.toString();
                applyFiltersAndSort();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        btnSort.setOnClickListener(v -> showSortBottomSheet());
        btnFilter.setOnClickListener(v -> showFilterBottomSheet());
    }

    private void showTransactionOptions(Transaction tx, View anchor) {
        androidx.appcompat.view.ContextThemeWrapper wrapper =
                new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);

        PopupMenu popup = new PopupMenu(wrapper, anchor, android.view.Gravity.END);

        popup.getMenu().add(0, 0, 0, "Edit Transaction");
        popup.getMenu().add(0, 1, 0, "Delete Transaction");

        popup.setForceShowIcon(true);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                Toast.makeText(this, "Edit feature coming soon!", Toast.LENGTH_SHORT).show();
            } else if (item.getItemId() == 1) {
                confirmDeleteTransaction(tx);
            }
            return true;
        });

        popup.show();
    }

    private void confirmDeleteTransaction(Transaction tx) {
        androidx.appcompat.app.AlertDialog dialog = createDeleteDialogBuilder(tx).create();

        dialog.setOnShowListener(d -> {
            android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            if (positiveButton != null) {
                positiveButton.setTextColor(Color.parseColor("#D32F2F"));
            }

            android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            if (negativeButton != null) {
                negativeButton.setTextColor(Color.parseColor("#667085"));
            }
        });

        dialog.show();
    }

    @NonNull
    private MaterialAlertDialogBuilder createDeleteDialogBuilder(Transaction tx) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);

        builder.setTitle("Delete Transaction");
        builder.setMessage("Are you sure you want to permanently delete this transaction? This action cannot be undone.");

        android.graphics.drawable.GradientDrawable dialogBackground = new android.graphics.drawable.GradientDrawable();
        dialogBackground.setColor(Color.WHITE);
        dialogBackground.setCornerRadius(48f);
        builder.setBackground(dialogBackground);

        builder.setPositiveButton("Delete", (dialog, which) -> deleteTransactionFromFirestore(tx));
        builder.setNegativeButton("Cancel", null);

        return builder;
    }

    private void deleteTransactionFromFirestore(Transaction tx) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null || tx.getTransactionId() == null) return;

        FirebaseFirestore.getInstance()
                .collection("Users")
                .document(currentUser.getUid())
                .collection("Transactions")
                .document(tx.getTransactionId())
                .delete()
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Transaction deleted successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

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

    public interface OnTransactionLongClickListener {
        void onTransactionLongClick(Transaction tx, View anchor);
    }

    public static class MasterAdapter extends RecyclerView.Adapter<MasterAdapter.ViewHolder> {
        private final List<Transaction> transactions;
        private final Map<String, String> userCardsMap;
        private final OnTransactionLongClickListener longClickListener;

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        private final NumberFormat currencyFormatter;

        public MasterAdapter(List<Transaction> transactions, Map<String, String> userCardsMap, OnTransactionLongClickListener longClickListener) {
            this.transactions = transactions;
            this.userCardsMap = userCardsMap;
            this.longClickListener = longClickListener;
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

            // NEW: Properly distinguish incoming vs outgoing money visually
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

            holder.itemView.setOnClickListener(v -> showTransactionDetailsSheet(v.getContext(), tx));

            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onTransactionLongClick(tx, v);
                return true;
            });
        }

        @SuppressLint({"SetTextI18n", "InflateParams"})
        private void showTransactionDetailsSheet(Context context, Transaction tx) {
            BottomSheetDialog sheetDialog = new BottomSheetDialog(context);
            View sheetView = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_details, new android.widget.FrameLayout(context), false);
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
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Transaction ID", tx.getTransactionId());
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show();
                }
            });

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

            double totalAmt = tx.getTotalAmount() > 0 ? tx.getTotalAmount() : getSplitTotalForSettlement(tx);
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

                                View splitRow = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, layoutSplitsContainer, false);

                                splitRow.setMinimumHeight(0);
                                int verticalPadding = (int) (2 * context.getResources().getDisplayMetrics().density);
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
}