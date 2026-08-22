package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
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
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressLint("SetTextI18n")
public class CardLedgerActivity extends AppCompatActivity {

    private TextView tvAvailableLimit, tvTotalUsed, tvBilledDue, tvUnbilled;

    private LinearLayout layoutCashbackSummary;
    private TextView tvLifetimeCashback, tvUnbilledCashback;

    private RecyclerView recyclerViewCardTx;
    private TextView layoutEmptyState;
    private TextInputEditText etSearchCardTx;

    private CardTransactionAdapter adapter;
    private final List<Transaction> masterList = new ArrayList<>();
    private final List<Transaction> displayList = new ArrayList<>();

    private String cardId;
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

        cardId = getIntent().getStringExtra("CARD_ID");
        String bankName = getIntent().getStringExtra("BANK_NAME");
        String cardName = getIntent().getStringExtra("CARD_NAME");
        String cardType = getIntent().getStringExtra("CARD_TYPE");
        String last4 = getIntent().getStringExtra("LAST4");
        totalLimit = getIntent().getDoubleExtra("TOTAL_LIMIT", 0.0);
        String themeColor = getIntent().getStringExtra("THEME_COLOR");

        if (cardId == null) {
            finish();
            return;
        }

        initializeViewsAndMockup(bankName, cardName, cardType, last4, themeColor);
        verifyCashbackEligibility();
        fetchCardTransactions();
    }

    private void initializeViewsAndMockup(String bankName, String cardName, String cardType, String last4, String themeColor) {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        View viewDynamicHeader = findViewById(R.id.viewDynamicHeader);
        TextView tvBankName = findViewById(R.id.tvBankName);
        TextView tvCardName = findViewById(R.id.tvCardName);
        TextView tvCardType = findViewById(R.id.tvCardType);
        TextView tvCardNumber = findViewById(R.id.tvCardNumber);
        TextView tvTotalLimit = findViewById(R.id.tvTotalLimit);

        MaterialButton btnAllTransactions = findViewById(R.id.btnAllTransactions);
        MaterialButton btnGenerateBill = findViewById(R.id.btnGenerateBill);
        MaterialButton btnActiveEmis = findViewById(R.id.btnActiveEmis);

        tvBilledDue = findViewById(R.id.tvBilledDue);
        tvUnbilled = findViewById(R.id.tvUnbilled);
        tvAvailableLimit = findViewById(R.id.tvAvailableLimit);
        tvTotalUsed = findViewById(R.id.tvTotalUsed);

        layoutCashbackSummary = findViewById(R.id.layoutCashbackSummary);
        tvLifetimeCashback = findViewById(R.id.tvLifetimeCashback);
        tvUnbilledCashback = findViewById(R.id.tvUnbilledCashback);

        recyclerViewCardTx = findViewById(R.id.recyclerViewCardTx);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);
        etSearchCardTx = findViewById(R.id.etSearchCardTx);

        tvBankName.setText(bankName != null ? bankName : "Bank");
        tvCardName.setText(cardName != null ? cardName : "Credit Card");
        tvCardType.setText(cardType != null ? cardType : "Visa");
        tvCardNumber.setText(" " + (last4 != null ? last4 : "0000"));

        if (themeColor != null && !themeColor.isEmpty()) {
            try {
                viewDynamicHeader.setBackgroundColor(Color.parseColor(themeColor));
            } catch (Exception ignored) {}
        }

        tvTotalLimit.setText(currencyFormatter.format(totalLimit));

        btnAllTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(this, CardAllTransactionsActivity.class);
            intent.putExtra("CARD_ID", cardId);
            intent.putExtra("CARD_NAME", cardName);
            startActivity(intent);
        });

        btnGenerateBill.setOnClickListener(v -> openBillGenerationSheet());

        if (btnActiveEmis != null) {
            btnActiveEmis.setOnClickListener(v -> {
                Intent intent = new Intent(this, CardActiveEmisActivity.class);
                intent.putExtra("CARD_ID", cardId);
                intent.putExtra("CARD_NAME", cardName);
                startActivity(intent);
            });
        }

        View fabAddTransaction = findViewById(R.id.btnAddTransactionHeader);
        if (fabAddTransaction != null) {
            fabAddTransaction.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddTransactionActivity.class);
                intent.putExtra("SOURCE", "CARD_LEDGER");
                intent.putExtra("CARD_ID", cardId);

                String shortBankName = BankUtils.getBankInitials(bankName);
                String fullCardDisplayName = cardName + " - " + shortBankName + " (" + (last4 != null ? last4 : "0000") + ")";
                intent.putExtra("CARD_NAME", fullCardDisplayName);

                startActivity(intent);
            });
        }

        recyclerViewCardTx.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CardTransactionAdapter(displayList, cardName);
        recyclerViewCardTx.setAdapter(adapter);

        etSearchCardTx.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterTransactions(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void verifyCashbackEligibility() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            FirebaseFirestore.getInstance().collection("Users").document(user.getUid())
                    .collection("Cards").document(cardId).get()
                    .addOnSuccessListener(doc -> {
                        if (doc.exists()) {
                            Card c = doc.toObject(Card.class);
                            if (c != null && c.isCashbackCard()) {
                                layoutCashbackSummary.setVisibility(View.VISIBLE);
                            }
                        }
                    });
        }
    }

    private void openBillGenerationSheet() {
        List<Transaction> unbilledList = new ArrayList<>();

        for (Transaction tx : masterList) {
            boolean isUnbilledSpend = !tx.isBilled() && ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType()));
            boolean isRecentPayment = "CARD_PAYMENT".equals(tx.getTransactionType()) && (tx.getBilledMonth() == null || tx.getBilledMonth().trim().isEmpty());

            if (isUnbilledSpend || isRecentPayment) {
                unbilledList.add(tx);
            }
        }

        if (unbilledList.isEmpty()) {
            Toast.makeText(this, "No unbilled transactions available to bill.", Toast.LENGTH_SHORT).show();
            return;
        }

        BottomSheetDialog sheetDialog = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.dialog_generate_bill, new android.widget.FrameLayout(this), false);
        sheetDialog.setContentView(sheetView);

        LinearLayout layoutItemsContainer = sheetView.findViewById(R.id.layoutBillItemsContainer);
        MaterialButton btnConfirmGenerate = sheetView.findViewById(R.id.btnConfirmGenerate);

        List<Transaction> selectedToBill = new ArrayList<>(unbilledList);
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        for (Transaction tx : unbilledList) {
            View rowView = LayoutInflater.from(this).inflate(R.layout.item_bill_generation_row, layoutItemsContainer, false);

            MaterialCheckBox cbIncludeTx = rowView.findViewById(R.id.cbIncludeTx);
            TextView tvTxTitle = rowView.findViewById(R.id.tvTxTitle);
            TextView tvTxDate = rowView.findViewById(R.id.tvTxDate);
            TextView tvTxAmount = rowView.findViewById(R.id.tvTxAmount);

            tvTxTitle.setText(tx.getTitle());
            tvTxDate.setText(sdf.format(new Date(tx.getTimestamp())));

            if ("CARD_PAYMENT".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                tvTxAmount.setText("+" + currencyFormatter.format(tx.getTotalAmount()));
                tvTxAmount.setTextColor(Color.parseColor("#388E3C")); // Green
            } else {
                tvTxAmount.setText(currencyFormatter.format(tx.getTotalAmount()));
                tvTxAmount.setTextColor(Color.parseColor("#101828")); // Standard Dark
            }

            cbIncludeTx.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedToBill.add(tx);
                } else {
                    selectedToBill.remove(tx);
                }
            });

            rowView.setOnClickListener(v -> cbIncludeTx.setChecked(!cbIncludeTx.isChecked()));
            layoutItemsContainer.addView(rowView);
        }

        btnConfirmGenerate.setOnClickListener(v -> {
            if (selectedToBill.isEmpty()) {
                Toast.makeText(this, "Please select at least one transaction.", Toast.LENGTH_SHORT).show();
                return;
            }
            sheetDialog.dismiss();
            showBillDateSelectionDialog(selectedToBill);
        });

        sheetDialog.show();
    }

    private void showBillDateSelectionDialog(List<Transaction> selectedToBill) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_month_year_picker, null);
        NumberPicker pickerMonth = dialogView.findViewById(R.id.pickerMonth);
        NumberPicker pickerYear = dialogView.findViewById(R.id.pickerYear);

        String[] months = new String[]{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};
        pickerMonth.setMinValue(0);
        pickerMonth.setMaxValue(11);
        pickerMonth.setDisplayedValues(months);

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);
        pickerYear.setMinValue(currentYear - 2);
        pickerYear.setMaxValue(currentYear + 5);
        pickerYear.setValue(currentYear);

        pickerMonth.setValue(cal.get(Calendar.MONTH));

        new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .setPositiveButton("Verify & Generate", (dialog, which) -> {
                    String selectedMonthYear = months[pickerMonth.getValue()] + " " + pickerYear.getValue();
                    checkIfBillExistsAndGenerate(selectedToBill, selectedMonthYear);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void checkIfBillExistsAndGenerate(List<Transaction> selectedToBill, String selectedMonthYear) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(user.getUid()).collection("Transactions")
                .whereEqualTo("cardId", cardId)
                .whereEqualTo("billedMonth", selectedMonthYear)
                .limit(1)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.isEmpty()) {
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("Generation Failed")
                                .setMessage("A statement for " + selectedMonthYear + " has already been generated. You cannot generate multiple statements for the same month.")
                                .setPositiveButton("OK", null)
                                .setCancelable(false)
                                .show();
                    } else {
                        executeGenerateBill(selectedToBill, selectedMonthYear);
                    }
                });
    }

    private void executeGenerateBill(List<Transaction> selectedToBill, String selectedMonthYear) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        WriteBatch batch = db.batch();

        long generatedAt = System.currentTimeMillis();

        for (Transaction tx : selectedToBill) {
            batch.update(
                    db.collection("Users").document(user.getUid()).collection("Transactions").document(tx.getTransactionId()),
                    "billed", true,
                    "billedMonth", selectedMonthYear,
                    "statementGeneratedAt", generatedAt
            );
        }

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Statement for " + selectedMonthYear + " Generated!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Failed to generate bill.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchCardTransactions() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(currentUser.getUid()).collection("Transactions")
                .whereEqualTo("cardId", cardId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    masterList.clear();

                    double billedSpends = 0.0;
                    double unbilledSpends = 0.0;
                    double totalPayments = 0.0;

                    double lifetimeCashback = 0.0;
                    double unbilledCashback = 0.0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx != null) {
                            masterList.add(tx);

                            // --- PERFECTED CASHBACK LOGIC ---
                            // 1. If billed -> goes to Lifetime Cashback
                            // 2. If unbilled -> goes to Unbilled Cashback
                            // Because it applies to all transactions regardless of type,
                            // the EMI Reversal's negative cashback perfectly offsets the original!
                            if (tx.isBilled()) {
                                lifetimeCashback += tx.getCashbackEarned();
                            } else {
                                unbilledCashback += tx.getCashbackEarned();
                            }

                            // --- FINANCIAL DEBT CALCULATION ---
                            if ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                                if (tx.isBilled()) {
                                    billedSpends += tx.getTotalAmount();
                                } else {
                                    unbilledSpends += tx.getTotalAmount();
                                }
                            } else if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                                totalPayments += tx.getTotalAmount();
                            }
                            // Keeps the card limit accurately blocked using the hidden Master EMI transaction
                            else if ("EMI_MASTER".equals(tx.getTransactionType()) && tx.getEmiData() != null) {
                                unbilledSpends += tx.getEmiData().getRemainingEmiPrincipal();
                            }
                        }
                    }

                    masterList.sort((t1, t2) -> Long.compare(t2.getTimestamp(), t1.getTimestamp()));

                    double finalBilledDue = billedSpends - totalPayments;
                    double finalUnbilledDue = unbilledSpends;

                    if (finalBilledDue < 0) {
                        finalUnbilledDue += finalBilledDue;
                        finalBilledDue = 0;
                    }
                    if (finalUnbilledDue < 0) {
                        finalUnbilledDue = 0;
                    }

                    double finalTotalUsed = finalBilledDue + finalUnbilledDue;

                    tvTotalUsed.setText(currencyFormatter.format(finalTotalUsed));
                    tvBilledDue.setText(currencyFormatter.format(finalBilledDue));
                    tvUnbilled.setText(currencyFormatter.format(finalUnbilledDue));
                    tvAvailableLimit.setText(currencyFormatter.format(Math.max(0, totalLimit - finalTotalUsed)));

                    tvLifetimeCashback.setText(currencyFormatter.format(lifetimeCashback));
                    tvUnbilledCashback.setText(currencyFormatter.format(unbilledCashback));

                    filterTransactions(etSearchCardTx.getText() != null ? etSearchCardTx.getText().toString() : "");
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterTransactions(String query) {
        displayList.clear();
        String lowerCaseQuery = query.toLowerCase().trim();

        for (Transaction tx : masterList) {
            String title = tx.getTitle() != null ? tx.getTitle().toLowerCase() : "";
            boolean matchesSearch = title.contains(lowerCaseQuery);

            boolean isUnbilledSpend = !tx.isBilled() && ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType()));
            boolean isRecentPayment = "CARD_PAYMENT".equals(tx.getTransactionType()) && (tx.getBilledMonth() == null || tx.getBilledMonth().trim().isEmpty());

            if (matchesSearch && (isUnbilledSpend || isRecentPayment)) {
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
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
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

            String shortId = tx.getTransactionId() != null && tx.getTransactionId().length() >= 6
                    ? tx.getTransactionId().substring(0, 6).toUpperCase()
                    : "UNKNOWN";
            holder.tvTxNumber.setText("Txn: #" + shortId);

            holder.tvTxTotalAmount.setVisibility(View.GONE);
            holder.tvTxSource.setVisibility(View.GONE);

            if ("CARD_PAYMENT".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                holder.tvTxAmount.setText("+" + currencyFormatter.format(tx.getTotalAmount()));
                holder.tvTxAmount.setTextColor(Color.parseColor("#388E3C")); // Green
            } else {
                holder.tvTxAmount.setText(currencyFormatter.format(tx.getTotalAmount()));
                holder.tvTxAmount.setTextColor(Color.parseColor("#101828")); // Dark
            }

            if (tx.isEmi() && "CARD_SPEND".equals(tx.getTransactionType())) {
                holder.tvTxStatus.setText("Converted to EMI");
                holder.tvTxStatus.setTextColor(Color.parseColor("#9C27B0")); // Purple
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#F3E5F5"));
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#F3E5F5"));
                holder.ivTxIcon.setColorFilter(Color.parseColor("#9C27B0"));
            } else if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                if (tx.getTitle() != null && tx.getTitle().contains("EMI Reversal")) {
                    holder.tvTxStatus.setText("EMI Refund");
                    holder.tvTxStatus.setTextColor(Color.parseColor("#F57C00")); // Orange warning
                    holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                    holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                    holder.ivTxIcon.setColorFilter(Color.parseColor("#F57C00"));
                } else {
                    holder.tvTxStatus.setText("Bill Paid");
                    holder.tvTxStatus.setTextColor(Color.parseColor("#388E3C")); // Green
                    holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                    holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
                    holder.ivTxIcon.setColorFilter(Color.parseColor("#388E3C"));
                }
            } else {
                holder.tvTxStatus.setText("Unbilled");
                holder.tvTxStatus.setTextColor(Color.parseColor("#F57C00")); // Orange
                holder.badgeStatus.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
                holder.cardIconContainer.setCardBackgroundColor(Color.parseColor("#E3F2FD")); // Blue Icon Background
                holder.ivTxIcon.setColorFilter(Color.parseColor("#1565C0")); // Blue Icon
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
            ImageView ivCloseSheet = sheetView.findViewById(R.id.ivCloseSheet);
            ImageView ivCopyTxId = sheetView.findViewById(R.id.ivCopyTxId);

            com.google.android.material.button.MaterialButton btnConvertToEmi = sheetView.findViewById(R.id.btnConvertToEmi);

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

            if (btnConvertToEmi != null) {
                if ("CARD_SPEND".equals(tx.getTransactionType()) && !tx.isEmi()) {
                    btnConvertToEmi.setVisibility(View.VISIBLE);
                    btnConvertToEmi.setOnClickListener(v -> {
                        sheetDialog.dismiss();
                        Intent intent = new Intent(context, ConvertEmiActivity.class);
                        intent.putExtra("TRANSACTION_ID", tx.getTransactionId());
                        intent.putExtra("CARD_ID", tx.getCardId());
                        context.startActivity(intent);
                    });
                } else {
                    btnConvertToEmi.setVisibility(View.GONE);
                }
            }

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