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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

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
    private TextInputEditText etInterestRate, etProcessingFee, etGstOnPf;

    private MaterialCardView cardPrivilegeCharges, cardAmortizationGrid;
    private LinearLayout layoutPrivilegeContainer, layoutGridContainer;
    private MaterialButton btnConvertEmi;

    private final Map<String, TextInputEditText> privilegeInputs = new HashMap<>();
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

        spinTenure = findViewById(R.id.spinTenure);
        etInterestRate = findViewById(R.id.etInterestRate);
        etProcessingFee = findViewById(R.id.etProcessingFee);
        etGstOnPf = findViewById(R.id.etGstOnPf);

        cardPrivilegeCharges = findViewById(R.id.cardPrivilegeCharges);
        layoutPrivilegeContainer = findViewById(R.id.layoutPrivilegeContainer);

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
                        buildPrivilegeChargeInputs();
                    }
                });
    }

    private void buildPrivilegeChargeInputs() {
        if (originalTx.getSplits() == null) return;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance()
                .collection("Users").document(user.getUid())
                .collection("FinMates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Map<String, String> namesMap = new HashMap<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FinMate fm = doc.toObject(FinMate.class);
                        if (fm != null) namesMap.put(fm.getFinMateId(), fm.getName());
                    }

                    layoutPrivilegeContainer.removeAllViews();
                    privilegeInputs.clear();
                    boolean hasFinMates = false;

                    for (String mateId : originalTx.getSplits().keySet()) {
                        if (mateId.equals("self")) continue;

                        hasFinMates = true;
                        View row = LayoutInflater.from(this).inflate(R.layout.item_privilege_charge, layoutPrivilegeContainer, false);
                        TextView tvName = row.findViewById(R.id.tvFinMateName);
                        TextInputEditText etCharge = row.findViewById(R.id.etPrivilegeCharge);

                        tvName.setText(namesMap.getOrDefault(mateId, "Unknown"));
                        privilegeInputs.put(mateId, etCharge);
                        layoutPrivilegeContainer.addView(row);
                    }

                    if (hasFinMates) {
                        cardPrivilegeCharges.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void generateAmortizationGrid() {
        String tenureStr = spinTenure.getText().toString();
        String rateStr = String.valueOf(etInterestRate.getText()).trim();

        if (tenureStr.isEmpty() || rateStr.isEmpty()) {
            Toast.makeText(this, "Please enter Tenure and Interest Rate", Toast.LENGTH_SHORT).show();
            return;
        }

        int months = Integer.parseInt(tenureStr);
        double annualRate = Double.parseDouble(rateStr);
        double p = originalTx.getTotalAmount();

        double r = (annualRate / 12) / 100.0;
        double emi = (p * r * Math.pow(1 + r, months)) / (Math.pow(1 + r, months) - 1);

        generatedSchedule.clear();
        layoutGridContainer.removeAllViews();

        double remainingPrincipal = p;

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

        // 1. Read Bank Processing Fees & GST (Deducted by Bank)
        String pfStr = etProcessingFee.getText() != null ? etProcessingFee.getText().toString().trim() : "0";
        String gstStr = etGstOnPf.getText() != null ? etGstOnPf.getText().toString().trim() : "0";
        double bankPf = pfStr.isEmpty() ? 0.0 : Double.parseDouble(pfStr);
        double bankGst = gstStr.isEmpty() ? 0.0 : Double.parseDouble(gstStr);

        // 2. Read Privilege Charges (User Cash Profit per FinMate)
        Map<String, Double> totalPrivilegeCharges = new HashMap<>();
        for (Map.Entry<String, TextInputEditText> entry : privilegeInputs.entrySet()) {
            String chargeStr = entry.getValue().getText() != null ? entry.getValue().getText().toString().trim() : "0";
            double charge = chargeStr.isEmpty() ? 0.0 : Double.parseDouble(chargeStr);
            if (charge > 0) {
                totalPrivilegeCharges.put(entry.getKey(), charge);
            }
        }

        // 3. Extract Initial Splits
        Map<String, Double> originalPrincipalSplits = new HashMap<>();
        if (originalTx.getSplits() != null) {
            for (Map.Entry<String, Transaction.TransactionSplit> entry : originalTx.getSplits().entrySet()) {
                originalPrincipalSplits.put(entry.getKey(), entry.getValue().getCombinedStealthAmount());
            }
        }

        // 4. Construct EMI Data
        Transaction.EmiData emiData = new Transaction.EmiData(
                bankPf, bankGst, originalTx.getTotalAmount(),
                originalPrincipalSplits, totalPrivilegeCharges, generatedSchedule
        );

        WriteBatch batch = db.batch();

        // 5. Morph Original Transaction into EMI_MASTER
        DocumentReference txRef = db.collection("Users").document(userId).collection("Transactions").document(transactionId);
        batch.update(txRef,
                "transactionType", "EMI_MASTER",
                "emi", true,
                "emiData", emiData
        );

        // 6. Refund the FinMates' Upfront Debt
        for (Map.Entry<String, Double> entry : originalPrincipalSplits.entrySet()) {
            String mateId = entry.getKey();
            if ("self".equals(mateId)) continue;

            double refundAmount = entry.getValue();
            DocumentReference fmRef = db.collection("Users").document(userId).collection("FinMates").document(mateId);

            batch.update(fmRef,
                    "receivableCardAmount", FieldValue.increment(-refundAmount),
                    "totalReceivable", FieldValue.increment(-refundAmount)
            );
        }

        btnConvertEmi.setEnabled(false);
        btnConvertEmi.setText("Converting...");

        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Successfully Converted to EMI!", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to convert: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            btnConvertEmi.setEnabled(true);
            btnConvertEmi.setText("Execute Reversal & Convert");
        });
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