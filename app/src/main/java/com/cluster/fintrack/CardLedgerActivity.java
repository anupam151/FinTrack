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
import androidx.cardview.widget.CardView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
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
public class CardLedgerActivity extends AppCompatActivity {

    private TextView tvAvailableLimit, tvTotalUsed;
    private RecyclerView recyclerViewCardTx;
    private TextView layoutEmptyState;
    private TextInputEditText etSearchCardTx;

    private CardTransactionAdapter adapter;
    private final List<Transaction> masterList = new ArrayList<>();
    private final List<Transaction> displayList = new ArrayList<>();

    private String cardId;
    private String cardName;
    private double totalLimit = 0.0;
    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_card_ledger);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainCardLedger), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        // Get Intent Data
        cardId = getIntent().getStringExtra("CARD_ID");
        String bankName = getIntent().getStringExtra("BANK_NAME");
        cardName = getIntent().getStringExtra("CARD_NAME");
        String cardType = getIntent().getStringExtra("CARD_TYPE");
        String last4 = getIntent().getStringExtra("LAST4");
        totalLimit = getIntent().getDoubleExtra("TOTAL_LIMIT", 0.0);
        String themeColor = getIntent().getStringExtra("THEME_COLOR");

        if (cardId == null) {
            finish();
            return;
        }

        initializeViewsAndMockup(bankName, cardName, cardType, last4, themeColor);
        fetchCardTransactions();
    }

    private void initializeViewsAndMockup(String bankName, String cardName, String cardType, String last4, String themeColor) {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        // Local variables for static UI elements
        View viewDynamicHeader = findViewById(R.id.viewDynamicHeader);
        TextView tvBankName = findViewById(R.id.tvBankName);
        TextView tvCardName = findViewById(R.id.tvCardName);
        TextView tvCardType = findViewById(R.id.tvCardType);
        TextView tvCardNumber = findViewById(R.id.tvCardNumber);
        TextView tvTotalLimit = findViewById(R.id.tvTotalLimit);
        TextView tvBilledDue = findViewById(R.id.tvBilledDue);
        TextView tvUnbilled = findViewById(R.id.tvUnbilled);

        // Global variables for dynamic UI elements
        tvAvailableLimit = findViewById(R.id.tvAvailableLimit);
        tvTotalUsed = findViewById(R.id.tvTotalUsed);
        recyclerViewCardTx = findViewById(R.id.recyclerViewCardTx);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        etSearchCardTx = findViewById(R.id.etSearchCardTx);

        // 1. Setup Card Mockup
        tvBankName.setText(bankName != null ? bankName : "Bank");
        tvCardName.setText(cardName != null ? cardName : "Credit Card");
        tvCardType.setText(cardType != null ? cardType : "Visa");
        tvCardNumber.setText("****  ****  ****  " + (last4 != null ? last4 : "0000"));

        if (themeColor != null && !themeColor.isEmpty()) {
            try {
                viewDynamicHeader.setBackgroundColor(Color.parseColor(themeColor));
            } catch (Exception ignored) {}
        }

        // 2. Setup Static Summaries
        tvTotalLimit.setText(currencyFormatter.format(totalLimit));
        tvBilledDue.setText("---"); // Placeholder for advanced EMI logic later
        tvUnbilled.setText("---");

        // 3. Setup RecyclerView
        recyclerViewCardTx.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CardTransactionAdapter(displayList, this.cardName);
        recyclerViewCardTx.setAdapter(adapter);

        etSearchCardTx.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterTransactions(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void fetchCardTransactions() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(currentUser.getUid()).collection("Transactions")
                .whereEqualTo("cardId", cardId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    masterList.clear();
                    double totalUsed = 0.0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx != null) {
                            masterList.add(tx);

                            // MATHEMATICS FIX: Accurately add spends and subtract bill payments
                            if ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                                totalUsed += tx.getTotalAmount();
                            } else if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                                totalUsed -= tx.getTotalAmount();
                            }
                        }
                    }

                    tvTotalUsed.setText(currencyFormatter.format(totalUsed));
                    tvAvailableLimit.setText(currencyFormatter.format(Math.max(0, totalLimit - totalUsed)));

                    filterTransactions(etSearchCardTx.getText() != null ? etSearchCardTx.getText().toString() : "");
                });
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
            layoutEmptyState.setVisibility(View.VISIBLE);
            recyclerViewCardTx.setVisibility(View.GONE);
        } else {
            layoutEmptyState.setVisibility(View.GONE);
            recyclerViewCardTx.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    public static class CardTransactionAdapter extends RecyclerView.Adapter<CardTransactionAdapter.ViewHolder> {
        private final List<Transaction> transactions;
        private final String currentCardName;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault());
        private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

        public CardTransactionAdapter(List<Transaction> transactions, String currentCardName) {
            this.transactions = transactions;
            this.currentCardName = currentCardName;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_card_transaction, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction tx = transactions.get(position);

            holder.tvTxTitle.setText(tx.getTitle());
            holder.tvTxDate.setText(dateFormat.format(new Date(tx.getTimestamp())));
            holder.tvTxAmount.setText(currencyFormatter.format(tx.getTotalAmount()));

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                    ? tx.getTransactionId().substring(0, 6).toUpperCase()
                    : "UNKNOWN";
            holder.tvTxNumber.setText("Txn: #" + shortId);

            // Hide unused elements for cleaner UI
            holder.tvTxTotalAmount.setVisibility(View.GONE);
            holder.tvTxSource.setVisibility(View.GONE);

            // UI STYLING FIX: Recognize CARD_PAYMENT and style it green
            if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                holder.tvTxStatus.setText("Bill Paid");
                holder.tvTxStatus.setTextColor(Color.parseColor("#388E3C")); // Green Text
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9")); // Light Green Box

                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#388E3C"));

            } else if ("PAY_CREDIT".equals(tx.getTransactionType())) {
                holder.tvTxStatus.setText("Paid Credit");
                holder.tvTxStatus.setTextColor(Color.parseColor("#388E3C"));
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));

                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#388E3C"));

            } else {
                // Normal Spend
                holder.tvTxStatus.setText("Spend");
                holder.tvTxStatus.setTextColor(Color.parseColor("#1565C0")); // Blue Text
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // Light Blue Box

                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1565C0"));
            }

            // Show Bottom Sheet on Click
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
            ImageView ivCloseSheet = sheetView.findViewById(R.id.ivCloseSheet);
            ImageView ivCopyTxId = sheetView.findViewById(R.id.ivCopyTxId);

            if (ivCloseSheet != null) {
                ivCloseSheet.setOnClickListener(v -> sheetDialog.dismiss());
            }

            if (ivCopyTxId != null) {
                ivCopyTxId.setOnClickListener(v -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Transaction ID", tx.getTransactionId());
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(clip);
                        Toast.makeText(context, "Transaction ID copied", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            tvSheetTxTitle.setText(tx.getTitle());
            tvSheetTxDate.setText(dateTimeFormat.format(new Date(tx.getTimestamp())));

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                    ? tx.getTransactionId().substring(0, 6).toUpperCase()
                    : "UNKNOWN";
            tvSheetTxId.setText("#" + shortId);

            if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                tvSheetSource.setText("Paid from Cash/Bank");
            } else {
                tvSheetSource.setText(currentCardName != null ? currentCardName : "Credit Card");
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

        @Override public int getItemCount() { return transactions.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvTxTitle, tvTxDate, tvTxNumber, tvTxSource, tvTxAmount, tvTxStatus, tvTxTotalAmount;
            MaterialCardView badgeStatus;
            CardView cardIconContainer;
            ImageView ivTxIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvTxTitle = itemView.findViewById(R.id.tvTxTitle);
                tvTxDate = itemView.findViewById(R.id.tvTxDate);
                tvTxNumber = itemView.findViewById(R.id.tvTxNumber);
                tvTxSource = itemView.findViewById(R.id.tvTxSource);
                tvTxAmount = itemView.findViewById(R.id.tvTxAmount);
                tvTxStatus = itemView.findViewById(R.id.tvTxStatus);
                tvTxTotalAmount = itemView.findViewById(R.id.tvTxTotalAmount);
                badgeStatus = itemView.findViewById(R.id.badgeStatus);
                cardIconContainer = itemView.findViewById(R.id.cardIconContainer);
                ivTxIcon = itemView.findViewById(R.id.ivTxIcon);
            }
        }
    }
}