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

public class FinMateLedgerActivity extends AppCompatActivity {

    private TextView tvTotalPending, tvCurrentDue;
    private RecyclerView recyclerViewLedger;
    private LinearLayout layoutEmptyLedger;

    private TextInputEditText etSearchLedger;

    private String finMateId;
    private String finMateName;

    private LedgerAdapter adapter;
    private TextView tvAdvanceBadge;
    private final List<Transaction> masterLedgerList = new ArrayList<>();
    private final List<Transaction> displayList = new ArrayList<>();

    private final Map<String, String> userCardsMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_finmate_ledger);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.FinMateLedgerActivity), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        finMateId = getIntent().getStringExtra("FINMATE_ID");
        finMateName = getIntent().getStringExtra("FINMATE_NAME");

        if (finMateId == null) {
            Toast.makeText(this, "Error loading ledger", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        setupSearchAndFilter();

        fetchUserCards();
        fetchLedgerData();
    }

    private void initializeViews() {
        ImageView ivBack = findViewById(R.id.ivBack);
        TextView tvLedgerTitle = findViewById(R.id.tvLedgerTitle);
        tvTotalPending = findViewById(R.id.tvTotalPending);
        tvCurrentDue = findViewById(R.id.tvCurrentDue);
        tvAdvanceBadge = findViewById(R.id.tvAdvanceBadge);

        recyclerViewLedger = findViewById(R.id.recyclerViewLedger);
        layoutEmptyLedger = findViewById(R.id.layoutEmptyLedger);
        etSearchLedger = findViewById(R.id.etSearchLedger);

        tvLedgerTitle.setText(finMateName != null ? getString(R.string.finmate_ledger_title, finMateName) : getString(R.string.ledger_title));

        ivBack.setOnClickListener(v -> finish());

        recyclerViewLedger.setLayoutManager(new LinearLayoutManager(this));

        // Pass userCardsMap to the adapter here
        adapter = new LedgerAdapter(displayList, finMateId, userCardsMap);
        recyclerViewLedger.setAdapter(adapter);
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
                            userCardsMap.put(card.getCardId(), card.getBankName() + " (••" + card.getLast4Digits() + ")");
                        }
                    }
                    if (adapter != null) {
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void fetchLedgerData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("Transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Failed to load ledger", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    masterLedgerList.clear();
                    double currentDue = 0.0;

                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);

                            if (tx != null && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
                                masterLedgerList.add(tx);

                                Transaction.TransactionSplit split = tx.getSplits().get(finMateId);
                                if (split != null) {
                                    if ("SETTLEMENT".equals(tx.getTransactionType())) {
                                        currentDue -= split.getCombinedStealthAmount();
                                    } else {
                                        currentDue += split.getCombinedStealthAmount();
                                    }
                                }
                            }
                        }
                    }

                    double totalDue = 0.0;

                    updateTotalPendingUI(totalDue, currentDue);
                    filterTransactions(etSearchLedger.getText() != null ? etSearchLedger.getText().toString() : "");
                });
    }

    private void updateTotalPendingUI(double totalDue, double currentDue) {
        Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
        NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);

        tvTotalPending.setText(formatter.format(totalDue));

        if (currentDue < 0) {
            double advanceAmount = Math.abs(currentDue);
            tvCurrentDue.setText(formatter.format(advanceAmount));
            tvAdvanceBadge.setVisibility(View.VISIBLE);
        } else {
            tvCurrentDue.setText(formatter.format(currentDue));
            tvAdvanceBadge.setVisibility(View.GONE);
        }
    }

    private void setupSearchAndFilter() {
        etSearchLedger.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        MaterialCardView btnFilterLedger = findViewById(R.id.btnFilterLedger);
        btnFilterLedger.setOnClickListener(v -> showFilterBottomSheet());
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterTransactions(String query) {
        displayList.clear();
        if (query.trim().isEmpty()) {
            displayList.addAll(masterLedgerList);
        } else {
            String lowerCaseQuery = query.toLowerCase().trim();
            for (Transaction tx : masterLedgerList) {
                if (tx.getTitle() != null && tx.getTitle().toLowerCase().contains(lowerCaseQuery)) {
                    displayList.add(tx);
                }
            }
        }

        if(displayList.isEmpty()){
            layoutEmptyLedger.setVisibility(View.VISIBLE);
            recyclerViewLedger.setVisibility(View.GONE);
        } else {
            layoutEmptyLedger.setVisibility(View.GONE);
            recyclerViewLedger.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    @SuppressLint("InflateParams")
    private void showFilterBottomSheet() {
        BottomSheetDialog filterDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.bottom_sheet_ledger_filter, null);
        filterDialog.setContentView(view);

        ChipGroup cgTransactionType = view.findViewById(R.id.cgTransactionType);

        List<String> usedCardIds = new ArrayList<>();
        for (Transaction tx : masterLedgerList) {
            if ("CARD_SPEND".equals(tx.getTransactionType()) && tx.getCardId() != null) {
                if (!usedCardIds.contains(tx.getCardId())) {
                    usedCardIds.add(tx.getCardId());
                }
            }
        }

        int insertIndex = 1;
        for (String cardId : usedCardIds) {
            String cardName = userCardsMap.containsKey(cardId) ? userCardsMap.get(cardId) : "Card ending in " + cardId.substring(cardId.length() - 4);

            Chip chip = new Chip(this);
            chip.setText(cardName);
            chip.setCheckable(true);
            chip.setTag(cardId);

            cgTransactionType.addView(chip, insertIndex++);
        }

        ImageView ivCloseFilter = view.findViewById(R.id.ivCloseFilter);
        MaterialButton btnApplyFilter = view.findViewById(R.id.btnApplyFilter);
        MaterialButton btnClearFilter = view.findViewById(R.id.btnClearFilter);

        ivCloseFilter.setOnClickListener(v -> filterDialog.dismiss());

        btnClearFilter.setOnClickListener(v -> {
            Toast.makeText(this, "Filters Cleared", Toast.LENGTH_SHORT).show();
            filterDialog.dismiss();
        });

        btnApplyFilter.setOnClickListener(v -> {
            Toast.makeText(this, "Applying Filters...", Toast.LENGTH_SHORT).show();
            filterDialog.dismiss();
        });

        filterDialog.show();
    }

    public static class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.ViewHolder> {

        private final List<Transaction> transactions;
        private final String targetFinMateId;
        private final Map<String, String> userCardsMap;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        private final NumberFormat currencyFormatter;

        public LedgerAdapter(List<Transaction> transactions, String targetFinMateId, Map<String, String> userCardsMap) {
            this.transactions = transactions;
            this.targetFinMateId = targetFinMateId;
            this.userCardsMap = userCardsMap;
            Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
            currencyFormatter = NumberFormat.getCurrencyInstance(indianLocale);
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
            Transaction.TransactionSplit split = tx.getSplits().get(targetFinMateId);

            if (split == null) return;

            holder.tvTxTitle.setText(tx.getTitle());
            holder.tvTxDate.setText(dateFormat.format(new Date(tx.getTimestamp())));

            holder.tvTxAmount.setText(currencyFormatter.format(split.getCombinedStealthAmount()));

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                    ? tx.getTransactionId().substring(0, 6).toUpperCase()
                    : "UNKNOWN";
            holder.tvTxNumber.setText(holder.itemView.getContext().getString(R.string.txn_number_format, shortId));

            // Display Payment Source (Cash or Specific Card) safely using string resources
            if ("SETTLEMENT".equals(tx.getTransactionType())) {
                holder.tvTxSource.setVisibility(View.GONE);
            } else {
                holder.tvTxSource.setVisibility(View.VISIBLE);
                if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
                    holder.tvTxSource.setText(R.string.paid_via_cash);
                } else {
                    String cardName = userCardsMap.get(tx.getCardId());
                    if (cardName != null) {
                        holder.tvTxSource.setText(holder.itemView.getContext().getString(R.string.paid_via_card_name, cardName));
                    } else {
                        holder.tvTxSource.setText(R.string.paid_via_card);
                    }
                }
            }

            if (tx.getSplits() != null && tx.getSplits().size() > 1) {
                holder.tvTxTotalAmount.setVisibility(View.VISIBLE);
                holder.tvTxTotalAmount.setText(holder.itemView.getContext().getString(R.string.total_amount_format, currencyFormatter.format(tx.getTotalAmount())));
            } else {
                holder.tvTxTotalAmount.setVisibility(View.GONE);
            }

            if ("SETTLEMENT".equals(tx.getTransactionType())) {
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1abcab"));

                holder.tvTxAmount.setTextColor(Color.parseColor("#1abcab"));
                holder.tvTxAmount.setText(holder.itemView.getContext().getString(R.string.positive_amount_format, currencyFormatter.format(split.getCombinedStealthAmount())));

                holder.tvTxStatus.setText(R.string.status_settled);
                holder.tvTxStatus.setTextColor(Color.parseColor("#1abcab"));
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E0F2F1"));
            } else {
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#D32F2F"));

                holder.tvTxAmount.setTextColor(Color.parseColor("#082561"));

                double totalOwed = split.getCombinedStealthAmount();
                double amountPaid = split.getPaidAmount();

                if (amountPaid >= totalOwed && totalOwed > 0) {
                    holder.tvTxStatus.setText(R.string.status_paid);
                    holder.tvTxStatus.setTextColor(Color.parseColor("#388E3C"));
                    holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                } else if (amountPaid > 0) {
                    holder.tvTxStatus.setText(R.string.status_partially_paid);
                    holder.tvTxStatus.setTextColor(Color.parseColor("#F57C00"));
                    holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                } else {
                    holder.tvTxStatus.setText(R.string.status_unpaid);
                    holder.tvTxStatus.setTextColor(Color.parseColor("#D32F2F"));
                    holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                }
            }

            // ==========================================
            // NEW: Click Listener to open Bottom Sheet
            // ==========================================
            holder.itemView.setOnClickListener(v -> showTransactionDetailsSheet(v.getContext(), tx));
        }

        @SuppressLint({"SetTextI18n", "InflateParams"})
        private void showTransactionDetailsSheet(Context context, Transaction tx) {
            BottomSheetDialog sheetDialog = new BottomSheetDialog(context);

            // InflateParams warning is suppressed because passing null is standard for Dialogs
            View sheetView = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_details, null);
            sheetDialog.setContentView(sheetView);

            TextView tvSheetTxTitle = sheetView.findViewById(R.id.tvSheetTxTitle);
            TextView tvSheetTxDate = sheetView.findViewById(R.id.tvSheetTxDate);
            TextView tvSheetTxId = sheetView.findViewById(R.id.tvSheetTxId);
            TextView tvSheetSource = sheetView.findViewById(R.id.tvSheetSource);
            TextView tvSheetTotalAmount = sheetView.findViewById(R.id.tvSheetTotalAmount);
            LinearLayout layoutSplitsContainer = sheetView.findViewById(R.id.layoutSplitsContainer);
            ImageView ivCloseSheet = sheetView.findViewById(R.id.ivCloseSheet);

            ivCloseSheet.setOnClickListener(v -> sheetDialog.dismiss());

            tvSheetTxTitle.setText(tx.getTitle());
            tvSheetTxDate.setText(dateFormat.format(new Date(tx.getTimestamp())));

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                    ? tx.getTransactionId().substring(0, 6).toUpperCase()
                    : "UNKNOWN";
            tvSheetTxId.setText("#" + shortId);

            if ("SETTLEMENT".equals(tx.getTransactionType())) {
                tvSheetSource.setText("Settlement Payment");
            } else if (tx.getCardId() == null || "CASH".equals(tx.getCardId())) {
                tvSheetSource.setText("Cash");
            } else {
                String cName = userCardsMap.get(tx.getCardId());
                tvSheetSource.setText(cName != null ? cName : "Card");
            }

            double totalAmt = tx.getTotalAmount() > 0 ? tx.getTotalAmount() : splitAmountSum(tx);
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

        private double splitAmountSum(Transaction tx) {
            double sum = 0;
            if (tx.getSplits() != null) {
                for (Transaction.TransactionSplit s : tx.getSplits().values()) {
                    if (s != null) sum += s.getCombinedStealthAmount();
                }
            }
            return sum;
        }

        @Override
        public int getItemCount() {
            return transactions.size();
        }

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