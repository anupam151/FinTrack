package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressLint("SetTextI18n")
public class CardAllTransactionsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewAllTx;
    private TextView tvEmptyState;
    private String cardId;

    private final List<Transaction> allTxList = new ArrayList<>();
    private GroupedTransactionAdapter adapter;

    // Track which statement headers are currently expanded
    private final Set<String> expandedGroups = new HashSet<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_card_all_transactions);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.recyclerViewAllTx).getRootView(), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        cardId = getIntent().getStringExtra("CARD_ID");
        String cardName = getIntent().getStringExtra("CARD_NAME");

        if (cardId == null) {
            finish();
            return;
        }

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        recyclerViewAllTx = findViewById(R.id.recyclerViewAllTx);
        tvEmptyState = findViewById(R.id.tvEmptyState);

        recyclerViewAllTx.setLayoutManager(new LinearLayoutManager(this));
        adapter = new GroupedTransactionAdapter(cardName, expandedGroups);
        recyclerViewAllTx.setAdapter(adapter);

        fetchAllTransactions();
    }

    private void fetchAllTransactions() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(user.getUid()).collection("Transactions")
                .whereEqualTo("cardId", cardId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshot != null) {
                        allTxList.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null) allTxList.add(tx);
                        }

                        allTxList.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));
                        buildGroupedList();
                    }
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void buildGroupedList() {
        Map<String, StatementMonth> monthMap = new LinkedHashMap<>();
        SimpleDateFormat backupFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());

        StatementMonth unbilledMonth = new StatementMonth("Current Unbilled Cycle");

        for (Transaction tx : allTxList) {
            if (tx.getBilledMonth() != null && !tx.getBilledMonth().trim().isEmpty()) {
                String groupName = tx.getBilledMonth();
                StatementMonth sm = monthMap.get(groupName);
                if (sm == null) {
                    sm = new StatementMonth(groupName);
                    monthMap.put(groupName, sm);
                }
                sm.addTransaction(tx);
            }
            else if (tx.isBilled() && !"CARD_PAYMENT".equals(tx.getTransactionType())) {
                String groupName = backupFormat.format(new Date(tx.getTimestamp()));
                StatementMonth sm = monthMap.get(groupName);
                if (sm == null) {
                    sm = new StatementMonth(groupName);
                    monthMap.put(groupName, sm);
                }
                sm.addTransaction(tx);
            }
            else {
                unbilledMonth.addTransaction(tx);
            }
        }

        // --- THE 72-HOUR STRICT VALIDATION ENGINE ---
        long highestTimestamp = 0;
        StatementMonth latestStatement = null;

        for (StatementMonth sm : monthMap.values()) {
            if (sm.generatedAt > highestTimestamp) {
                highestTimestamp = sm.generatedAt;
                latestStatement = sm;
            }
        }

        long seventyTwoHoursInMillis = 259200000L;
        if (latestStatement != null) {
            long timeSinceGeneration = System.currentTimeMillis() - highestTimestamp;
            if (timeSinceGeneration <= seventyTwoHoursInMillis) {
                latestStatement.isUndoable = true;
            }
        }

        List<StatementMonth> finalGroups = new ArrayList<>();
        if (!unbilledMonth.transactions.isEmpty()) {
            finalGroups.add(unbilledMonth);
        }
        finalGroups.addAll(monthMap.values());

        if (finalGroups.isEmpty()) {
            tvEmptyState.setVisibility(View.VISIBLE);
            recyclerViewAllTx.setVisibility(View.GONE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
            recyclerViewAllTx.setVisibility(View.VISIBLE);
        }

        adapter.updateData(finalGroups);
    }

    public static class StatementMonth {
        public String monthYear;
        public List<Transaction> transactions = new ArrayList<>();
        public double totalBilledAmount = 0.0;

        public long generatedAt = 0;
        public boolean isUndoable = false;

        public StatementMonth(String monthYear) {
            this.monthYear = monthYear;
        }

        public void addTransaction(Transaction tx) {
            transactions.add(tx);
            if ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                totalBilledAmount += tx.getTotalAmount();
            }
            if (tx.getStatementGeneratedAt() > generatedAt) {
                generatedAt = tx.getStatementGeneratedAt();
            }
        }
    }

    // =========================================================================
    // DYNAMIC ACCORDION ADAPTER
    // =========================================================================
    public static class GroupedTransactionAdapter extends RecyclerView.Adapter<GroupedTransactionAdapter.AccordionViewHolder> {

        private final String currentCardName;
        private final Set<String> expandedGroups;
        private final List<StatementMonth> groups = new ArrayList<>();

        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

        public GroupedTransactionAdapter(String currentCardName, Set<String> expandedGroups) {
            this.currentCardName = currentCardName;
            this.expandedGroups = expandedGroups;
        }

        @SuppressLint("NotifyDataSetChanged")
        public void updateData(List<StatementMonth> newGroups) {
            this.groups.clear();
            this.groups.addAll(newGroups);
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return groups.size();
        }

        @NonNull
        @Override
        public AccordionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_month_header, parent, false);
            return new AccordionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AccordionViewHolder holder, int position) {
            StatementMonth sm = groups.get(position);
            Context context = holder.itemView.getContext();

            holder.tvMonthHeader.setText(sm.monthYear);
            holder.tvTotalBillAmount.setText(currencyFormatter.format(sm.totalBilledAmount));

            if (sm.monthYear.equals("Current Unbilled Cycle")) {
                holder.tvMonthHeader.setTextColor(Color.parseColor("#F57C00"));
                ((MaterialCardView) holder.itemView).setCardBackgroundColor(Color.parseColor("#FFF8E1"));
            } else {
                holder.tvMonthHeader.setTextColor(Color.parseColor("#082561"));
                ((MaterialCardView) holder.itemView).setCardBackgroundColor(Color.parseColor("#FFFFFF"));
            }

            boolean isExpanded = expandedGroups.contains(sm.monthYear);
            holder.ivExpandToggle.setRotation(isExpanded ? 180f : 0f);

            // Clean up any recycled timers
            holder.cancelTimer();

            // --- UNDO BUTTON & TIMER LOGIC ---
            if (sm.isUndoable) {
                holder.ivUndoStatement.setVisibility(View.VISIBLE);
                holder.tvUndoTimer.setVisibility(View.VISIBLE);

                long seventyTwoHoursInMillis = 259200000L;
                long timeSinceGeneration = System.currentTimeMillis() - sm.generatedAt;
                long timeLeft = seventyTwoHoursInMillis - timeSinceGeneration;

                if (timeLeft > 0) {
                    holder.countDownTimer = new CountDownTimer(timeLeft, 1000) {
                        @Override
                        public void onTick(long millisUntilFinished) {
                            long hours = millisUntilFinished / 3600000;
                            long minutes = (millisUntilFinished % 3600000) / 60000;
                            long seconds = (millisUntilFinished % 60000) / 1000;
                            holder.tvUndoTimer.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds));
                        }

                        @Override
                        public void onFinish() {
                            holder.ivUndoStatement.setVisibility(View.GONE);
                            holder.tvUndoTimer.setVisibility(View.GONE);
                            sm.isUndoable = false;
                        }
                    }.start();
                } else {
                    holder.ivUndoStatement.setVisibility(View.GONE);
                    holder.tvUndoTimer.setVisibility(View.GONE);
                    sm.isUndoable = false;
                }

                holder.ivUndoStatement.setOnClickListener(v ->
                        new MaterialAlertDialogBuilder(context)
                                .setTitle("Undo Statement")
                                .setMessage("Are you sure you want to undo the " + sm.monthYear + " statement? These transactions will be moved back to the Unbilled cycle.")
                                .setPositiveButton("Undo", (dialog, which) -> {
                                    FirebaseFirestore db = FirebaseFirestore.getInstance();
                                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                                    if (user != null) {
                                        WriteBatch batch = db.batch();
                                        for (Transaction tx : sm.transactions) {
                                            batch.update(
                                                    db.collection("Users").document(user.getUid()).collection("Transactions").document(tx.getTransactionId()),
                                                    "billed", false,
                                                    "billedMonth", "",
                                                    "statementGeneratedAt", 0L
                                            );
                                        }
                                        batch.commit().addOnSuccessListener(task ->
                                                Toast.makeText(context, "Statement Undone!", Toast.LENGTH_SHORT).show()
                                        );
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show()
                );
            } else {
                holder.ivUndoStatement.setVisibility(View.GONE);
                holder.tvUndoTimer.setVisibility(View.GONE);
            }

            holder.viewDivider.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
            holder.scrollTransactionsContainer.setVisibility(isExpanded ? View.VISIBLE : View.GONE);

            // THE FIX: Changed getAdapterPosition() to getBindingAdapterPosition()
            holder.layoutHeaderClickable.setOnClickListener(v -> {
                int currentPosition = holder.getBindingAdapterPosition();
                if (currentPosition != RecyclerView.NO_POSITION) {
                    if (isExpanded) {
                        expandedGroups.remove(sm.monthYear);
                    } else {
                        expandedGroups.add(sm.monthYear);
                    }
                    notifyItemChanged(currentPosition);
                }
            });

            holder.layoutTransactionsContainer.removeAllViews();
            if (isExpanded) {
                int horizontalMargin = (int) (10 * context.getResources().getDisplayMetrics().density);
                int verticalMargin = (int) (6 * context.getResources().getDisplayMetrics().density);

                for (Transaction tx : sm.transactions) {
                    View itemHolder = LayoutInflater.from(context).inflate(R.layout.item_card_transaction, holder.layoutTransactionsContainer, false);

                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    params.setMargins(horizontalMargin, verticalMargin, horizontalMargin, verticalMargin);
                    itemHolder.setLayoutParams(params);

                    TextView tvTxTitle = itemHolder.findViewById(R.id.tvTxTitle);
                    TextView tvTxDate = itemHolder.findViewById(R.id.tvTxDate);
                    TextView tvTxNumber = itemHolder.findViewById(R.id.tvTxNumber);
                    TextView tvTxSource = itemHolder.findViewById(R.id.tvTxSource);
                    TextView tvTxAmount = itemHolder.findViewById(R.id.tvTxAmount);
                    TextView tvTxStatus = itemHolder.findViewById(R.id.tvTxStatus);
                    TextView tvTxTotalAmount = itemHolder.findViewById(R.id.tvTxTotalAmount);
                    MaterialCardView badgeStatus = itemHolder.findViewById(R.id.badgeStatus);
                    CardView cardIconContainer = itemHolder.findViewById(R.id.cardIconContainer);
                    ImageView ivTxIcon = itemHolder.findViewById(R.id.ivTxIcon);

                    tvTxTitle.setText(tx.getTitle());
                    tvTxDate.setText(dateFormat.format(new Date(tx.getTimestamp())));
                    tvTxAmount.setText(currencyFormatter.format(tx.getTotalAmount()));

                    String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                            ? tx.getTransactionId().substring(0, 6).toUpperCase()
                            : "UNKNOWN";
                    tvTxNumber.setText("Txn: #" + shortId);

                    tvTxTotalAmount.setVisibility(View.GONE);
                    tvTxSource.setVisibility(View.GONE);

                    if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                        tvTxStatus.setText("Bill Paid");
                        tvTxStatus.setTextColor(Color.parseColor("#388E3C"));
                        badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                        cardIconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                        ivTxIcon.setColorFilter(Color.parseColor("#388E3C"));
                    } else if ("PAY_CREDIT".equals(tx.getTransactionType())) {
                        tvTxStatus.setText("Paid Credit");
                        tvTxStatus.setTextColor(Color.parseColor("#388E3C"));
                        badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                        cardIconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                        ivTxIcon.setColorFilter(Color.parseColor("#388E3C"));
                    } else {
                        if (tx.isBilled()) {
                            tvTxStatus.setText("Billed");
                            tvTxStatus.setTextColor(Color.parseColor("#D32F2F"));
                            badgeStatus.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
                        } else {
                            tvTxStatus.setText("Unbilled");
                            tvTxStatus.setTextColor(Color.parseColor("#F57C00"));
                            badgeStatus.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                        }
                        cardIconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
                        ivTxIcon.setColorFilter(Color.parseColor("#1565C0"));
                    }

                    itemHolder.setOnClickListener(v -> showTransactionDetailsSheet(context, tx));
                    holder.layoutTransactionsContainer.addView(itemHolder);
                }
            }
        }

        // Extremely important to prevent memory leaks from running timers inside a scrollable list
        @Override
        public void onViewRecycled(@NonNull AccordionViewHolder holder) {
            super.onViewRecycled(holder);
            holder.cancelTimer();
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

            if (ivCloseSheet != null) ivCloseSheet.setOnClickListener(v -> sheetDialog.dismiss());

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
            tvSheetTxDate.setText(dateFormat.format(new Date(tx.getTimestamp())));

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

        public static class AccordionViewHolder extends RecyclerView.ViewHolder {
            LinearLayout layoutHeaderClickable, layoutTransactionsContainer;
            View viewDivider;
            com.cluster.fintrack.MaxHeightScrollView scrollTransactionsContainer;
            TextView tvMonthHeader, tvTotalBillAmount, tvCashbackAmount, tvUndoTimer;
            ImageView ivExpandToggle, ivUndoStatement;
            CountDownTimer countDownTimer;

            public AccordionViewHolder(@NonNull View itemView) {
                super(itemView);
                layoutHeaderClickable = itemView.findViewById(R.id.layoutHeaderClickable);
                layoutTransactionsContainer = itemView.findViewById(R.id.layoutTransactionsContainer);
                viewDivider = itemView.findViewById(R.id.viewDivider);
                scrollTransactionsContainer = itemView.findViewById(R.id.scrollTransactionsContainer);
                tvMonthHeader = itemView.findViewById(R.id.tvMonthHeader);
                tvTotalBillAmount = itemView.findViewById(R.id.tvTotalBillAmount);
                tvCashbackAmount = itemView.findViewById(R.id.tvCashbackAmount);
                ivExpandToggle = itemView.findViewById(R.id.ivExpandToggle);
                ivUndoStatement = itemView.findViewById(R.id.ivUndoStatement);
                tvUndoTimer = itemView.findViewById(R.id.tvUndoTimer);
            }

            public void cancelTimer() {
                if (countDownTimer != null) {
                    countDownTimer.cancel();
                    countDownTimer = null;
                }
            }
        }
    }
}