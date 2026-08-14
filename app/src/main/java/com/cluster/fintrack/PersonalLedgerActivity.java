package com.cluster.fintrack;

import android.annotation.SuppressLint;
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

import com.google.android.material.card.MaterialCardView;
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

        recyclerViewPersonal.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PersonalAdapter(displayList, userCardsMap, finMatesMap);
        recyclerViewPersonal.setAdapter(adapter);

        etSearchPersonal.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterTransactions(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
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
                    userCardsMap.put(card.getCardId(), card.getCardName() + " - " + shortName);
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

                        // 1. Check if it's a loan taken (Liability)
                        if ("TAKE_CREDIT".equals(type) && tx.getSplits() != null) {
                            for (Transaction.TransactionSplit split : tx.getSplits().values()) {
                                double due = split.getCombinedStealthAmount() - split.getPaidAmount();
                                if (due > 0.01) {
                                    cumulativeLoans += due;
                                    belongsToSelf = true;
                                }
                            }
                        }

                        // 2. Check if paying back a loan (Transfers liability to Card/Cash)
                        if ("PAY_CREDIT".equals(type) && tx.getSplits() != null) {
                            belongsToSelf = true;
                            double amt = tx.getTotalAmount();
                            String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";

                            if ("CASH".equals(cId)) {
                                cumulativeCash += amt;
                            } else {
                                cardWiseDue.merge(cId, amt, Double::sum); // Safe computation
                            }
                        }

                        // 3. Check Normal Personal Spends
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
                                        cardWiseDue.merge(cId, amt, Double::sum); // Safe computation
                                    }
                                }
                            }
                        }

                        if (belongsToSelf) {
                            masterList.add(tx);
                        }
                    }

                    // Total Current Due = Total unpaid CC spends + Total active Loans taken
                    double totalCardSpends = 0.0;
                    for (Double val : cardWiseDue.values()) totalCardSpends += val;
                    double currentDue = totalCardSpends + cumulativeLoans;

                    tvCurrentDueSelf.setText(currencyFormatter.format(currentDue));

                    // Build the sleek horizontal breakdown chips
                    layoutBreakdownCards.removeAllViews();
                    if (cumulativeLoans > 0) addBreakdownCard("Active Loans", cumulativeLoans, "#E65100", "#FFF3E0");
                    if (cumulativeCash > 0) addBreakdownCard("Total Cash Spent", cumulativeCash, "#388E3C", "#E8F5E9");

                    for (Map.Entry<String, Double> entry : cardWiseDue.entrySet()) {
                        String cardName = userCardsMap.getOrDefault(entry.getKey(), "Credit Card");
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

    @SuppressLint("NotifyDataSetChanged")
    private void filterTransactions(String query) {
        displayList.clear();
        String lowerCaseQuery = query.toLowerCase().trim();
        for (Transaction tx : masterList) {
            String title = tx.getTitle() != null ? tx.getTitle().toLowerCase() : "";
            if (title.contains(lowerCaseQuery)) {
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
            holder.tvTxTotalAmount.setVisibility(View.GONE); // Hide total on personal view for cleaner UX

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