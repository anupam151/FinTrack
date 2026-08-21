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

    private double calculateTotalCardDueForMonth(Transaction.EmiMonth month, Transaction.EmiData emiData) {
        double principalAmt = month.getBankPrincipal();
        double interestAmt = month.getBankInterest();
        double gstAmt = month.getBankGst();

        double bankFeeAmount = 0.0;
        if (month.getMonthNumber() == 1 && emiData.getBankProcessingFee() > 0) {
            bankFeeAmount = emiData.getBankProcessingFee() + emiData.getBankProcessingFeeGst();
        }

        return principalAmt + interestAmt + gstAmt + bankFeeAmount;
    }

    private void transferMonthToUnbilled(int monthIndex) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        Transaction.EmiMonth month = emiTx.getEmiData().getAmortizationSchedule().get(monthIndex);
        if (month.isBilled()) return;

        double principalAmt = month.getBankPrincipal();
        double interestAmt = month.getBankInterest();
        double gstAmt = month.getBankGst();
        double pfAmt = (month.getMonthNumber() == 1) ? emiTx.getEmiData().getBankProcessingFee() : 0.0;
        double pfGstAmt = (month.getMonthNumber() == 1) ? emiTx.getEmiData().getBankProcessingFeeGst() : 0.0;

        double totalCardDueForMonth = calculateTotalCardDueForMonth(month, emiTx.getEmiData());
        double remainingPrincipal = emiTx.getEmiData().getRemainingEmiPrincipal() - principalAmt;

        month.setBilled(true);
        emiTx.getEmiData().setRemainingEmiPrincipal(Math.max(0, remainingPrincipal));

        WriteBatch batch = db.batch();
        long currentTimestamp = System.currentTimeMillis();
        String baseTitle = emiTx.getTitle() + " (Month " + month.getMonthNumber() + ")";

        DocumentReference masterRef = db.collection("Users").document(userId).collection("Transactions").document(transactionId);
        batch.set(masterRef, emiTx, SetOptions.merge());

        // ======================================================================
        // 1. CARD TRANSACTIONS (5 separate entries, visible ONLY to Card)
        // ======================================================================

        Transaction tPrin = new Transaction(generateRandomId(), "CARD_SPEND", emiTx.getCardId(), baseTitle + " - Principal", currentTimestamp, principalAmt, false);
        tPrin.setSplits(new HashMap<>());
        batch.set(db.collection("Users").document(userId).collection("Transactions").document(tPrin.getTransactionId()), tPrin);

        Transaction tInt = new Transaction(generateRandomId(), "CARD_SPEND", emiTx.getCardId(), baseTitle + " - Interest", currentTimestamp, interestAmt, false);
        tInt.setSplits(new HashMap<>());
        batch.set(db.collection("Users").document(userId).collection("Transactions").document(tInt.getTransactionId()), tInt);

        if (gstAmt > 0) {
            Transaction tGst = new Transaction(generateRandomId(), "CARD_SPEND", emiTx.getCardId(), baseTitle + " - GST on Interest", currentTimestamp, gstAmt, false);
            tGst.setSplits(new HashMap<>());
            batch.set(db.collection("Users").document(userId).collection("Transactions").document(tGst.getTransactionId()), tGst);
        }

        if (pfAmt > 0) {
            Transaction tPf = new Transaction(generateRandomId(), "CARD_SPEND", emiTx.getCardId(), baseTitle + " - Processing Fee", currentTimestamp, pfAmt, false);
            tPf.setSplits(new HashMap<>());
            batch.set(db.collection("Users").document(userId).collection("Transactions").document(tPf.getTransactionId()), tPf);
        }

        if (pfGstAmt > 0) {
            Transaction tPfGst = new Transaction(generateRandomId(), "CARD_SPEND", emiTx.getCardId(), baseTitle + " - GST on PF", currentTimestamp, pfGstAmt, false);
            tPfGst.setSplits(new HashMap<>());
            batch.set(db.collection("Users").document(userId).collection("Transactions").document(tPfGst.getTransactionId()), tPfGst);
        }

        // ======================================================================
        // 2. FINMATE TRANSACTION (1 consolidated entry, visible ONLY to FinMate)
        // ======================================================================

        Map<String, Double> originalSplits = emiTx.getEmiData().getOriginalPrincipalSplits();
        if (originalSplits != null && !originalSplits.isEmpty()) {
            boolean hasFinMate = false;
            for (String key : originalSplits.keySet()) {
                if (!"self".equals(key)) {
                    hasFinMate = true;
                    break;
                }
            }

            if (hasFinMate) {
                double totalMonths = emiTx.getEmiData().getAmortizationSchedule().size();

                // --- THE FIX: Correctly sum the Transaction's Total Amount to include Cash Profit! ---
                double totalGhostTxAmount = totalCardDueForMonth;
                Map<String, Double> privMap = emiTx.getEmiData().getTotalPrivilegeCharges();
                if (privMap != null) {
                    for (Map.Entry<String, Double> pEntry : privMap.entrySet()) {
                        if (!"self".equals(pEntry.getKey()) && pEntry.getValue() != null) {
                            totalGhostTxAmount += (pEntry.getValue() / totalMonths);
                        }
                    }
                }

                String ghostCardId = emiTx.getCardId() + "_GHOST";
                // Create transaction with the accurate math (Card + Cash limits combined)
                Transaction tFinMate = new Transaction(generateRandomId(), "CARD_SPEND", ghostCardId, baseTitle, currentTimestamp, totalGhostTxAmount, false);

                Map<String, Transaction.TransactionSplit> consolidatedSplits = new HashMap<>();
                double totalOriginal = emiTx.getTotalAmount();

                for (Map.Entry<String, Double> entry : originalSplits.entrySet()) {
                    String mateId = entry.getKey();
                    if ("self".equals(mateId)) continue;

                    double ratio = totalOriginal > 0 ? (entry.getValue() / totalOriginal) : 0;
                    double cardShare = totalCardDueForMonth * ratio;

                    double privilegeCharge = 0.0;
                    if (privMap != null) {
                        Double pCharge = privMap.get(mateId);
                        if (pCharge != null) {
                            privilegeCharge = pCharge / totalMonths;
                        }
                    }

                    // Assign cardShare as Card Due, privilegeCharge as Cash Due
                    consolidatedSplits.put(mateId, new Transaction.TransactionSplit(cardShare, privilegeCharge, 0.0));

                    DocumentReference fmRef = db.collection("Users").document(userId).collection("FinMates").document(mateId);
                    batch.update(fmRef,
                            "receivableCardAmount", FieldValue.increment(cardShare),
                            "receivableCashAmount", FieldValue.increment(privilegeCharge),
                            "totalReceivable", FieldValue.increment(cardShare + privilegeCharge)
                    );
                }

                tFinMate.setSplits(consolidatedSplits);
                batch.set(db.collection("Users").document(userId).collection("Transactions").document(tFinMate.getTransactionId()), tFinMate);
            }
        }

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Month " + month.getMonthNumber() + " transferred successfully!", Toast.LENGTH_SHORT).show();
            fetchEmiData();
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