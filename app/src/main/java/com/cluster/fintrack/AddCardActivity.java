package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public class AddCardActivity extends AppCompatActivity {

    private TextInputEditText etCardName, etLast4Digits, etTotalLimit, etBillingDay, etCashbackRates;
    private MaterialAutoCompleteTextView spinBankName, spinCardType;
    private TextInputLayout tilBankName, tilCardType, tilCashbackRates;
    private MaterialCardView cardColorPreview;
    private MaterialSwitch switchCashback;
    private MaterialButton btnSaveCard;
    private TextView tvActivityTitle;

    private final int[] currentColor = {Color.parseColor("#082561")};
    private final int defaultColor = Color.parseColor("#082561");

    private String editCardId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_card);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAddCard), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initializeViews();
        setupDropdowns();
        checkForEditMode();
        setupClickListeners();
    }

    private void initializeViews() {
        tvActivityTitle = findViewById(R.id.tvActivityTitle);
        etCardName = findViewById(R.id.etCardName);
        etLast4Digits = findViewById(R.id.etLast4Digits);
        etTotalLimit = findViewById(R.id.etTotalLimit);
        etBillingDay = findViewById(R.id.etBillingDay);

        etCashbackRates = findViewById(R.id.etCashbackRates);
        tilCashbackRates = findViewById(R.id.tilCashbackRates);

        spinBankName = findViewById(R.id.spinBankName);
        tilBankName = findViewById(R.id.tilBankName);

        spinCardType = findViewById(R.id.spinCardType);
        tilCardType = findViewById(R.id.tilCardType);

        cardColorPreview = findViewById(R.id.cardColorPreview);
        switchCashback = findViewById(R.id.switchCashback);
        btnSaveCard = findViewById(R.id.btnSaveCard);

        tilBankName.setEndIconMode(TextInputLayout.END_ICON_NONE);
        tilCardType.setEndIconMode(TextInputLayout.END_ICON_NONE);

        switchCashback.setOnCheckedChangeListener((buttonView, isChecked) -> {
            tilCashbackRates.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            // Only autofill if the text box is empty to prevent overwriting user edits
            if (isChecked && TextUtils.isEmpty(etCashbackRates.getText())) {
                etCashbackRates.setText("1, 2, 5");
            }
        });
    }

    private void checkForEditMode() {
        if (getIntent() != null && getIntent().hasExtra("CARD_ID")) {
            editCardId = getIntent().getStringExtra("CARD_ID");
            tvActivityTitle.setText("Edit Credit Card");
            btnSaveCard.setText("Update Card");

            tilBankName.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
            tilBankName.setEndIconTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#667085")));

            tilCardType.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
            tilCardType.setEndIconTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#667085")));

            etCardName.setText(getIntent().getStringExtra("CARD_NAME"));
            spinBankName.setText(getIntent().getStringExtra("BANK_NAME"), false);
            spinCardType.setText(getIntent().getStringExtra("CARD_TYPE"), false);
            etLast4Digits.setText(getIntent().getStringExtra("LAST4"));

            double limit = getIntent().getDoubleExtra("TOTAL_LIMIT", 0.0);
            etTotalLimit.setText(limit > 0 ? String.valueOf((int) limit) : "");

            int bDay = getIntent().getIntExtra("BILLING_DAY", 1);
            etBillingDay.setText(String.valueOf(bDay));

            // Load the numbers into the text box before flipping the switch
            double[] ratesArray = getIntent().getDoubleArrayExtra("CASHBACK_RATES");
            if (ratesArray != null && ratesArray.length > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < ratesArray.length; i++) {
                    if (ratesArray[i] == (long) ratesArray[i]) {
                        sb.append(String.format(Locale.getDefault(), "%d", (long) ratesArray[i]));
                    } else {
                        sb.append(ratesArray[i]);
                    }
                    if (i < ratesArray.length - 1) sb.append(", ");
                }
                etCashbackRates.setText(sb.toString());
            }

            boolean isCb = getIntent().getBooleanExtra("IS_CASHBACK", false);
            switchCashback.setChecked(isCb);
            tilCashbackRates.setVisibility(isCb ? View.VISIBLE : View.GONE);

            try {
                String themeColor = getIntent().getStringExtra("THEME_COLOR");
                if (themeColor != null && !themeColor.isEmpty()) {
                    currentColor[0] = Color.parseColor(themeColor);
                    cardColorPreview.setCardBackgroundColor(currentColor[0]);
                }
            } catch (Exception ignored) {}
        } else {
            etCardName.requestFocus();
            WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), etCardName);
            controller.show(WindowInsetsCompat.Type.ime());
        }
    }

    private void setupDropdowns() {
        GradientDrawable roundedDropdownBackground = new GradientDrawable();
        roundedDropdownBackground.setColor(Color.WHITE);
        float cornerRadius = 12 * getResources().getDisplayMetrics().density;
        roundedDropdownBackground.setCornerRadius(cornerRadius);

        spinBankName.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        spinBankName.setAdapter(getBankArrayAdapter());
        spinBankName.setDropDownBackgroundDrawable(roundedDropdownBackground);

        spinBankName.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (spinBankName.hasFocus() && s.length() > 0) spinBankName.showDropDown();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        spinCardType.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        String[] cardTypes = new String[]{"RuPay", "Visa", "MasterCard", "Amex", "Discover", "Other"};
        ArrayAdapter<String> cardTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, cardTypes);
        spinCardType.setAdapter(cardTypeAdapter);
        spinCardType.setDropDownBackgroundDrawable(roundedDropdownBackground);

        spinCardType.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (spinCardType.hasFocus() && s.length() > 0) spinCardType.showDropDown();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupClickListeners() {
        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnClearAllFields).setOnClickListener(v -> clearAllFields());

        findViewById(R.id.btnResetColor).setOnClickListener(v -> {
            currentColor[0] = defaultColor;
            cardColorPreview.setCardBackgroundColor(defaultColor);
        });

        cardColorPreview.setOnClickListener(v -> {
            yuku.ambilwarna.AmbilWarnaDialog colorPickerDialog = new yuku.ambilwarna.AmbilWarnaDialog(this, currentColor[0], new yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override public void onCancel(yuku.ambilwarna.AmbilWarnaDialog dialog) {}
                @Override public void onOk(yuku.ambilwarna.AmbilWarnaDialog dialog, int color) {
                    currentColor[0] = color;
                    cardColorPreview.setCardBackgroundColor(color);
                }
            });
            colorPickerDialog.show();
        });

        btnSaveCard.setOnClickListener(v -> validateAndSaveCard());
    }

    private void clearAllFields() {
        etCardName.setText("");
        spinBankName.setText("", false);
        spinCardType.setText("", false);
        etLast4Digits.setText("");
        etTotalLimit.setText("");
        etBillingDay.setText("");
        etCashbackRates.setText("");
        switchCashback.setChecked(false);
        tilCashbackRates.setVisibility(View.GONE);

        currentColor[0] = defaultColor;
        cardColorPreview.setCardBackgroundColor(defaultColor);

        etCardName.setError(null);
        spinBankName.setError(null);
        spinCardType.setError(null);
        etLast4Digits.setError(null);
        etTotalLimit.setError(null);
        etBillingDay.setError(null);
        etCashbackRates.setError(null);

        etCardName.requestFocus();
    }

    private void validateAndSaveCard() {
        String cardName = String.valueOf(etCardName.getText()).trim();
        String bankName = String.valueOf(spinBankName.getText()).trim();
        String cardType = String.valueOf(spinCardType.getText()).trim();
        String last4 = String.valueOf(etLast4Digits.getText()).trim();
        String limitStr = String.valueOf(etTotalLimit.getText()).trim();
        String billingDayStr = String.valueOf(etBillingDay.getText()).trim();
        boolean isCashback = switchCashback.isChecked();
        String ratesStr = String.valueOf(etCashbackRates.getText()).trim();

        if (TextUtils.isEmpty(cardName)) { etCardName.setError("Required"); return; }
        if (TextUtils.isEmpty(bankName)) { spinBankName.setError("Required"); return; }

        String[] validBanks = new String[]{
                "AU Small Finance Bank", "American Express", "Axis Bank", "Bandhan Bank",
                "Bank of Baroda", "Bank of India", "Bank of Maharashtra", "Barclays Bank",
                "Baroda Gujarat Gramin Bank", "Baroda Rajasthan Kshetriya Gramin Bank",
                "Baroda U.P. Bank", "CSB Bank", "Canara Bank", "Capital Small Finance Bank",
                "Central Bank of India", "City Union Bank", "Cosmos Co-operative Bank",
                "DBS Bank", "DCB Bank", "Deutsche Bank", "Dhanlaxmi Bank",
                "Equitas Small Finance Bank", "ESAF Small Finance Bank", "Federal Bank",
                "First Abu Dhabi Bank", "HDFC Bank", "HSBC Bank", "ICICI Bank Limited",
                "IDFC FIRST Bank", "Indian Bank", "Indian Overseas Bank", "IndusInd Bank",
                "Jammu & Kashmir Bank", "Jana Small Finance Bank", "Karnataka Bank",
                "Karur Vysya Bank", "Kerala Gramin Bank", "Kotak Mahindra Bank",
                "Nainital Bank", "Punjab & Sind Bank", "Punjab National Bank", "RBL Bank",
                "Saraswat Co-operative Bank", "SBM Bank India", "South Indian Bank",
                "Standard Chartered Bank", "State Bank of India", "Suryoday Small Finance Bank",
                "SVC Co-operative Bank", "Tamilnad Mercantile Bank", "UCO Bank",
                "Ujjivan Small Finance Bank", "Union Bank of India", "Utkarsh Small Finance Bank",
                "YES Bank"
        };

        boolean isValidBank = false;
        for (String bank : validBanks) {
            if (bank.equalsIgnoreCase(bankName)) {
                isValidBank = true;
                break;
            }
        }

        if (!isValidBank) {
            spinBankName.setError("Please select a valid bank from the list");
            spinBankName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(cardType)) { spinCardType.setError("Required"); return; }

        String[] validCardTypes = new String[]{"RuPay", "Visa", "MasterCard", "Amex", "Discover", "Other"};
        boolean isValidCardType = false;
        for (String type : validCardTypes) {
            if (type.equalsIgnoreCase(cardType)) {
                isValidCardType = true;
                break;
            }
        }

        if (!isValidCardType) {
            spinCardType.setError("Please select a valid card type from the list");
            spinCardType.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(last4) || last4.length() != 4) { etLast4Digits.setError("Enter 4 digits"); return; }
        if (TextUtils.isEmpty(limitStr)) { etTotalLimit.setError("Required"); return; }
        if (TextUtils.isEmpty(billingDayStr)) { etBillingDay.setError("Required"); return; }

        double totalLimit = Double.parseDouble(limitStr);
        int billingDay = Integer.parseInt(billingDayStr);
        if (billingDay < 1 || billingDay > 31) {
            etBillingDay.setError("Enter 1-31");
            return;
        }

        // --- STRICT DUPLICATE CHECK & SORTING ENGINE ---
        List<Double> ratesList = new ArrayList<>();
        if (isCashback) {
            if (TextUtils.isEmpty(ratesStr)) {
                etCashbackRates.setError("Enter at least one percentage");
                etCashbackRates.requestFocus();
                return;
            }

            String[] parts = ratesStr.split(",");
            boolean hasDuplicate = false;

            for (String part : parts) {
                try {
                    double val = Double.parseDouble(part.replace("%", "").trim());
                    if (val > 0) {
                        if (ratesList.contains(val)) {
                            hasDuplicate = true;
                            break; // Stop parsing, duplicate found!
                        } else {
                            ratesList.add(val);
                        }
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (hasDuplicate) {
                etCashbackRates.setError("Duplicate percentages are not allowed");
                etCashbackRates.requestFocus();
                return;
            }

            if (ratesList.isEmpty()) {
                etCashbackRates.setError("Enter valid numbers (e.g., 1, 2, 5)");
                etCashbackRates.requestFocus();
                return;
            }

            // Cleanly sort the array from lowest to highest percentage!
            Collections.sort(ratesList);
        }

        String themeColorHex = String.format("#%06X", (0xFFFFFF & currentColor[0]));

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String cardIdToSave = editCardId != null ? editCardId : db.collection("Users").document(userId).collection("Cards").document().getId();
        long timestamp = System.currentTimeMillis();

        Card savedCard = new Card(cardIdToSave, bankName, cardName, cardType, last4, totalLimit, billingDay, themeColorHex, timestamp, isCashback, ratesList);

        btnSaveCard.setEnabled(false);
        btnSaveCard.setText(editCardId != null ? "Updating..." : "Saving...");

        db.collection("Users").document(userId).collection("Cards").document(cardIdToSave)
                .set(savedCard)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, editCardId != null ? "Card Updated!" : "Card Saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                        Toast.makeText(this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
                        btnSaveCard.setEnabled(true);
                        btnSaveCard.setText(editCardId != null ? "Update Card" : "Save Card");
                    }
                });
    }

    private ArrayAdapter<String> getBankArrayAdapter() {
        List<String> bankList = Arrays.asList(
                "AU Small Finance Bank", "American Express", "Axis Bank", "Bandhan Bank",
                "Bank of Baroda", "Bank of India", "Bank of Maharashtra", "Barclays Bank",
                "Baroda Gujarat Gramin Bank", "Baroda Rajasthan Kshetriya Gramin Bank",
                "Baroda U.P. Bank", "CSB Bank", "Canara Bank", "Capital Small Finance Bank",
                "Central Bank of India", "City Union Bank", "Cosmos Co-operative Bank",
                "DBS Bank", "DCB Bank", "Deutsche Bank", "Dhanlaxmi Bank",
                "Equitas Small Finance Bank", "ESAF Small Finance Bank", "Federal Bank",
                "First Abu Dhabi Bank", "HDFC Bank", "HSBC Bank", "ICICI Bank Limited",
                "IDFC FIRST Bank", "Indian Bank", "Indian Overseas Bank", "IndusInd Bank",
                "Jammu & Kashmir Bank", "Jana Small Finance Bank", "Karnataka Bank",
                "Karur Vysya Bank", "Kerala Gramin Bank", "Kotak Mahindra Bank",
                "Nainital Bank", "Punjab & Sind Bank", "Punjab National Bank", "RBL Bank",
                "Saraswat Co-operative Bank", "SBM Bank India", "South Indian Bank",
                "Standard Chartered Bank", "State Bank of India", "Suryoday Small Finance Bank",
                "SVC Co-operative Bank", "Tamilnad Mercantile Bank", "UCO Bank",
                "Ujjivan Small Finance Bank", "Union Bank of India", "Utkarsh Small Finance Bank",
                "YES Bank"
        );

        return new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, new ArrayList<>(bankList)) {
            @NonNull
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        if (constraint == null || constraint.length() == 0) {
                            results.values = bankList;
                            results.count = bankList.size();
                        } else {
                            List<String> filteredList = new ArrayList<>();
                            String filterPattern = constraint.toString().toLowerCase().trim();
                            for (String bank : bankList) {
                                if (bank.toLowerCase().contains(filterPattern)) {
                                    filteredList.add(bank);
                                }
                            }
                            results.values = filteredList;
                            results.count = filteredList.size();
                        }
                        return results;
                    }

                    @Override
                    @SuppressWarnings("unchecked")
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results.values != null) addAll((List<String>) results.values);
                        notifyDataSetChanged();
                    }
                };
            }
        };
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof android.widget.EditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    boolean clickedAnotherEditText = false;
                    int[] editIds = {
                            R.id.etCardName, R.id.spinBankName, R.id.spinCardType,
                            R.id.etLast4Digits, R.id.etTotalLimit, R.id.etBillingDay, R.id.etCashbackRates
                    };
                    Rect rect = new Rect();
                    for (int id : editIds) {
                        View editView = findViewById(id);
                        if (editView != null && editView.isShown()) {
                            editView.getGlobalVisibleRect(rect);
                            if (rect.contains((int) event.getRawX(), (int) event.getRawY())) {
                                clickedAnotherEditText = true;
                                break;
                            }
                        }
                    }
                    if (!clickedAnotherEditText) {
                        v.clearFocus();
                        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), v);
                        controller.hide(WindowInsetsCompat.Type.ime());
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }
}