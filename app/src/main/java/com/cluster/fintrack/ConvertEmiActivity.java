package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressLint("SetTextI18n")
public class ConvertEmiActivity extends AppCompatActivity {

    private String transactionId;
    private Transaction originalTx;

    private NestedScrollView mainScrollView;

    private TextView tvOriginalTitle, tvOriginalAmount;
    private MaterialAutoCompleteTextView spinTenure;
    private TextInputEditText etInterestRate, etProcessingFee, etGstOnPf, etTotalPrivilegeCharge, etConversionAmount;

    private MaterialCardView cardPrivilegeCharges, cardAmortizationGrid;
    private LinearLayout layoutGridContainer;
    private MaterialButton btnConvertEmi;

    private final List<Transaction.EmiMonth> generatedSchedule = new ArrayList<>();
    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_convert_emi);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainConvertEmi), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        transactionId = getIntent().getStringExtra("TRANSACTION_ID");
        String cardId = getIntent().getStringExtra("CARD_ID");

        if (transactionId == null || cardId == null) {
            finish();
            return;
        }

        initializeViews();
        fetchOriginalTransaction();
    }

    private void initializeViews() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        mainScrollView = findViewById(R.id.mainScrollView);

        tvOriginalTitle = findViewById(R.id.tvOriginalTitle);
        tvOriginalAmount = findViewById(R.id.tvOriginalAmount);

        // Added the new Conversion Amount field binding
        etConversionAmount = findViewById(R.id.etConversionAmount);

        spinTenure = findViewById(R.id.spinTenure);
        etInterestRate = findViewById(R.id.etInterestRate);
        etProcessingFee = findViewById(R.id.etProcessingFee);
        etGstOnPf = findViewById(R.id.etGstOnPf);

        cardPrivilegeCharges = findViewById(R.id.cardPrivilegeCharges);
        etTotalPrivilegeCharge = findViewById(R.id.etTotalPrivilegeCharge);

        cardAmortizationGrid = findViewById(R.id.cardAmortizationGrid);
        layoutGridContainer = findViewById(R.id.layoutGridContainer);

        MaterialButton btnGenerateGrid = findViewById(R.id.btnGenerateGrid);
        btnConvertEmi = findViewById(R.id.btnConvertEmi);

        String[] tenures = new String[]{"3", "6", "9", "12", "18", "24"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, tenures);
        spinTenure.setAdapter(adapter);

        btnGenerateGrid.setOnClickListener(v -> generateAmortizationGrid());
        btnConvertEmi.setOnClickListener(v -> executeReversalAndConvert());
    }

    private void fetchOriginalTransaction() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("Users").document(user.getUid())
                .collection("Transactions").document(transactionId)
                .get()
                .addOnSuccessListener(doc -> {
                    originalTx = doc.toObject(Transaction.class);
                    if (originalTx != null) {
                        tvOriginalTitle.setText(originalTx.getTitle());
                        tvOriginalAmount.setText(currencyFormatter.format(originalTx.getTotalAmount()));

                        // Pre-fill the conversion field with the full amount by default
                        if (etConversionAmount != null) {
                            etConversionAmount.setText(String.format(Locale.US, "%.2f", originalTx.getTotalAmount()));
                        }

                        // Show privilege block ONLY if there are FinMates involved
                        boolean hasFinMates = false;
                        if (originalTx.getSplits() != null) {
                            for (String mateId : originalTx.getSplits().keySet()) {
                                if (!"self".equals(mateId)) {
                                    hasFinMates = true;
                                    break;
                                }
                            }
                        }
                        cardPrivilegeCharges.setVisibility(hasFinMates ? View.VISIBLE : View.GONE);
                    }
                });
    }

    private void generateAmortizationGrid() {
        // Fetch the user-defined amount (fallback to total if empty)
        String amountStr = etConversionAmount.getText() != null ? etConversionAmount.getText().toString().trim() : "";
        double conversionAmount = amountStr.isEmpty() ? originalTx.getTotalAmount() : Double.parseDouble(amountStr);

        if (conversionAmount <= 0 || conversionAmount > originalTx.getTotalAmount()) {
            Toast.makeText(this, "Invalid conversion amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        String tenureStr = spinTenure.getText().toString();
        String rateStr = String.valueOf(etInterestRate.getText()).trim();

        if (tenureStr.isEmpty() || rateStr.isEmpty()) {
            Toast.makeText(this, "Please enter Tenure and Interest Rate", Toast.LENGTH_SHORT).show();
            return;
        }

        int months = Integer.parseInt(tenureStr);
        double annualRate = Double.parseDouble(rateStr);

        double r = (annualRate / 12) / 100.0;
        // Use conversionAmount for the calculation instead of the total amount
        double emi = (conversionAmount * r * Math.pow(1 + r, months)) / (Math.pow(1 + r, months) - 1);

        generatedSchedule.clear();
        layoutGridContainer.removeAllViews();

        double remainingPrincipal = conversionAmount;

        for (int i = 1; i <= months; i++) {
            double interest = remainingPrincipal * r;
            double principal = emi - interest;

            if (i == months) principal = remainingPrincipal;

            double gst = interest * 0.18;

            Transaction.EmiMonth monthData = new Transaction.EmiMonth(i, principal, interest, gst, false);
            generatedSchedule.add(monthData);

            View row = LayoutInflater.from(this).inflate(R.layout.item_emi_month_row, layoutGridContainer, false);
            TextView tvNum = row.findViewById(R.id.tvMonthNumber);
            EditText etPrincipalVal = row.findViewById(R.id.etPrincipal);
            EditText etInterestVal = row.findViewById(R.id.etInterest);
            EditText etGstVal = row.findViewById(R.id.etGst);
            TextView tvTotal = row.findViewById(R.id.tvTotalBankDue);

            tvNum.setText(String.valueOf(i));
            etPrincipalVal.setText(String.format(Locale.getDefault(), "%.2f", principal));
            etInterestVal.setText(String.format(Locale.getDefault(), "%.2f", interest));
            etGstVal.setText(String.format(Locale.getDefault(), "%.2f", gst));

            double total = principal + interest + gst;
            tvTotal.setText(currencyFormatter.format(total));

            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    try {
                        double pVal = Double.parseDouble(etPrincipalVal.getText().toString());
                        double iVal = Double.parseDouble(etInterestVal.getText().toString());
                        double gVal = Double.parseDouble(etGstVal.getText().toString());
                        monthData.setBankPrincipal(pVal);
                        monthData.setBankInterest(iVal);
                        monthData.setBankGst(gVal);
                        tvTotal.setText(currencyFormatter.format(pVal + iVal + gVal));
                    } catch (NumberFormatException ignored) {}
                }
            };

            etPrincipalVal.addTextChangedListener(watcher);
            etInterestVal.addTextChangedListener(watcher);
            etGstVal.addTextChangedListener(watcher);

            layoutGridContainer.addView(row);
            remainingPrincipal -= principal;
        }

        cardAmortizationGrid.setVisibility(View.VISIBLE);
        btnConvertEmi.setEnabled(true);
        btnConvertEmi.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1abcab")));

        if (mainScrollView != null) {
            mainScrollView.post(() -> mainScrollView.fullScroll(View.FOCUS_DOWN));
        }
    }

    // Pass the calculated variables so Reversal perfectly matches the target
    private Transaction createReversalTransaction(long currentTimestamp, double conversionAmount, double conversionRatio) {
        String reversalTxId = generateRandomId();
        Transaction reversalTx = new Transaction(
                reversalTxId,
                "CARD_PAYMENT",
                originalTx.getCardId(),
                "EMI Reversal: " + originalTx.getTitle(),
                currentTimestamp,
                conversionAmount, // REVERSE ONLY WHAT IS CONVERTED
                false
        );

        // CHECK & DEDUCT CASHBACK PROPORTIONALLY
        double originalCashback = originalTx.getCashbackEarned();
        double cashbackToDeduct = originalCashback * conversionRatio;
        reversalTx.setCashbackEarned(cashbackToDeduct > 0.0 ? -cashbackToDeduct : 0.0);

        reversalTx.setSplits(new HashMap<>()); // Keeps ledger perfectly clean

        return reversalTx;
    }

    private void executeReversalAndConvert() {
        if (originalTx == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;
        String userId = user.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        if (generatedSchedule.isEmpty()) {
            Toast.makeText(this, "Please generate the amortization grid first.", Toast.LENGTH_SHORT).show();
            return;
        }

        // --- PARTIAL CONVERSION MATH ---
        String amountStr = etConversionAmount.getText() != null ? etConversionAmount.getText().toString().trim() : "";
        double conversionAmount = amountStr.isEmpty() ? originalTx.getTotalAmount() : Double.parseDouble(amountStr);

        if (conversionAmount <= 0 || conversionAmount > originalTx.getTotalAmount()) {
            Toast.makeText(this, "Invalid conversion amount.", Toast.LENGTH_SHORT).show();
            return;
        }

        double conversionRatio = originalTx.getTotalAmount() > 0 ? (conversionAmount / originalTx.getTotalAmount()) : 1.0;
        boolean isPartialConversion = conversionAmount < originalTx.getTotalAmount();

        // 1. Bank Fees
        String pfStr = etProcessingFee.getText() != null ? etProcessingFee.getText().toString().trim() : "0";
        String gstStr = etGstOnPf.getText() != null ? etGstOnPf.getText().toString().trim() : "0";
        double bankPf = pfStr.isEmpty() ? 0.0 : Double.parseDouble(pfStr);
        double gstPercentage = gstStr.isEmpty() ? 0.0 : Double.parseDouble(gstStr);
        double bankGst = bankPf * (gstPercentage / 100.0);

        // 2. Proportionally separate the original splits!
        Map<String, Double> convertedPrincipalSplits = new HashMap<>();
        Map<String, Transaction.TransactionSplit> remainingOriginalSplits = new HashMap<>();
        double totalFinMateConverted = 0.0;

        if (originalTx.getSplits() != null) {
            for (Map.Entry<String, Transaction.TransactionSplit> entry : originalTx.getSplits().entrySet()) {
                String mateId = entry.getKey();
                Transaction.TransactionSplit originalSplit = entry.getValue();

                double ogAmount = originalSplit.getCombinedStealthAmount();
                double convertedAmt = ogAmount * conversionRatio;
                double remainingAmt = ogAmount - convertedAmt; // The leftover unconverted debt

                convertedPrincipalSplits.put(mateId, convertedAmt);
                if (!"self".equals(mateId)) {
                    totalFinMateConverted += convertedAmt;
                }

                // If partially converted, keep the leftover split inside the original transaction!
                if (remainingAmt > 0.01) {
                    remainingOriginalSplits.put(mateId, new Transaction.TransactionSplit(remainingAmt, originalSplit.getCashAmount(), originalSplit.getPaidAmount()));
                }
            }
        }

        // 3. Distribute Privilege Charge proportionally among the CONVERTED FinMate amounts
        String privStr = etTotalPrivilegeCharge.getText() != null ? etTotalPrivilegeCharge.getText().toString().trim() : "0";
        double totalPrivilege = privStr.isEmpty() ? 0.0 : Double.parseDouble(privStr);

        Map<String, Double> privilegeMap = new HashMap<>();
        if (totalPrivilege > 0 && totalFinMateConverted > 0) {
            for (Map.Entry<String, Double> entry : convertedPrincipalSplits.entrySet()) {
                String mateId = entry.getKey();
                if (!"self".equals(mateId)) {
                    double ratio = entry.getValue() / totalFinMateConverted;
                    privilegeMap.put(mateId, totalPrivilege * ratio);
                }
            }
        }

        Transaction.EmiData emiData = new Transaction.EmiData(
                bankPf, bankGst, conversionAmount, // Set EMI Master Total to the targeted converted amount
                convertedPrincipalSplits, privilegeMap, generatedSchedule
        );

        WriteBatch batch = db.batch();
        long currentTimestamp = System.currentTimeMillis();

        // --- ACTION 1: Keep Original Transaction & Manage Unconverted Splits ---
        DocumentReference originalTxRef = db.collection("Users").document(userId).collection("Transactions").document(transactionId);
        if (isPartialConversion) {
            // Keep the badge but leave the remaining unconverted splits so the FinMate keeps the standard unbilled debt
            batch.update(originalTxRef, "emi", true, "splits", remainingOriginalSplits);
        } else {
            // Fully converted, erase splits completely
            batch.update(originalTxRef, "emi", true, "splits", new HashMap<>());
        }

        // --- ACTION 2: Create Reversal/Refund Transaction ONLY for Converted Amount ---
        Transaction reversalTx = createReversalTransaction(currentTimestamp, conversionAmount, conversionRatio);
        batch.set(db.collection("Users").document(userId).collection("Transactions").document(reversalTx.getTransactionId()), reversalTx);

        // --- ACTION 3: Create the hidden EMI Master Transaction ---
        String emiMasterTxId = generateRandomId();
        Transaction emiMasterTx = new Transaction(
                emiMasterTxId, "EMI_MASTER", originalTx.getCardId(),
                originalTx.getTitle() + " (EMI)", currentTimestamp,
                conversionAmount, true
        );
        emiMasterTx.setEmiData(emiData);
        emiMasterTx.setSplits(new HashMap<>());

        batch.set(db.collection("Users").document(userId).collection("Transactions").document(emiMasterTxId), emiMasterTx);

        // --- ACTION 4: Refund the FinMates' CONVERTED Upfront Debt ---
        for (Map.Entry<String, Double> entry : convertedPrincipalSplits.entrySet()) {
            String mateId = entry.getKey();
            if ("self".equals(mateId)) continue;

            double refundAmount = entry.getValue(); // Only refund the amount that moved to EMI
            if (refundAmount > 0) {
                DocumentReference fmRef = db.collection("Users").document(userId).collection("FinMates").document(mateId);
                batch.update(fmRef,
                        "receivableCardAmount", FieldValue.increment(-refundAmount),
                        "totalReceivable", FieldValue.increment(-refundAmount)
                );
            }
        }

        btnConvertEmi.setEnabled(false);
        btnConvertEmi.setText("Converting...");

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Successfully Converted!", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to convert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnConvertEmi.setEnabled(true);
            btnConvertEmi.setText("Execute Reversal & Convert");
        });
    }

    private String generateRandomId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(6);
        SecureRandom rnd = new SecureRandom();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}