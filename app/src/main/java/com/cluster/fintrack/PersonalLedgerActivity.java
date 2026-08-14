package com.cluster.fintrack;

import android.annotation.SuppressLint;
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
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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

@SuppressLint("SetTextI18n")
public class PersonalLedgerActivity extends AppCompatActivity {

    private TextView tvCurrentDueSelf;
    private LinearLayout layoutBreakdownCards;
    private RecyclerView recyclerViewPersonal;
    private TextView layoutEmptyPersonal;
    private TextInputEditText etSearchPersonal;

    private PersonalAdapter adapter;
    private final List<Transaction> masterList = new ArrayList<>();
    private final List<Transaction> displayList = new ArrayList<>();

    private final Map<String, String> userCardsMap = new HashMap<>();
    private final Map<String, String> finMatesMap = new HashMap<>();

    private String currentFilterSourceId = "ALL";
    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_personal_ledger);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainPersonalLedger), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        initializeViews();
        fetchBackgroundData();
    }

    private void initializeViews() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        tvCurrentDueSelf = findViewById(R.id.tvCurrentDueSelf);
        layoutBreakdownCards = findViewById(R.id.layoutBreakdownCards);
        recyclerViewPersonal = findViewById(R.id.recyclerViewPersonal);
        layoutEmptyPersonal = findViewById(R.id.layoutEmptyPersonal);
        etSearchPersonal = findViewById(R.id.etSearchPersonal);

        MaterialCardView btnFilterPersonal = findViewById(R.id.btnFilterPersonal);

        recyclerViewPersonal.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PersonalAdapter(displayList, userCardsMap, finMatesMap);
        recyclerViewPersonal.setAdapter(adapter);

        etSearchPersonal.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterTransactions(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnFilterPersonal.setOnClickListener(v -> showFilterBottomSheet());
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchBackgroundData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("Cards").get().addOnSuccessListener(snapshot -> {
            userCardsMap.clear();
            for (DocumentSnapshot doc : snapshot) {
                Card card = doc.toObject(Card.class);
                if (card != null) {
                    String shortName = card.getBankName().length() > 4 ? card.getBankName().substring(0, 4).toUpperCase() : card.getBankName().toUpperCase();
                    userCardsMap.put(card.getCardId(), card.getCardName() + " - " + shortName + " (" + card.getLast4Digits() + ")");
                }
            }

            db.collection("Users").document(currentUser.getUid()).collection("FinMates").get().addOnSuccessListener(fmSnapshot -> {
                finMatesMap.clear();
                for (DocumentSnapshot doc : fmSnapshot) {
                    FinMate fm = doc.toObject(FinMate.class);
                    if (fm != null) finMatesMap.put(fm.getFinMateId(), fm.getName());
                }
                if (adapter != null) adapter.notifyDataSetChanged();
                fetchPersonalLedgerData();
            });
        });
    }

    private void fetchPersonalLedgerData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(currentUser.getUid()).collection("Transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    masterList.clear();
                    Map<String, Double> cardWiseDue = new HashMap<>();
                    double cumulativeLoans = 0.0;
                    double cumulativeCash = 0.0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx == null) continue;

                        boolean belongsToSelf = false;
                        String type = tx.getTransactionType();

                        if ("TAKE_CREDIT".equals(type) && tx.getSplits() != null) {
                            for (Transaction.TransactionSplit split : tx.getSplits().values()) {
                                double due = split.getCombinedStealthAmount() - split.getPaidAmount();
                                if (due > 0.01) {
                                    cumulativeLoans += due;
                                    belongsToSelf = true;
                                }
                            }
                        }

                        if ("PAY_CREDIT".equals(type) && tx.getSplits() != null) {
                            belongsToSelf = true;
                            double amt = tx.getTotalAmount();
                            String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";

                            if ("CASH".equals(cId)) {
                                cumulativeCash += amt;
                            } else {
                                cardWiseDue.merge(cId, amt, Double::sum);
                            }
                        }

                        if (tx.getSplits() != null && tx.getSplits().containsKey("self")) {
                            Transaction.TransactionSplit mySplit = tx.getSplits().get("self");
                            if (mySplit != null) {
                                double amt = mySplit.getCombinedStealthAmount();
                                if (amt > 0.01) {
                                    belongsToSelf = true;
                                    String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";

                                    if ("CASH_SPEND".equals(type) || "CASH".equals(cId)) {
                                        cumulativeCash += amt;
                                    } else if ("CARD_SPEND".equals(type)) {
                                        cardWiseDue.merge(cId, amt, Double::sum);
                                    }
                                }
                            }
                        }

                        if (belongsToSelf) {
                            masterList.add(tx);
                        }
                    }

                    double totalCardSpends = 0.0;
                    for (Double val : cardWiseDue.values()) totalCardSpends += val;
                    double currentDue = totalCardSpends + cumulativeLoans;

                    tvCurrentDueSelf.setText(currencyFormatter.format(currentDue));

                    layoutBreakdownCards.removeAllViews();
                    if (cumulativeLoans > 0) addBreakdownCard("Active Loans", cumulativeLoans, "#E65100", "#FFF3E0");
                    if (cumulativeCash > 0) addBreakdownCard("Total Cash Spent", cumulativeCash, "#388E3C", "#E8F5E9");

                    for (Map.Entry<String, Double> entry : cardWiseDue.entrySet()) {
                        String fullCardName = userCardsMap.get(entry.getKey());

                        // Safe Split Null Check
                        String cardName;
                        if (fullCardName != null && fullCardName.contains(" - ")) {
                            cardName = fullCardName.split(" - ")[0];
                        } else {
                            cardName = (fullCardName != null) ? fullCardName : "Credit Card";
                        }

                        addBreakdownCard(cardName, entry.getValue(), "#082561", "#E3F2FD");
                    }

                    filterTransactions(etSearchPersonal.getText() != null ? etSearchPersonal.getText().toString() : "");
                });
    }

    private void addBreakdownCard(String title, double amount, String textColor, String bgColor) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(8, 8, 8, 8);
        card.setLayoutParams(params);
        card.setCardBackgroundColor(Color.parseColor(bgColor));
        card.setCardElevation(1f);
        card.setRadius(24f);
        card.setStrokeWidth(0);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 24, 40, 24);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(Color.parseColor(textColor));
        tvTitle.setTextSize(12f);

        TextView tvAmount = new TextView(this);
        tvAmount.setText(currencyFormatter.format(amount));
        tvAmount.setTextColor(Color.parseColor(textColor));
        tvAmount.setTextSize(16f);
        tvAmount.setTypeface(null, android.graphics.Typeface.BOLD);
        tvAmount.setPadding(0, 4, 0, 0);

        layout.addView(tvTitle);
        layout.addView(tvAmount);
        card.addView(layout);

        layoutBreakdownCards.addView(card);
    }

    @SuppressLint("InflateParams")
    private void showFilterBottomSheet() {
        BottomSheetDialog filterDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ledger_filter, findViewById(android.R.id.content), false);
        filterDialog.setContentView(view);

        ChipGroup cgTransactionType = view.findViewById(R.id.cgTransactionType);

        List<String> usedCardIds = new ArrayList<>();
        for (Transaction tx : masterList) {
            if (tx.getCardId() != null && !usedCardIds.contains(tx.getCardId()) && !"CASH".equals(tx.getCardId())) {
                usedCardIds.add(tx.getCardId());
            }
        }

        addChipToGroup(cgTransactionType, "All Sources", "ALL");
        addChipToGroup(cgTransactionType, "Cash Spends", "CASH");

        for (String cardId : usedCardIds) {
            String cardName = userCardsMap.containsKey(cardId) ? userCardsMap.get(cardId) : "Card ending in " + cardId.substring(cardId.length() - 4);
            addChipToGroup(cgTransactionType, cardName, cardId);
        }

        ImageView ivCloseFilter = view.findViewById(R.id.ivCloseFilter);
        MaterialButton btnApplyFilter = view.findViewById(R.id.btnApplyFilter);
        MaterialButton btnClearFilter = view.findViewById(R.id.btnClearFilter);

        ivCloseFilter.setOnClickListener(v -> filterDialog.dismiss());

        btnClearFilter.setOnClickListener(v -> {
            Toast.makeText(this, "Filters Cleared", Toast.LENGTH_SHORT).show();
            currentFilterSourceId = "ALL";
            filterTransactions(etSearchPersonal.getText() != null ? etSearchPersonal.getText().toString() : "");
            filterDialog.dismiss();
        });

        btnApplyFilter.setOnClickListener(v -> {
            int checkedId = cgTransactionType.getCheckedChipId();
            if (checkedId != View.NO_ID) {
                Chip selectedChip = cgTransactionType.findViewById(checkedId);
                currentFilterSourceId = (String) selectedChip.getTag();
            } else {
                currentFilterSourceId = "ALL";
            }
            Toast.makeText(this, "Applying Filters...", Toast.LENGTH_SHORT).show();
            filterTransactions(etSearchPersonal.getText() != null ? etSearchPersonal.getText().toString() : "");
            filterDialog.dismiss();
        });

        filterDialog.show();
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

    @SuppressLint("NotifyDataSetChanged")
    private void filterTransactions(String query) {
        displayList.clear();
        String lowerCaseQuery = query.toLowerCase().trim();

        for (Transaction tx : masterList) {
            boolean matchesSearch = matchesSearchFilter(tx, lowerCaseQuery);
            boolean matchesSource = true;

            if (!"ALL".equals(currentFilterSourceId)) {
                if ("CASH".equals(currentFilterSourceId)) {
                    matchesSource = tx.getCardId() == null || "CASH".equals(tx.getCardId());
                } else {
                    matchesSource = currentFilterSourceId.equals(tx.getCardId());
                }
            }

            if (matchesSearch && matchesSource) {
                displayList.add(tx);
            }
        }

        if (displayList.isEmpty()) {
            layoutEmptyPersonal.setVisibility(View.VISIBLE);
            recyclerViewPersonal.setVisibility(View.GONE);
        } else {
            layoutEmptyPersonal.setVisibility(View.GONE);
            recyclerViewPersonal.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    private boolean matchesSearchFilter(Transaction tx, String query) {
        if (query.isEmpty()) return true;

        String title = tx.getTitle() != null ? tx.getTitle().toLowerCase() : "";
        String txId = tx.getTransactionId() != null ? tx.getTransactionId().toLowerCase() : "";

        String cardName;
        if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
            cardName = "cash";
        } else {
            String fetchedName = userCardsMap.get(tx.getCardId());
            cardName = fetchedName != null ? fetchedName.toLowerCase() : "";
        }

        double splitAmount = 0.0;
        if (tx.getSplits() != null) {
            Transaction.TransactionSplit split = tx.getSplits().get("self");
            if (split != null) {
                splitAmount = split.getCombinedStealthAmount();
            }
        }

        String amountStr = String.valueOf(splitAmount);
        String totalAmountStr = String.valueOf(tx.getTotalAmount());

        return title.contains(query) || txId.contains(query) || cardName.contains(query) || amountStr.contains(query) || totalAmountStr.contains(query);
    }

    public static class PersonalAdapter extends RecyclerView.Adapter<PersonalAdapter.ViewHolder> {
        private final List<Transaction> transactions;
        private final Map<String, String> userCardsMap;
        private final Map<String, String> finMatesMap;
        private final SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

        public PersonalAdapter(List<Transaction> transactions, Map<String, String> userCardsMap, Map<String, String> finMatesMap) {
            this.transactions = transactions;
            this.userCardsMap = userCardsMap;
            this.finMatesMap = finMatesMap;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_finmate_ledger, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction tx = transactions.get(position);
            String type = tx.getTransactionType();

            holder.tvTxDate.setText(dateOnlyFormat.format(new Date(tx.getTimestamp())));
            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6 ? tx.getTransactionId().substring(0, 6).toUpperCase() : "UNKNOWN";
            holder.tvTxNumber.setText("Txn: #" + shortId);
            holder.tvTxTotalAmount.setVisibility(View.GONE);

            if ("TAKE_CREDIT".equals(type)) {
                String lenderId = tx.getSplits().keySet().iterator().next();
                String lenderName = finMatesMap.getOrDefault(lenderId, "Someone");

                holder.tvTxTitle.setText("Loan taken from " + lenderName);
                holder.tvTxAmount.setText(currencyFormatter.format(tx.getTotalAmount()));
                holder.tvTxAmount.setTextColor(Color.parseColor("#E65100"));

                holder.tvTxSource.setText("Credit Received");
                holder.tvTxStatus.setText("Liability");
                holder.tvTxStatus.setTextColor(Color.parseColor("#E65100"));
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#E65100"));

            } else if ("PAY_CREDIT".equals(type)) {
                String lenderId = tx.getSplits().keySet().iterator().next();
                String lenderName = finMatesMap.getOrDefault(lenderId, "Someone");

                holder.tvTxTitle.setText("Paid back " + lenderName);
                holder.tvTxAmount.setText(currencyFormatter.format(tx.getTotalAmount()));
                holder.tvTxAmount.setTextColor(Color.parseColor("#082561"));

                String cardName = userCardsMap.get(tx.getCardId());
                holder.tvTxSource.setText("Paid via: " + (cardName != null ? cardName : "Cash"));

                holder.tvTxStatus.setText("Paid Debt");
                holder.tvTxStatus.setTextColor(Color.parseColor("#388E3C"));
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1565C0"));

            } else {
                Transaction.TransactionSplit mySplit = tx.getSplits().get("self");
                if (mySplit == null) return;

                holder.tvTxTitle.setText(tx.getTitle());
                holder.tvTxAmount.setText(currencyFormatter.format(mySplit.getCombinedStealthAmount()));
                holder.tvTxAmount.setTextColor(Color.parseColor("#082561"));

                if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
                    holder.tvTxSource.setText("Paid via: Cash");
                } else {
                    String cardName = userCardsMap.get(tx.getCardId());
                    holder.tvTxSource.setText("Paid via: " + (cardName != null ? cardName : "Card"));
                }

                holder.tvTxStatus.setText("Expense");
                holder.tvTxStatus.setTextColor(Color.parseColor("#1565C0"));
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1565C0"));
            }

            holder.itemView.setOnClickListener(v -> showTransactionDetailsSheet(v.getContext(), tx));
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

            sheetView.findViewById(R.id.ivCloseSheet).setOnClickListener(v -> sheetDialog.dismiss());

            tvSheetTxTitle.setText(tx.getTitle());
            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
            tvSheetTxDate.setText(dateTimeFormat.format(new Date(tx.getTimestamp())));

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6 ? tx.getTransactionId().substring(0, 6).toUpperCase() : "UNKNOWN";
            tvSheetTxId.setText("#" + shortId);

            if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
                tvSheetSource.setText("Cash");
            } else {
                String cName = userCardsMap.get(tx.getCardId());
                tvSheetSource.setText(cName != null ? cName : "Card");
            }

            tvSheetTotalAmount.setText(currencyFormatter.format(tx.getTotalAmount()));

            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null && tx.getSplits() != null) {
                FirebaseFirestore.getInstance().collection("Users").document(user.getUid()).collection("FinMates")
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            Map<String, String> mateNames = new HashMap<>();
                            mateNames.put("self", "Self (You)");
                            for (DocumentSnapshot doc : snapshot) {
                                FinMate fm = doc.toObject(FinMate.class);
                                if (fm != null) mateNames.put(fm.getFinMateId(), fm.getName());
                            }

                            layoutSplitsContainer.removeAllViews();
                            for (Map.Entry<String, Transaction.TransactionSplit> entry : tx.getSplits().entrySet()) {
                                String mateId = entry.getKey();
                                Transaction.TransactionSplit sp = entry.getValue();
                                if (sp == null) continue;

                                View splitRow = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, layoutSplitsContainer, false);
                                splitRow.setMinimumHeight(0);
                                int verticalPadding = (int) (2 * context.getResources().getDisplayMetrics().density);
                                splitRow.setPadding(0, verticalPadding, 0, verticalPadding);

                                TextView text1 = splitRow.findViewById(android.R.id.text1);
                                TextView text2 = splitRow.findViewById(android.R.id.text2);

                                text1.setText(mateNames.getOrDefault(mateId, "Unknown Person"));
                                text1.setTextColor(Color.parseColor("#082561"));
                                text1.setTextSize(14f);

                                text2.setText("Share: " + currencyFormatter.format(sp.getCombinedStealthAmount()));
                                text2.setTextColor(Color.parseColor("#667085"));
                                text2.setTextSize(12f);

                                layoutSplitsContainer.addView(splitRow);
                            }
                        });
            }
            sheetDialog.show();
        }

        @Override public int getItemCount() { return transactions.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTxTitle, tvTxDate, tvTxAmount, tvTxStatus, tvTxNumber, tvTxTotalAmount, tvTxSource;
            MaterialCardView badgeStatus;
            CardView cardIconContainer;
            ImageView ivTxIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTxTitle = itemView.findViewById(R.id.tvTxTitle);
                tvTxDate = itemView.findViewById(R.id.tvTxDate);
                tvTxAmount = itemView.findViewById(R.id.tvTxAmount);
                tvTxStatus = itemView.findViewById(R.id.tvTxStatus);
                tvTxNumber = itemView.findViewById(R.id.tvTxNumber);
                tvTxTotalAmount = itemView.findViewById(R.id.tvTxTotalAmount);
                tvTxSource = itemView.findViewById(R.id.tvTxSource);
                badgeStatus = itemView.findViewById(R.id.badgeStatus);
                cardIconContainer = itemView.findViewById(R.id.cardIconContainer);
                ivTxIcon = itemView.findViewById(R.id.ivTxIcon);
            }
        }
    }
}