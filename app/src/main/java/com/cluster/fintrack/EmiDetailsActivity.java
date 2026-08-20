package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressLint("SetTextI18n")
public class EmiDetailsActivity extends AppCompatActivity {

    private String transactionId;
    private Transaction emiTx;

    private TextView tvEmiTitle, tvOriginalAmount, tvBlockedLimit;
    private LinearProgressIndicator progressLimitBlock;

    private LinearLayout layoutFinMateBreakdown;
    private LinearLayout layoutScheduleGrid;
    private MaterialButton btnForeclose;

    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_emi_details);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainEmiDetails), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        transactionId = getIntent().getStringExtra("TRANSACTION_ID");

        if (transactionId == null) {
            Toast.makeText(this, "Error: Missing Transaction ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        fetchEmiData();
    }

    private void initializeViews() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        tvEmiTitle = findViewById(R.id.tvEmiTitle);
        tvOriginalAmount = findViewById(R.id.tvOriginalAmount);
        tvBlockedLimit = findViewById(R.id.tvBlockedLimit);
        progressLimitBlock = findViewById(R.id.progressLimitBlock);

        layoutFinMateBreakdown = findViewById(R.id.layoutFinMateBreakdown);
        layoutScheduleGrid = findViewById(R.id.layoutScheduleGrid);
        btnForeclose = findViewById(R.id.btnForeclose);

        btnForeclose.setOnClickListener(v -> Toast.makeText(this, "Foreclosure coming soon!", Toast.LENGTH_SHORT).show());
    }

    private void fetchEmiData() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("Users").document(user.getUid())
                .collection("Transactions").document(transactionId)
                .get()
                .addOnSuccessListener(doc -> {
                    emiTx = doc.toObject(Transaction.class);
                    if (emiTx != null && emiTx.getEmiData() != null) {
                        populateHeader();
                        populateAmortizationGrid();
                        fetchFinMatesAndPopulateBreakdown(user.getUid());
                    } else {
                        Toast.makeText(this, "Invalid EMI Data", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    private void populateHeader() {
        tvEmiTitle.setText(emiTx.getTitle());
        tvOriginalAmount.setText("Original Total: " + currencyFormatter.format(emiTx.getTotalAmount()));

        double remainingLimit = emiTx.getEmiData().getRemainingEmiPrincipal();
        tvBlockedLimit.setText(currencyFormatter.format(remainingLimit));

        int progress = 0;
        if (emiTx.getTotalAmount() > 0) {
            progress = (int) ((remainingLimit / emiTx.getTotalAmount()) * 100);
        }
        progressLimitBlock.setProgress(progress);

        if (remainingLimit <= 0) {
            btnForeclose.setVisibility(View.GONE);
        }
    }

    private void fetchFinMatesAndPopulateBreakdown(String userId) {
        FirebaseFirestore.getInstance().collection("Users").document(userId).collection("FinMates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, String> namesMap = new HashMap<>();
                    namesMap.put("self", "Self (You)");
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FinMate fm = doc.toObject(FinMate.class);
                        if (fm != null) namesMap.put(fm.getFinMateId(), fm.getName());
                    }

                    layoutFinMateBreakdown.removeAllViews();
                    Transaction.EmiData data = emiTx.getEmiData();

                    if (data.getOriginalPrincipalSplits() == null) return;

                    for (Map.Entry<String, Double> entry : data.getOriginalPrincipalSplits().entrySet()) {
                        String mateId = entry.getKey();
                        double principalShare = entry.getValue();

                        double profit = 0.0;
                        if (data.getTotalPrivilegeCharges() != null) {
                            Double profitObj = data.getTotalPrivilegeCharges().get(mateId);
                            if (profitObj != null) profit = profitObj;
                        }

                        String name = namesMap.getOrDefault(mateId, "Unknown");

                        TextView tvRow = new TextView(this);
                        tvRow.setTextColor(Color.parseColor("#101828"));
                        tvRow.setTextSize(13f);
                        tvRow.setPadding(0, 8, 0, 8);

                        String text = "• " + name + ": Principal Share " + currencyFormatter.format(principalShare);
                        if (profit > 0) {
                            text += " | Privilege Cash Profit: " + currencyFormatter.format(profit);
                        }
                        tvRow.setText(text);
                        layoutFinMateBreakdown.addView(tvRow);
                    }
                });
    }

    private void populateAmortizationGrid() {
        layoutScheduleGrid.removeAllViews();
        if (emiTx.getEmiData().getAmortizationSchedule() == null) return;

        List<Transaction.EmiMonth> schedule = emiTx.getEmiData().getAmortizationSchedule();

        for (int i = 0; i < schedule.size(); i++) {
            Transaction.EmiMonth month = schedule.get(i);
            View row = LayoutInflater.from(this).inflate(R.layout.item_emi_details_month_row, layoutScheduleGrid, false);

            TextView tvNum = row.findViewById(R.id.tvMonthNumber);
            TextView tvPrin = row.findViewById(R.id.tvPrincipal);
            TextView tvInt = row.findViewById(R.id.tvInterest);
            TextView tvTotal = row.findViewById(R.id.tvTotalBankDue);
            MaterialCardView badgeStatus = row.findViewById(R.id.badgeStatus);
            TextView tvStatus = row.findViewById(R.id.tvStatus);

            tvNum.setText(String.valueOf(month.getMonthNumber()));
            tvPrin.setText(currencyFormatter.format(month.getBankPrincipal()));

            double intAndGst = month.getBankInterest() + month.getBankGst();
            tvInt.setText(currencyFormatter.format(intAndGst) + " (GST: " + currencyFormatter.format(month.getBankGst()) + ")");
            tvTotal.setText(currencyFormatter.format(month.getTotalBankDueForMonth()));

            if (month.isCancelled()) {
                tvStatus.setText("Cancelled");
                tvStatus.setTextColor(Color.parseColor("#D32F2F"));
                badgeStatus.setCardBackgroundColor(Color.parseColor("#FFEBEE"));
            } else if (month.isBilled()) {
                tvStatus.setText("Billed");
                tvStatus.setTextColor(Color.parseColor("#388E3C"));
                badgeStatus.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            } else {
                tvStatus.setText("Transfer to Unbilled");
                tvStatus.setTextColor(Color.parseColor("#1565C0"));
                badgeStatus.setCardBackgroundColor(Color.parseColor("#E3F2FD"));

                final int monthIndex = i;
                badgeStatus.setOnClickListener(v -> transferMonthToUnbilled(monthIndex));
            }

            layoutScheduleGrid.addView(row);
        }
    }

    private void transferMonthToUnbilled(int monthIndex) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Transaction.EmiMonth month = emiTx.getEmiData().getAmortizationSchedule().get(monthIndex);
        if (month.isBilled()) return;

        double totalMonthDue = month.getTotalBankDueForMonth();
        double remainingPrincipal = emiTx.getEmiData().getRemainingEmiPrincipal() - month.getBankPrincipal();

        month.setBilled(true);
        emiTx.getEmiData().setRemainingEmiPrincipal(Math.max(0, remainingPrincipal));

        WriteBatch batch = db.batch();

        // 1. Update Master EMI Transaction
        DocumentReference masterRef = db.collection("Users").document(userId).collection("Transactions").document(transactionId);
        batch.set(masterRef, emiTx, SetOptions.merge());

        // 2. Create Unbilled Spend Transaction for this Month's Installment
        String newTxId = generateRandomId();
        Map<String, Transaction.TransactionSplit> splits = new HashMap<>();

        Map<String, Double> originalSplits = emiTx.getEmiData().getOriginalPrincipalSplits();
        double totalOriginal = emiTx.getTotalAmount();

        for (Map.Entry<String, Double> entry : originalSplits.entrySet()) {
            String mateId = entry.getKey();
            double ratio = totalOriginal > 0 ? (entry.getValue() / totalOriginal) : 0;
            double matePrincipalShare = month.getBankPrincipal() * ratio;
            double mateInterestShare = (month.getBankInterest() + month.getBankGst()) * ratio;

            // Privilege charge applied as cash, principal/interest as card
            double privilegeCharge = 0.0;
            if (emiTx.getEmiData().getTotalPrivilegeCharges() != null) {
                Double pCharge = emiTx.getEmiData().getTotalPrivilegeCharges().get(mateId);
                if (pCharge != null) {
                    // Distribute privilege charge evenly across months or attach full
                    privilegeCharge = pCharge / emiTx.getEmiData().getAmortizationSchedule().size();
                }
            }

            splits.put(mateId, new Transaction.TransactionSplit(matePrincipalShare + mateInterestShare, privilegeCharge, 0.0));
        }

        Transaction monthlySpendTx = new Transaction(
                newTxId,
                "CARD_SPEND",
                emiTx.getCardId(),
                emiTx.getTitle() + " (Month " + month.getMonthNumber() + ")",
                System.currentTimeMillis(),
                totalMonthDue,
                false
        );
        monthlySpendTx.setSplits(splits);

        DocumentReference newTxRef = db.collection("Users").document(userId).collection("Transactions").document(newTxId);
        batch.set(newTxRef, monthlySpendTx);

        // 3. Update FinMate Balances
        for (Map.Entry<String, Transaction.TransactionSplit> splitEntry : splits.entrySet()) {
            String mateId = splitEntry.getKey();
            if ("self".equals(mateId)) continue;

            double cardAmt = splitEntry.getValue().getCardAmount();
            double cashAmt = splitEntry.getValue().getCashAmount();

            DocumentReference fmRef = db.collection("Users").document(userId).collection("FinMates").document(mateId);
            batch.update(fmRef,
                    "receivableCardAmount", FieldValue.increment(cardAmt),
                    "receivableCashAmount", FieldValue.increment(cashAmt),
                    "totalReceivable", FieldValue.increment(cardAmt + cashAmt)
            );
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Month " + month.getMonthNumber() + " transferred to unbilled spends!", Toast.LENGTH_SHORT).show();
            fetchEmiData(); // Refresh UI
        }).addOnFailureListener(e -> Toast.makeText(this, "Failed to transfer: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private String generateRandomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }
}