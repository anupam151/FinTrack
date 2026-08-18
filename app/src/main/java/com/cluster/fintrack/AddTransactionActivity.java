package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
@SuppressLint("SetTextI18n")
public class AddTransactionActivity extends AppCompatActivity {

    private ImageView ivBack;
    private TextView tvTransactionTitle;
    private MaterialButton btnTypeSpend, btnTypeReceive;
    private TextInputEditText etTransactionTitle, etTotalAmount, etTransactionDate;

    private NestedScrollView mainScrollView;

    // TOGGLES
    private RadioGroup rgSpendOptions, rgReceiveOptions, rgSplitDecision, rgCashbackPercentage;

    // SPEND UI ELEMENTS
    private MaterialCardView cardPaymentSource, cardTargetCreditCard, cardSplitEngine, cardPayBackCredit, cardPayBackAdvance, cardSinglePerson, cardCashback;
    private MaterialAutoCompleteTextView spinPaymentSource, spinTargetCreditCard, spinPayBackPerson, spinSinglePerson;
    private TextView btnAddSplitFinMate, tvNoCreditTx, tvPayBackAdvanceTitle, tvPayBackAdvanceAmount;
    private ImageView ivPayBackAdvanceIcon;
    private LinearLayout layoutSplitEngineContainer, layoutCreditTransactionsContainer;

    // RECEIVE UI ELEMENTS
    private MaterialCardView cardReceivePerson, cardReceiveUnpaidList, cardReceiveAdvance;
    private MaterialAutoCompleteTextView spinReceivePerson;
    private TextView tvNoUnpaidTx, tvAdvanceTitle, tvAdvanceAmount, tvReceivePersonTitle;
    private ImageView ivAdvanceIcon;
    private LinearLayout layoutUnpaidTransactionsContainer;

    private MaterialButton btnSaveTransaction;

    private final List<FinMate> allFinMatesList = new ArrayList<>();
    private final Map<String, String> sourceNameToIdMap = new HashMap<>();
    private final Map<String, String> finMateNameToIdMap = new HashMap<>();

    // CASHBACK TRACKERS
    private final Map<String, Boolean> cardCashbackMap = new HashMap<>();
    private final Map<String, List<Double>> cardCashbackRatesMap = new HashMap<>();

    private final Set<String> existingTxIds = new HashSet<>();

    private final Map<String, View> activeSplitRows = new HashMap<>();
    private final Map<String, TextInputEditText> activeSplitInputs = new HashMap<>();
    private final Map<String, MaterialCheckBox> creditCheckBoxes = new HashMap<>();

    private final LinkedHashMap<String, Transaction> selectedUnpaidTransactions = new LinkedHashMap<>();
    private String currentSelectedReceiveFinMateId = null;

    private final LinkedHashMap<String, Transaction> selectedCreditTransactions = new LinkedHashMap<>();
    private String currentSelectedPayBackFinMateId = null;

    private boolean isSpendMode = true;
    private long selectedTransactionTimestamp = 0;
    private float touchDownX, touchDownY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_transaction);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAddTransaction), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        initializeViews();
        setupListeners();
        fetchFirestoreData();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            touchDownX = event.getRawX();
            touchDownY = event.getRawY();
        }

        boolean result = super.dispatchTouchEvent(event);

        if (event.getAction() == MotionEvent.ACTION_UP) {
            float touchUpX = event.getRawX();
            float touchUpY = event.getRawY();

            float deltaX = Math.abs(touchUpX - touchDownX);
            float deltaY = Math.abs(touchUpY - touchDownY);

            if (deltaX < 20 && deltaY < 20) {
                View focusedView = getCurrentFocus();
                if (focusedView instanceof EditText) {
                    Rect outRect = new Rect();
                    focusedView.getGlobalVisibleRect(outRect);

                    if (!outRect.contains((int) touchUpX, (int) touchUpY)) {
                        focusedView.clearFocus();
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) {
                            imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                        }
                    }
                }
            }
        }
        return result;
    }

    private void initializeViews() {
        ivBack = findViewById(R.id.ivBack);
        tvTransactionTitle = findViewById(R.id.tvTransactionTitle);
        btnTypeSpend = findViewById(R.id.btnTypeSpend);
        btnTypeReceive = findViewById(R.id.btnTypeReceive);

        mainScrollView = findViewById(R.id.mainScrollView);

        etTransactionTitle = findViewById(R.id.etTransactionTitle);
        etTotalAmount = findViewById(R.id.etTotalAmount);
        etTransactionDate = findViewById(R.id.etTransactionDate);

        rgSpendOptions = findViewById(R.id.rgSpendOptions);
        rgReceiveOptions = findViewById(R.id.rgReceiveOptions);
        rgSplitDecision = findViewById(R.id.rgSplitDecision);

        cardPaymentSource = findViewById(R.id.cardPaymentSource);
        cardTargetCreditCard = findViewById(R.id.cardTargetCreditCard);
        cardSplitEngine = findViewById(R.id.cardSplitEngine);
        cardSinglePerson = findViewById(R.id.cardSinglePerson);
        cardPayBackCredit = findViewById(R.id.cardPayBackCredit);
        cardPayBackAdvance = findViewById(R.id.cardPayBackAdvance);
        cardCashback = findViewById(R.id.cardCashback);
        rgCashbackPercentage = findViewById(R.id.rgCashbackPercentage);

        spinPaymentSource = findViewById(R.id.spinPaymentSource);
        spinTargetCreditCard = findViewById(R.id.spinTargetCreditCard);
        spinPayBackPerson = findViewById(R.id.spinPayBackPerson);
        spinSinglePerson = findViewById(R.id.spinSinglePerson);
        btnAddSplitFinMate = findViewById(R.id.btnAddSplitFinMate);
        tvNoCreditTx = findViewById(R.id.tvNoCreditTx);
        tvPayBackAdvanceTitle = findViewById(R.id.tvPayBackAdvanceTitle);
        tvPayBackAdvanceAmount = findViewById(R.id.tvPayBackAdvanceAmount);
        ivPayBackAdvanceIcon = findViewById(R.id.ivPayBackAdvanceIcon);
        layoutSplitEngineContainer = findViewById(R.id.layoutSplitEngineContainer);
        layoutCreditTransactionsContainer = findViewById(R.id.layoutCreditTransactionsContainer);

        cardReceivePerson = findViewById(R.id.cardReceivePerson);
        cardReceiveUnpaidList = findViewById(R.id.cardReceiveUnpaidList);
        cardReceiveAdvance = findViewById(R.id.cardReceiveAdvance);
        spinReceivePerson = findViewById(R.id.spinReceivePerson);
        tvReceivePersonTitle = findViewById(R.id.tvReceivePersonTitle);
        tvNoUnpaidTx = findViewById(R.id.tvNoUnpaidTx);
        tvAdvanceTitle = findViewById(R.id.tvAdvanceTitle);
        tvAdvanceAmount = findViewById(R.id.tvAdvanceAmount);
        ivAdvanceIcon = findViewById(R.id.ivAdvanceIcon);
        layoutUnpaidTransactionsContainer = findViewById(R.id.layoutUnpaidTransactionsContainer);

        btnSaveTransaction = findViewById(R.id.btnSaveTransaction);

        if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.GONE);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        if (rgSplitDecision != null) {
            rgSplitDecision.setOnCheckedChangeListener((group, checkedId) -> {
                String selectedSource = spinPaymentSource.getText() != null ? spinPaymentSource.getText().toString() : "";
                String cardId = sourceNameToIdMap.get(selectedSource);

                if (checkedId == R.id.rbSplitNo) {
                    if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.VISIBLE);
                    if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.GONE);
                } else {
                    if ("CASH".equals(cardId)) {
                        Toast.makeText(this, "Cash transactions cannot be split among multiple people.", Toast.LENGTH_SHORT).show();
                        rgSplitDecision.check(R.id.rbSplitNo);
                        return;
                    }
                    if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.GONE);
                    if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.VISIBLE);
                }
            });
        }

        spinPaymentSource.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String cardId = sourceNameToIdMap.get(s.toString());
                if ("CASH".equals(cardId) && rgSplitDecision != null && rgSplitDecision.getCheckedRadioButtonId() == R.id.rbSplitYes) {
                    Toast.makeText(AddTransactionActivity.this, "Cash cannot be split. Switching to single person.", Toast.LENGTH_LONG).show();
                    rgSplitDecision.check(R.id.rbSplitNo);
                    clearSplitEngine();
                }
                updateCashbackVisibility();
            }
        });

        etTotalAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isSpendMode && rgSpendOptions.getCheckedRadioButtonId() == R.id.rbPayBackCredit) {
                    calculatePayBackAdvanceOrPartial();
                } else if (!isSpendMode && rgReceiveOptions.getCheckedRadioButtonId() == R.id.rbSettleDues) {
                    calculateSettleAdvanceOrPartial();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        etTransactionDate.setOnClickListener(v -> {
            View currentFocus = getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
                }
            }

            final Calendar calendar = Calendar.getInstance();
            if (selectedTransactionTimestamp != 0) {
                calendar.setTimeInMillis(selectedTransactionTimestamp);
            }

            int year = calendar.get(Calendar.YEAR);
            int month = calendar.get(Calendar.MONTH);
            int day = calendar.get(Calendar.DAY_OF_MONTH);

            DatePickerDialog datePickerDialog = new DatePickerDialog(
                    this,
                    (view, selectedYear, selectedMonth, selectedDay) -> {
                        calendar.set(selectedYear, selectedMonth, selectedDay);
                        selectedTransactionTimestamp = calendar.getTimeInMillis();
                        updateDateDisplay();
                        etTransactionDate.setError(null);
                    },
                    year, month, day
            );
            datePickerDialog.show();
        });

        btnTypeSpend.setOnClickListener(v -> {
            isSpendMode = true;
            tvTransactionTitle.setText(R.string.new_transaction_spend);

            setTabActive(btnTypeSpend, true);
            setTabActive(btnTypeReceive, false);

            rgSpendOptions.setVisibility(View.VISIBLE);
            rgReceiveOptions.setVisibility(View.GONE);

            cardReceivePerson.setVisibility(View.GONE);
            cardReceiveUnpaidList.setVisibility(View.GONE);
            cardReceiveAdvance.setVisibility(View.GONE);

            updateSpendSubViews();
            etTotalAmount.setText("");
        });

        btnTypeReceive.setOnClickListener(v -> {
            isSpendMode = false;
            tvTransactionTitle.setText("New Receipt");

            setTabActive(btnTypeReceive, true);
            setTabActive(btnTypeSpend, false);

            rgSpendOptions.setVisibility(View.GONE);
            rgReceiveOptions.setVisibility(View.VISIBLE);

            cardPaymentSource.setVisibility(View.GONE);
            if (cardTargetCreditCard != null) cardTargetCreditCard.setVisibility(View.GONE);
            if (rgSplitDecision != null) rgSplitDecision.setVisibility(View.GONE);
            if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.GONE);
            if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.GONE);
            cardPayBackCredit.setVisibility(View.GONE);
            cardPayBackAdvance.setVisibility(View.GONE);
            cardReceivePerson.setVisibility(View.VISIBLE);

            clearSplitEngine();
            updateReceiveSubViews();
            etTotalAmount.setText("");
        });

        rgSpendOptions.setOnCheckedChangeListener((group, checkedId) -> updateSpendSubViews());
        rgReceiveOptions.setOnCheckedChangeListener((group, checkedId) -> updateReceiveSubViews());

        spinReceivePerson.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = parent.getItemAtPosition(position).toString();
            String selectedFinMateId = finMateNameToIdMap.get(selectedName);

            if (selectedFinMateId != null) {
                currentSelectedReceiveFinMateId = selectedFinMateId;
                if (rgReceiveOptions.getCheckedRadioButtonId() == R.id.rbSettleDues) {
                    fetchUnpaidTransactions(selectedFinMateId, selectedName);
                }
            }
        });

        spinPayBackPerson.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = parent.getItemAtPosition(position).toString();
            String selectedFinMateId = finMateNameToIdMap.get(selectedName);

            if (selectedFinMateId != null) {
                currentSelectedPayBackFinMateId = selectedFinMateId;
                fetchUnpaidCredits(selectedFinMateId, selectedName);
            }
        });

        btnAddSplitFinMate.setOnClickListener(v -> showAddPersonDialog());
        btnSaveTransaction.setOnClickListener(v -> validateAndSaveTransaction());
    }

    private void clearSplitEngine() {
        layoutSplitEngineContainer.removeAllViews();
        activeSplitRows.clear();
        activeSplitInputs.clear();
        creditCheckBoxes.clear();
    }

    // THE FIX: Dynamically creates radio buttons based on the Card's configured percentages!
    private void updateCashbackVisibility() {
        if (isSpendMode && rgSpendOptions.getCheckedRadioButtonId() == R.id.rbNormalSpend) {
            String selectedSource = spinPaymentSource.getText() != null ? spinPaymentSource.getText().toString() : "";
            String cardId = sourceNameToIdMap.get(selectedSource);

            if (cardId != null && Boolean.TRUE.equals(cardCashbackMap.get(cardId))) {
                List<Double> rates = cardCashbackRatesMap.get(cardId);

                if (rates != null && !rates.isEmpty()) {
                    rgCashbackPercentage.removeAllViews();

                    for (int i = 0; i < rates.size(); i++) {
                        Double rate = rates.get(i);
                        RadioButton rb = new RadioButton(this);

                        // ID must be generated so RadioGroup logic works seamlessly
                        rb.setId(View.generateViewId());

                        String label = (rate == Math.floor(rate))
                                ? String.format(Locale.getDefault(), "%d%%", rate.longValue())
                                : String.format(Locale.getDefault(), "%.1f%%", rate);

                        rb.setText(label);
                        rb.setTag(rate); // Save percentage to the view tag for easy lookup!
                        rb.setTextColor(Color.parseColor("#082561"));
                        rb.setButtonTintList(ColorStateList.valueOf(Color.parseColor("#1abcab")));

                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        params.setMarginEnd((int) (12 * getResources().getDisplayMetrics().density));
                        rb.setLayoutParams(params);

                        rgCashbackPercentage.addView(rb);
                    }
                    cardCashback.setVisibility(View.VISIBLE);
                    return;
                }
            }
        }
        cardCashback.setVisibility(View.GONE);
        rgCashbackPercentage.removeAllViews();
    }

    private void updateSpendSubViews() {
        if (rgSpendOptions.getCheckedRadioButtonId() == R.id.rbNormalSpend) {
            btnSaveTransaction.setText("Save Spend");
            cardPaymentSource.setVisibility(View.VISIBLE);
            if (cardTargetCreditCard != null) cardTargetCreditCard.setVisibility(View.GONE);
            if (rgSplitDecision != null) rgSplitDecision.setVisibility(View.VISIBLE);

            if (rgSplitDecision != null && rgSplitDecision.getCheckedRadioButtonId() == R.id.rbSplitNo) {
                if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.VISIBLE);
                if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.GONE);
            } else {
                if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.GONE);
                if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.VISIBLE);
            }
            cardPayBackCredit.setVisibility(View.GONE);
            cardPayBackAdvance.setVisibility(View.GONE);

        } else if (rgSpendOptions.getCheckedRadioButtonId() == R.id.rbPayCardBill) {
            btnSaveTransaction.setText("Log Bill Payment");
            cardPaymentSource.setVisibility(View.GONE);
            if (cardTargetCreditCard != null) cardTargetCreditCard.setVisibility(View.VISIBLE);

            if (rgSplitDecision != null) rgSplitDecision.setVisibility(View.GONE);
            if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.GONE);
            if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.GONE);
            cardPayBackCredit.setVisibility(View.GONE);
            cardPayBackAdvance.setVisibility(View.GONE);

        } else {
            btnSaveTransaction.setText("Pay Back FinMate");
            cardPaymentSource.setVisibility(View.VISIBLE);
            if (cardTargetCreditCard != null) cardTargetCreditCard.setVisibility(View.GONE);

            if (rgSplitDecision != null) rgSplitDecision.setVisibility(View.GONE);
            if (cardSinglePerson != null) cardSinglePerson.setVisibility(View.GONE);
            if (cardSplitEngine != null) cardSplitEngine.setVisibility(View.GONE);
            cardPayBackCredit.setVisibility(View.VISIBLE);
            calculatePayBackAdvanceOrPartial();
        }
        updateCashbackVisibility();
    }

    private void updateReceiveSubViews() {
        if (rgReceiveOptions.getCheckedRadioButtonId() == R.id.rbSettleDues) {
            tvReceivePersonTitle.setText("Select Person Settling Up");
            btnSaveTransaction.setText("Save Settlement");
            cardReceiveUnpaidList.setVisibility(View.VISIBLE);

            if (currentSelectedReceiveFinMateId != null) {
                String name = spinReceivePerson.getText().toString();
                fetchUnpaidTransactions(currentSelectedReceiveFinMateId, name);
            }
        } else {
            tvReceivePersonTitle.setText("Select Person Giving Credit");
            btnSaveTransaction.setText("Take Credit");
            cardReceiveUnpaidList.setVisibility(View.GONE);
            cardReceiveAdvance.setVisibility(View.GONE);
            selectedUnpaidTransactions.clear();
        }
        updateCashbackVisibility();
    }

    private void setTabActive(MaterialButton button, boolean isActive) {
        if (isActive) {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1abcab")));
            button.setTextColor(Color.parseColor("#FFFFFF"));
            button.setStrokeWidth(0);
        } else {
            button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
            button.setTextColor(Color.parseColor("#082561"));
            button.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#082561")));
            button.setStrokeWidth(2);
        }
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        etTransactionDate.setText(sdf.format(new Date(selectedTransactionTimestamp)));
    }

    private void fetchFirestoreData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(userId).collection("Cards")
                .get()
                .addOnSuccessListener(snapshot -> {
                    sourceNameToIdMap.clear();
                    cardCashbackMap.clear();
                    cardCashbackRatesMap.clear();

                    List<String> sourceNames = new ArrayList<>();
                    List<String> strictlyCardsOnly = new ArrayList<>();

                    sourceNames.add("Cash (Personal Liquidity)");
                    sourceNameToIdMap.put("Cash (Personal Liquidity)", "CASH");
                    cardCashbackMap.put("CASH", false); // Cash doesn't earn cashback

                    for (DocumentSnapshot doc : snapshot) {
                        Card card = doc.toObject(Card.class);
                        if (card != null) {
                            String shortBankName = getBankInitials(card.getBankName());
                            String displayName = card.getCardName() + " - " + shortBankName + " (" + card.getLast4Digits() + ")";
                            sourceNames.add(displayName);
                            strictlyCardsOnly.add(displayName);
                            sourceNameToIdMap.put(displayName, card.getCardId());

                            // Log the dynamic cashback percentages
                            cardCashbackMap.put(card.getCardId(), card.isCashbackCard());
                            cardCashbackRatesMap.put(card.getCardId(), card.getCashbackRates());
                        }
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, sourceNames);
                    spinPaymentSource.setAdapter(adapter);
                    if (!sourceNames.isEmpty()) {
                        spinPaymentSource.setText(sourceNames.get(0), false);
                    }

                    if (spinTargetCreditCard != null) {
                        ArrayAdapter<String> targetCardAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, strictlyCardsOnly);
                        spinTargetCreditCard.setAdapter(targetCardAdapter);
                        if (!strictlyCardsOnly.isEmpty()) {
                            spinTargetCreditCard.setText(strictlyCardsOnly.get(0), false);
                        }
                    }
                });

        db.collection("Users").document(userId).collection("FinMates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    allFinMatesList.clear();
                    finMateNameToIdMap.clear();

                    List<String> finMateNames = new ArrayList<>();
                    List<String> singlePersonNames = new ArrayList<>();

                    singlePersonNames.add("Self (You)");
                    finMateNameToIdMap.put("Self (You)", "self");

                    for (DocumentSnapshot doc : snapshot) {
                        FinMate finMate = doc.toObject(FinMate.class);
                        if (finMate != null) {
                            allFinMatesList.add(finMate);
                            finMateNames.add(finMate.getName());
                            singlePersonNames.add(finMate.getName());
                            finMateNameToIdMap.put(finMate.getName(), finMate.getFinMateId());
                        }
                    }

                    ArrayAdapter<String> receiveAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, finMateNames);
                    spinReceivePerson.setAdapter(receiveAdapter);
                    spinPayBackPerson.setAdapter(receiveAdapter);

                    if (spinSinglePerson != null) {
                        ArrayAdapter<String> singlePersonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, singlePersonNames);
                        spinSinglePerson.setAdapter(singlePersonAdapter);
                        if (!singlePersonNames.isEmpty()) {
                            spinSinglePerson.setText(singlePersonNames.get(0), false);
                        }
                    }
                });

        db.collection("Users").document(userId).collection("Transactions")
                .get()
                .addOnSuccessListener(snapshot -> {
                    existingTxIds.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        existingTxIds.add(doc.getId());
                    }
                });
    }

    private String generateUniqueTransactionId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        SecureRandom random = new SecureRandom();
        String newId;

        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            newId = sb.toString();
        } while (existingTxIds.contains(newId));

        existingTxIds.add(newId);
        return newId;
    }

    private void fetchUnpaidCredits(String finMateId, String finMateName) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        layoutCreditTransactionsContainer.removeAllViews();
        selectedCreditTransactions.clear();
        cardPayBackAdvance.setVisibility(View.GONE);

        tvNoCreditTx.setVisibility(View.VISIBLE);
        tvNoCreditTx.setText(getString(R.string.fetching_unpaid_dues, finMateName));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("Transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    layoutCreditTransactionsContainer.removeAllViews();
                    boolean hasUnpaid = false;

                    Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
                    NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);

                        if (tx != null && "TAKE_CREDIT".equals(tx.getTransactionType()) && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
                            Transaction.TransactionSplit split = tx.getSplits().get(finMateId);

                            if (split != null) {
                                double effectiveDue = split.getCombinedStealthAmount() - split.getPaidAmount();

                                if (effectiveDue > 0.01) {
                                    hasUnpaid = true;

                                    MaterialCheckBox checkBox = new MaterialCheckBox(this);
                                    String dateStr = sdf.format(new Date(tx.getTimestamp()));

                                    checkBox.setText(getString(R.string.checkbox_unpaid_due, tx.getTitle(), dateStr, formatter.format(effectiveDue)));
                                    checkBox.setTextColor(Color.parseColor("#082561"));
                                    checkBox.setTextSize(14f);
                                    checkBox.setPadding(16, 24, 16, 24);

                                    checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                        if (isChecked) {
                                            selectedCreditTransactions.put(tx.getTransactionId(), tx);
                                        } else {
                                            selectedCreditTransactions.remove(tx.getTransactionId());
                                        }
                                        calculatePayBackAdvanceOrPartial();
                                    });

                                    layoutCreditTransactionsContainer.addView(checkBox);
                                }
                            }
                        }
                    }

                    if (hasUnpaid) {
                        tvNoCreditTx.setVisibility(View.GONE);
                    } else {
                        tvNoCreditTx.setVisibility(View.VISIBLE);
                        tvNoCreditTx.setText("No credits to pay back for this person.");
                    }
                })
                .addOnFailureListener(e -> {
                    tvNoCreditTx.setVisibility(View.VISIBLE);
                    tvNoCreditTx.setText(R.string.failed_to_load_transactions);
                });
    }

    private void fetchUnpaidTransactions(String finMateId, String finMateName) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        layoutUnpaidTransactionsContainer.removeAllViews();
        selectedUnpaidTransactions.clear();
        cardReceiveAdvance.setVisibility(View.GONE);

        tvNoUnpaidTx.setVisibility(View.VISIBLE);
        tvNoUnpaidTx.setText(getString(R.string.fetching_unpaid_dues, finMateName));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("Transactions")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    layoutUnpaidTransactionsContainer.removeAllViews();
                    boolean hasUnpaid = false;

                    Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
                    NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);
                    SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

                    double totalSettlements = 0.0;
                    double totalSpends = 0.0;

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx != null && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
                            Transaction.TransactionSplit split = tx.getSplits().get(finMateId);
                            if (split != null) {
                                if ("SETTLEMENT".equals(tx.getTransactionType())) {
                                    totalSettlements += split.getCombinedStealthAmount();
                                } else if ("CASH_SPEND".equals(tx.getTransactionType()) || "CARD_SPEND".equals(tx.getTransactionType())) {
                                    totalSpends += split.getCombinedStealthAmount();
                                }
                            }
                        }
                    }

                    double remainingAdvancePool = Math.max(0, totalSettlements - totalSpends);

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);

                        if (tx != null && ("CASH_SPEND".equals(tx.getTransactionType()) || "CARD_SPEND".equals(tx.getTransactionType())) && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
                            Transaction.TransactionSplit split = tx.getSplits().get(finMateId);

                            if (split != null) {
                                double baseRemainingDue = split.getCombinedStealthAmount() - split.getPaidAmount();

                                if (baseRemainingDue > 0.01) {
                                    double effectiveDue = baseRemainingDue;
                                    if (remainingAdvancePool > 0) {
                                        double absorption = Math.min(remainingAdvancePool, baseRemainingDue);
                                        effectiveDue -= absorption;
                                        remainingAdvancePool -= absorption;
                                    }

                                    if (effectiveDue > 0.01) {
                                        hasUnpaid = true;

                                        MaterialCheckBox checkBox = new MaterialCheckBox(this);
                                        String dateStr = sdf.format(new Date(tx.getTimestamp()));

                                        checkBox.setText(getString(R.string.checkbox_unpaid_due, tx.getTitle(), dateStr, formatter.format(effectiveDue)));
                                        checkBox.setTextColor(Color.parseColor("#082561"));
                                        checkBox.setTextSize(14f);
                                        checkBox.setPadding(16, 24, 16, 24);

                                        checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                                            if (isChecked) {
                                                selectedUnpaidTransactions.put(tx.getTransactionId(), tx);
                                            } else {
                                                selectedUnpaidTransactions.remove(tx.getTransactionId());
                                            }
                                            calculateSettleAdvanceOrPartial();
                                        });

                                        layoutUnpaidTransactionsContainer.addView(checkBox);
                                    }
                                }
                            }
                        }
                    }

                    if (hasUnpaid) {
                        tvNoUnpaidTx.setVisibility(View.GONE);
                    } else {
                        tvNoUnpaidTx.setVisibility(View.VISIBLE);
                        tvNoUnpaidTx.setText(getString(R.string.no_unpaid_dues, finMateName));
                    }
                })
                .addOnFailureListener(e -> {
                    tvNoUnpaidTx.setVisibility(View.VISIBLE);
                    tvNoUnpaidTx.setText(R.string.failed_to_load_transactions);
                });
    }

    private void calculatePayBackAdvanceOrPartial() {
        if (!isSpendMode || rgSpendOptions.getCheckedRadioButtonId() != R.id.rbPayBackCredit) return;

        String totalStr = etTotalAmount.getText() != null ? etTotalAmount.getText().toString().trim() : "";
        double enteredAmount = 0.0;
        if (!totalStr.isEmpty()) {
            try {
                enteredAmount = Double.parseDouble(totalStr);
            } catch (NumberFormatException ignored) {}
        }

        double checkedSum = 0.0;
        for (Transaction tx : selectedCreditTransactions.values()) {
            Transaction.TransactionSplit split = tx.getSplits().get(currentSelectedPayBackFinMateId);
            if (split != null) {
                checkedSum += (split.getCombinedStealthAmount() - split.getPaidAmount());
            }
        }

        if (enteredAmount == 0 && checkedSum == 0) {
            cardPayBackAdvance.setVisibility(View.GONE);
            return;
        }

        cardPayBackAdvance.setVisibility(View.VISIBLE);

        if (enteredAmount > checkedSum) {
            double advance = enteredAmount - checkedSum;
            tvPayBackAdvanceTitle.setText("Overpaying Credit");
            tvPayBackAdvanceTitle.setTextColor(Color.parseColor("#388E3C"));
            tvPayBackAdvanceAmount.setText(getString(R.string.advance_added_format, advance));
            tvPayBackAdvanceAmount.setTextColor(Color.parseColor("#2E7D32"));
            cardPayBackAdvance.setStrokeColor(Color.parseColor("#388E3C"));
            cardPayBackAdvance.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            ivPayBackAdvanceIcon.setColorFilter(Color.parseColor("#388E3C"));
        } else if (enteredAmount < checkedSum) {
            double shortage = checkedSum - enteredAmount;
            tvPayBackAdvanceTitle.setText("Partial Payment");
            tvPayBackAdvanceTitle.setTextColor(Color.parseColor("#E65100"));
            tvPayBackAdvanceAmount.setText(getString(R.string.short_by_format, shortage));
            tvPayBackAdvanceAmount.setTextColor(Color.parseColor("#E65100"));
            cardPayBackAdvance.setStrokeColor(Color.parseColor("#FF9800"));
            cardPayBackAdvance.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
            ivPayBackAdvanceIcon.setColorFilter(Color.parseColor("#FF9800"));
        } else {
            tvPayBackAdvanceTitle.setText("Exact Payment");
            tvPayBackAdvanceTitle.setTextColor(Color.parseColor("#082561"));
            tvPayBackAdvanceAmount.setText("Fully paying off selected credits.");
            tvPayBackAdvanceAmount.setTextColor(Color.parseColor("#082561"));
            cardPayBackAdvance.setStrokeColor(Color.parseColor("#082561"));
            cardPayBackAdvance.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            ivPayBackAdvanceIcon.setColorFilter(Color.parseColor("#082561"));
        }
    }

    private void calculateSettleAdvanceOrPartial() {
        if (isSpendMode || rgReceiveOptions.getCheckedRadioButtonId() != R.id.rbSettleDues) return;

        String totalStr = etTotalAmount.getText() != null ? etTotalAmount.getText().toString().trim() : "";
        double enteredAmount = 0.0;
        if (!totalStr.isEmpty()) {
            try {
                enteredAmount = Double.parseDouble(totalStr);
            } catch (NumberFormatException ignored) {}
        }

        double checkedSum = 0.0;
        for (Transaction tx : selectedUnpaidTransactions.values()) {
            Transaction.TransactionSplit split = tx.getSplits().get(currentSelectedReceiveFinMateId);
            if (split != null) {
                checkedSum += (split.getCombinedStealthAmount() - split.getPaidAmount());
            }
        }

        if (enteredAmount == 0 && checkedSum == 0) {
            cardReceiveAdvance.setVisibility(View.GONE);
            return;
        }

        cardReceiveAdvance.setVisibility(View.VISIBLE);

        if (enteredAmount > checkedSum) {
            double advance = enteredAmount - checkedSum;
            tvAdvanceTitle.setText(R.string.advance_credit_title);
            tvAdvanceTitle.setTextColor(Color.parseColor("#388E3C"));
            tvAdvanceAmount.setText(getString(R.string.advance_added_format, advance));
            tvAdvanceAmount.setTextColor(Color.parseColor("#2E7D32"));
            cardReceiveAdvance.setStrokeColor(Color.parseColor("#388E3C"));
            cardReceiveAdvance.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
            ivAdvanceIcon.setColorFilter(Color.parseColor("#388E3C"));
        } else if (enteredAmount < checkedSum) {
            double shortage = checkedSum - enteredAmount;
            tvAdvanceTitle.setText(R.string.partial_settlement_title);
            tvAdvanceTitle.setTextColor(Color.parseColor("#E65100"));
            tvAdvanceAmount.setText(getString(R.string.short_by_format, shortage));
            tvAdvanceAmount.setTextColor(Color.parseColor("#E65100"));
            cardReceiveAdvance.setStrokeColor(Color.parseColor("#FF9800"));
            cardReceiveAdvance.setCardBackgroundColor(Color.parseColor("#FFF3E0"));
            ivAdvanceIcon.setColorFilter(Color.parseColor("#FF9800"));
        } else {
            tvAdvanceTitle.setText(R.string.exact_settlement_title);
            tvAdvanceTitle.setTextColor(Color.parseColor("#082561"));
            tvAdvanceAmount.setText(R.string.exact_settlement_desc);
            tvAdvanceAmount.setTextColor(Color.parseColor("#082561"));
            cardReceiveAdvance.setStrokeColor(Color.parseColor("#082561"));
            cardReceiveAdvance.setCardBackgroundColor(Color.parseColor("#E3F2FD"));
            ivAdvanceIcon.setColorFilter(Color.parseColor("#082561"));
        }
    }

    private static class PersonItem {
        String id;
        String name;
        PersonItem(String id, String name) { this.id = id; this.name = name; }
        @NonNull
        @Override
        public String toString() { return name; }
    }

    private void showAddPersonDialog() {
        List<PersonItem> availablePersons = new ArrayList<>();

        if (!activeSplitRows.containsKey("self")) {
            availablePersons.add(new PersonItem("self", "Self (You)"));
        }

        for (FinMate fm : allFinMatesList) {
            if (!activeSplitRows.containsKey(fm.getFinMateId())) {
                availablePersons.add(new PersonItem(fm.getFinMateId(), fm.getName()));
            }
        }

        if (availablePersons.isEmpty()) {
            Toast.makeText(this, "All available persons are already added.", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_search_person, null);
        EditText etSearchPerson = dialogView.findViewById(R.id.etSearchPerson);
        ListView listViewPersons = dialogView.findViewById(R.id.listViewPersons);

        ArrayAdapter<PersonItem> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, availablePersons);
        listViewPersons.setAdapter(adapter);

        etSearchPerson.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setView(dialogView);

        GradientDrawable dialogBackground = new GradientDrawable();
        dialogBackground.setColor(Color.WHITE);
        dialogBackground.setCornerRadius(40f);
        builder.setBackground(dialogBackground);

        AlertDialog dialog = builder.create();

        listViewPersons.setOnItemClickListener((parent, view, position, id) -> {
            PersonItem selected = adapter.getItem(position);
            if (selected != null) {
                addSplitRow(selected.id, selected.name);
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void addSplitRow(String personId, String personName) {
        LinearLayout rowContainer = new LinearLayout(this);
        rowContainer.setOrientation(LinearLayout.VERTICAL);
        rowContainer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View rowView = getLayoutInflater().inflate(R.layout.item_split_row, rowContainer, false);
        rowContainer.addView(rowView);

        TextView tvName = rowView.findViewById(R.id.tvSplitFinMateName);
        TextInputEditText etAmount = rowView.findViewById(R.id.etSplitAmount);
        ImageView btnRemove = rowView.findViewById(R.id.btnRemoveSplitRow);

        tvName.setText(personName);

        btnRemove.setOnClickListener(v -> {
            layoutSplitEngineContainer.removeView(rowContainer);
            activeSplitRows.remove(personId);
            activeSplitInputs.remove(personId);
            creditCheckBoxes.remove(personId);
        });

        layoutSplitEngineContainer.addView(rowContainer);
        activeSplitRows.put(personId, rowContainer);
        activeSplitInputs.put(personId, etAmount);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && !personId.equals("self")) {
            FirebaseFirestore.getInstance().collection("Users").document(currentUser.getUid())
                    .collection("Transactions")
                    .get()
                    .addOnSuccessListener(snap -> {
                        double outstandingCredit = 0;
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null && "TAKE_CREDIT".equals(tx.getTransactionType()) && tx.getSplits().containsKey(personId)) {
                                Transaction.TransactionSplit split = tx.getSplits().get(personId);
                                if (split != null) {
                                    double due = split.getCombinedStealthAmount() - split.getPaidAmount();
                                    if (due > 0.01) outstandingCredit += due;
                                }
                            }
                        }
                        if (outstandingCredit > 0.01) {
                            MaterialCheckBox cb = new MaterialCheckBox(AddTransactionActivity.this);
                            Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
                            NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);
                            cb.setText("Utilize available credit (" + formatter.format(outstandingCredit) + ")");
                            cb.setTextColor(Color.parseColor("#E65100"));
                            cb.setTextSize(13f);
                            cb.setChecked(false);

                            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                            params.setMargins(16, 0, 0, 16);
                            cb.setLayoutParams(params);

                            creditCheckBoxes.put(personId, cb);
                            rowContainer.addView(cb);
                        }
                    });
        }

        etAmount.requestFocus();
        etAmount.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etAmount, 0);
            }

            mainScrollView.postDelayed(
                    () -> mainScrollView.smoothScrollTo(0, layoutSplitEngineContainer.getBottom() + 300),
                    250
            );
        });
    }

    private void validateAndSaveTransaction() {
        if (isSpendMode) {
            if (rgSpendOptions.getCheckedRadioButtonId() == R.id.rbNormalSpend) {
                saveSpendTransaction();
            } else if (rgSpendOptions.getCheckedRadioButtonId() == R.id.rbPayCardBill) {
                saveCardBillPayment();
            } else {
                savePayBackCreditTransaction();
            }
        } else {
            if (rgReceiveOptions.getCheckedRadioButtonId() == R.id.rbSettleDues) {
                saveSettleTransaction();
            } else {
                saveTakeCreditTransaction();
            }
        }
    }

    private void updateFinMateBalancesInBatch(WriteBatch batch, String userId, String finMateId, List<DocumentSnapshot> allTxDocs, Transaction newTx) {
        if (finMateId == null || finMateId.equals("self")) return;

        double cardSpend = 0.0;
        double cashSpend = 0.0;
        double cardPaid = 0.0;
        double cashPaid = 0.0;
        double inbound = 0.0;
        double outbound = 0.0;

        for (DocumentSnapshot doc : allTxDocs) {
            Transaction tx = doc.toObject(Transaction.class);
            if (tx != null && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
                Transaction.TransactionSplit split = tx.getSplits().get(finMateId);
                if (split != null) {
                    double amt = split.getCombinedStealthAmount();
                    double paid = split.getPaidAmount();
                    String type = tx.getTransactionType();
                    if ("CARD_SPEND".equals(type)) { cardSpend += amt; cardPaid += paid; }
                    else if ("CASH_SPEND".equals(type)) { cashSpend += amt; cashPaid += paid; }
                    else if ("SETTLEMENT".equals(type) || "TAKE_CREDIT".equals(type)) { inbound += amt; }
                    else if ("PAY_CREDIT".equals(type)) { outbound += amt; }
                }
            }
        }

        if (newTx != null && newTx.getSplits() != null && newTx.getSplits().containsKey(finMateId)) {
            Transaction.TransactionSplit split = newTx.getSplits().get(finMateId);
            if (split != null) {
                double amt = split.getCombinedStealthAmount();
                double paid = split.getPaidAmount();
                String type = newTx.getTransactionType();
                if ("CARD_SPEND".equals(type)) { cardSpend += amt; cardPaid += paid; }
                else if ("CASH_SPEND".equals(type)) { cashSpend += amt; cashPaid += paid; }
                else if ("SETTLEMENT".equals(type) || "TAKE_CREDIT".equals(type)) { inbound += amt; }
                else if ("PAY_CREDIT".equals(type)) { outbound += amt; }
            }
        }

        double netBalance = (cardSpend + cashSpend + outbound) - inbound;

        double finalReceivable = 0.0;
        double finalPayable = 0.0;
        double finalCard = 0.0;
        double finalCash = 0.0;

        if (netBalance > 0.01) {
            finalReceivable = netBalance;

            double calcCard = Math.max(0, cardSpend - cardPaid);
            double calcCash = Math.max(0, cashSpend - cashPaid);

            if (Math.abs((calcCard + calcCash) - netBalance) < 0.1) {
                finalCard = calcCard;
                finalCash = calcCash;
            } else {
                double totalSpends = cardSpend + cashSpend;
                if (totalSpends > 0) {
                    finalCard = netBalance * (cardSpend / totalSpends);
                    finalCash = netBalance * (cashSpend / totalSpends);
                }
            }
        } else if (netBalance < -0.01) {
            finalPayable = Math.abs(netBalance);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("totalReceivable", finalReceivable);
        data.put("payableAmount", finalPayable);
        data.put("receivableCardAmount", finalCard);
        data.put("receivableCashAmount", finalCash);

        DocumentReference fmRef = FirebaseFirestore.getInstance().collection("Users").document(userId).collection("FinMates").document(finMateId);
        batch.set(fmRef, data, SetOptions.merge());
    }

    private void saveCardBillPayment() {
        String title = String.valueOf(etTransactionTitle.getText()).trim();
        String totalStr = String.valueOf(etTotalAmount.getText()).trim();

        if (TextUtils.isEmpty(title)) {
            etTransactionTitle.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(totalStr)) {
            etTotalAmount.setError("Required");
            return;
        }
        if (selectedTransactionTimestamp == 0) {
            etTransactionDate.setError("Please select a date");
            Toast.makeText(this, "Please select a transaction date", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedTargetCardText = spinTargetCreditCard.getText() != null ? spinTargetCreditCard.getText().toString() : "";
        String cardIdToPay = sourceNameToIdMap.get(selectedTargetCardText);

        if (cardIdToPay == null || cardIdToPay.equals("CASH") || cardIdToPay.isEmpty()) {
            Toast.makeText(this, "Please select a valid Credit Card to pay", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        double paymentAmount = Double.parseDouble(totalStr);
        String txId = generateUniqueTransactionId();

        Transaction cardPaymentTx = new Transaction(
                txId,
                "CARD_PAYMENT",
                cardIdToPay,
                title,
                selectedTransactionTimestamp,
                paymentAmount,
                false
        );

        Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
        splitsMap.put("self", new Transaction.TransactionSplit(0.0, paymentAmount, 0.0));
        cardPaymentTx.setSplits(splitsMap);

        btnSaveTransaction.setEnabled(false);
        btnSaveTransaction.setText("Logging Payment...");

        db.collection("Users").document(userId).collection("Transactions").document(txId)
                .set(cardPaymentTx)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Card Bill Logged Successfully!", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                        Toast.makeText(this, "Failed to save: " + err, Toast.LENGTH_SHORT).show();
                        btnSaveTransaction.setEnabled(true);
                        btnSaveTransaction.setText("Log Card Payment");
                    }
                });
    }

    // THE FIX: Extracted dynamically fetching the checked percentage
    private double getSelectedCashbackPercentage() {
        if (rgCashbackPercentage == null) return 0.0;
        int checkedId = rgCashbackPercentage.getCheckedRadioButtonId();
        if (checkedId != -1) {
            View checkedView = rgCashbackPercentage.findViewById(checkedId);
            if (checkedView != null && checkedView.getTag() instanceof Double) {
                return (Double) checkedView.getTag();
            }
        }
        return 0.0;
    }

    private void saveSpendTransaction() {
        String title = String.valueOf(etTransactionTitle.getText()).trim();
        String totalStr = String.valueOf(etTotalAmount.getText()).trim();

        if (TextUtils.isEmpty(title)) {
            etTransactionTitle.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(totalStr)) {
            etTotalAmount.setError("Required");
            return;
        }

        if (selectedTransactionTimestamp == 0) {
            etTransactionDate.setError("Please select a date");
            Toast.makeText(this, "Please select a transaction date", Toast.LENGTH_SHORT).show();
            return;
        }

        double totalAmount = Double.parseDouble(totalStr);
        double sumOfSplits = 0.0;

        Map<String, Double> validatedSplits = new HashMap<>();

        if (rgSplitDecision != null && rgSplitDecision.getCheckedRadioButtonId() == R.id.rbSplitNo) {
            String selectedPersonName = spinSinglePerson.getText() != null ? spinSinglePerson.getText().toString() : "";
            String personId = finMateNameToIdMap.get(selectedPersonName);

            if (personId == null || personId.isEmpty()) {
                Toast.makeText(this, "Please select a person to assign this transaction to.", Toast.LENGTH_SHORT).show();
                return;
            }
            validatedSplits.put(personId, totalAmount);
            sumOfSplits = totalAmount;
        } else {
            if (activeSplitInputs.isEmpty()) {
                Toast.makeText(this, "Please add at least one person to split with!", Toast.LENGTH_SHORT).show();
                return;
            }

            for (Map.Entry<String, TextInputEditText> entry : activeSplitInputs.entrySet()) {
                String personId = entry.getKey();
                String valStr = entry.getValue().getText() != null ? entry.getValue().getText().toString().trim() : "";

                if (TextUtils.isEmpty(valStr)) {
                    entry.getValue().setError("Required");
                    return;
                }

                double amt = Double.parseDouble(valStr);
                sumOfSplits += amt;
                validatedSplits.put(personId, amt);
            }
        }

        if (Math.abs(sumOfSplits - totalAmount) > 0.01) {
            Toast.makeText(this, "Sum of splits (₹" + sumOfSplits + ") must equal Total Amount (₹" + totalAmount + ")!", Toast.LENGTH_LONG).show();
            return;
        }

        String selectedSourceText = spinPaymentSource.getText() != null ? spinPaymentSource.getText().toString() : "";
        String cardId = sourceNameToIdMap.get(selectedSourceText);

        if (cardId == null) {
            Toast.makeText(this, "Please select a valid payment source", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String transactionId = generateUniqueTransactionId();
        String txType = cardId.equals("CASH") ? "CASH_SPEND" : "CARD_SPEND";

        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(txSnapshot -> {

            Map<String, Double> totalSettlementsMap = new HashMap<>();
            Map<String, Double> totalSpendsMap = new HashMap<>();

            Map<String, List<Transaction>> availableSettlements = new HashMap<>();
            Map<String, List<Transaction>> availableCreditTransactions = new HashMap<>();

            for (DocumentSnapshot doc : txSnapshot.getDocuments()) {
                Transaction tx = doc.toObject(Transaction.class);
                if (tx == null || tx.getSplits() == null) continue;

                for (Map.Entry<String, Transaction.TransactionSplit> splitEntry : tx.getSplits().entrySet()) {
                    String mateId = splitEntry.getKey();
                    Transaction.TransactionSplit split = splitEntry.getValue();
                    if (split == null) continue;

                    double amount = split.getCombinedStealthAmount();
                    double paid = split.getPaidAmount();

                    if ("SETTLEMENT".equals(tx.getTransactionType())) {
                        Double currentSetObj = totalSettlementsMap.get(mateId);
                        double currentSet = (currentSetObj != null) ? currentSetObj : 0.0;
                        totalSettlementsMap.put(mateId, currentSet + amount);

                        if (amount - paid > 0.01) {
                            List<Transaction> setList = availableSettlements.get(mateId);
                            if (setList == null) {
                                setList = new ArrayList<>();
                                availableSettlements.put(mateId, setList);
                            }
                            setList.add(tx);
                        }
                    } else if ("CASH_SPEND".equals(tx.getTransactionType()) || "CARD_SPEND".equals(tx.getTransactionType())) {
                        Double currentSpdObj = totalSpendsMap.get(mateId);
                        double currentSpd = (currentSpdObj != null) ? currentSpdObj : 0.0;
                        totalSpendsMap.put(mateId, currentSpd + amount);

                    } else if ("TAKE_CREDIT".equals(tx.getTransactionType())) {
                        if (amount - paid > 0.01) {
                            List<Transaction> creditList = availableCreditTransactions.get(mateId);
                            if (creditList == null) {
                                creditList = new ArrayList<>();
                                availableCreditTransactions.put(mateId, creditList);
                            }
                            creditList.add(tx);
                        }
                    }
                }
            }

            WriteBatch batch = db.batch();

            Transaction transaction = new Transaction(
                    transactionId,
                    txType,
                    !cardId.equals("CASH") ? cardId : null,
                    title,
                    selectedTransactionTimestamp,
                    totalAmount,
                    false
            );

            // --- CASHBACK CALCULATION LOGIC ---
            if (cardCashback != null && cardCashback.getVisibility() == View.VISIBLE) {
                double percent = getSelectedCashbackPercentage();
                if (percent > 0.0) {
                    double cbEarned = Math.floor((totalAmount * percent) / 100.0);
                    transaction.setCashbackEarned(cbEarned);
                }
            }

            Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();

            for (Map.Entry<String, Double> entry : validatedSplits.entrySet()) {
                String personId = entry.getKey();
                double amtToPay = entry.getValue();
                double totalPaidForThisSplit = 0.0;

                Double tSetObj = totalSettlementsMap.get(personId);
                Double tSpdObj = totalSpendsMap.get(personId);
                double tSet = (tSetObj != null) ? tSetObj : 0.0;
                double tSpd = (tSpdObj != null) ? tSpdObj : 0.0;

                double trueStandardAdvance = Math.max(0, tSet - tSpd);

                if (trueStandardAdvance > 0.01) {
                    double utilized = Math.min(trueStandardAdvance, amtToPay);
                    totalPaidForThisSplit += utilized;
                    amtToPay -= utilized;

                    List<Transaction> settlements = availableSettlements.get(personId);
                    if (settlements != null) {
                        double tempUtilize = utilized;
                        for (Transaction setTx : settlements) {
                            if (tempUtilize <= 0.01) break;
                            Transaction.TransactionSplit sSplit = setTx.getSplits().get(personId);
                            if (sSplit != null) {
                                double due = sSplit.getCombinedStealthAmount() - sSplit.getPaidAmount();
                                double apply = Math.min(due, tempUtilize);

                                double newPaidAmt = sSplit.getPaidAmount() + apply;
                                DocumentReference setRef = db.collection("Users").document(userId).collection("Transactions").document(setTx.getTransactionId());
                                batch.update(setRef, "splits." + personId + ".paidAmount", newPaidAmt);

                                tempUtilize -= apply;
                            }
                        }
                    }
                }

                MaterialCheckBox cb = creditCheckBoxes.get(personId);
                if (amtToPay > 0.01 && cb != null && cb.isChecked()) {
                    List<Transaction> credits = availableCreditTransactions.get(personId);
                    if (credits != null) {
                        for (Transaction creditTx : credits) {
                            if (amtToPay <= 0.01) break;
                            Transaction.TransactionSplit cSplit = creditTx.getSplits().get(personId);
                            if (cSplit != null) {
                                double due = cSplit.getCombinedStealthAmount() - cSplit.getPaidAmount();
                                double utilized = Math.min(due, amtToPay);

                                double newPaidAmt = cSplit.getPaidAmount() + utilized;
                                DocumentReference creditRef = db.collection("Users").document(userId).collection("Transactions").document(creditTx.getTransactionId());
                                batch.update(creditRef, "splits." + personId + ".paidAmount", newPaidAmt);

                                totalPaidForThisSplit += utilized;
                                amtToPay -= utilized;
                            }
                        }
                    }
                }

                double originalSplitAmount = entry.getValue();
                double cardSplitAmt = txType.equals("CARD_SPEND") ? originalSplitAmount : 0.0;
                double cashSplitAmt = txType.equals("CASH_SPEND") ? originalSplitAmount : 0.0;

                splitsMap.put(personId, new Transaction.TransactionSplit(cardSplitAmt, cashSplitAmt, totalPaidForThisSplit));
            }

            transaction.setSplits(splitsMap);

            for (String personId : validatedSplits.keySet()) {
                updateFinMateBalancesInBatch(batch, userId, personId, txSnapshot.getDocuments(), transaction);
            }

            DocumentReference newTxRef = db.collection("Users").document(userId).collection("Transactions").document(transactionId);
            batch.set(newTxRef, transaction);

            btnSaveTransaction.setEnabled(false);
            btnSaveTransaction.setText("Saving...");

            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Transaction Saved Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(this, "Failed to save: " + err, Toast.LENGTH_SHORT).show();
                    btnSaveTransaction.setEnabled(true);
                    btnSaveTransaction.setText(R.string.save_transaction);
                }
            });
        });
    }

    private void savePayBackCreditTransaction() {
        String title = String.valueOf(etTransactionTitle.getText()).trim();
        String totalStr = String.valueOf(etTotalAmount.getText()).trim();

        if (TextUtils.isEmpty(title)) {
            etTransactionTitle.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(totalStr)) {
            etTotalAmount.setError("Required");
            return;
        }
        if (selectedTransactionTimestamp == 0) {
            etTransactionDate.setError("Please select a date");
            Toast.makeText(this, "Please select a transaction date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentSelectedPayBackFinMateId == null) {
            Toast.makeText(this, "Please select a person to pay back", Toast.LENGTH_SHORT).show();
            return;
        }

        String selectedSourceText = spinPaymentSource.getText() != null ? spinPaymentSource.getText().toString() : "";
        String cardId = sourceNameToIdMap.get(selectedSourceText);

        if (cardId == null) {
            Toast.makeText(this, "Please select a valid payment source", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(txSnapshot -> {
            double paymentSent = Double.parseDouble(totalStr);
            double tempPaymentSent = paymentSent;
            double amountApplied = 0.0;

            WriteBatch batch = db.batch();
            String payBackTxId = generateUniqueTransactionId();

            for (Transaction oldTx : selectedCreditTransactions.values()) {
                if (tempPaymentSent <= 0) break;

                Transaction.TransactionSplit oldSplit = oldTx.getSplits().get(currentSelectedPayBackFinMateId);
                if (oldSplit != null) {
                    double remainingDue = oldSplit.getCombinedStealthAmount() - oldSplit.getPaidAmount();
                    double allocation = Math.min(tempPaymentSent, remainingDue);

                    double newPaidAmount = oldSplit.getPaidAmount() + allocation;
                    amountApplied += allocation;
                    tempPaymentSent -= allocation;

                    DocumentReference oldTxRef = db.collection("Users").document(userId).collection("Transactions").document(oldTx.getTransactionId());
                    batch.update(oldTxRef, "splits." + currentSelectedPayBackFinMateId + ".paidAmount", newPaidAmount);
                }
            }

            Transaction payBackTx = new Transaction(
                    payBackTxId,
                    "PAY_CREDIT",
                    !cardId.equals("CASH") ? cardId : null,
                    title,
                    selectedTransactionTimestamp,
                    paymentSent,
                    false
            );

            Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
            splitsMap.put(currentSelectedPayBackFinMateId, new Transaction.TransactionSplit(0.0, paymentSent, amountApplied));
            payBackTx.setSplits(splitsMap);

            DocumentReference payBackRef = db.collection("Users").document(userId).collection("Transactions").document(payBackTxId);
            batch.set(payBackRef, payBackTx);

            updateFinMateBalancesInBatch(batch, userId, currentSelectedPayBackFinMateId, txSnapshot.getDocuments(), payBackTx);

            btnSaveTransaction.setEnabled(false);
            btnSaveTransaction.setText("Saving Payment...");

            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Credit Payment Saved!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(this, "Failed to save: " + err, Toast.LENGTH_SHORT).show();
                    btnSaveTransaction.setEnabled(true);
                    btnSaveTransaction.setText("Pay Back Credit");
                }
            });
        });
    }

    private void saveSettleTransaction() {
        String title = String.valueOf(etTransactionTitle.getText()).trim();
        String totalStr = String.valueOf(etTotalAmount.getText()).trim();

        if (TextUtils.isEmpty(title)) {
            etTransactionTitle.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(totalStr)) {
            etTotalAmount.setError("Required");
            return;
        }
        if (selectedTransactionTimestamp == 0) {
            etTransactionDate.setError("Please select a date");
            Toast.makeText(this, "Please select a transaction date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentSelectedReceiveFinMateId == null) {
            Toast.makeText(this, "Please select a person settling up", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(txSnapshot -> {
            double paymentReceived = Double.parseDouble(totalStr);
            double tempPaymentReceived = paymentReceived;
            double amountApplied = 0.0;

            WriteBatch batch = db.batch();
            String settlementTxId = generateUniqueTransactionId();

            for (Transaction oldTx : selectedUnpaidTransactions.values()) {
                if (tempPaymentReceived <= 0) break;

                Transaction.TransactionSplit oldSplit = oldTx.getSplits().get(currentSelectedReceiveFinMateId);
                if (oldSplit != null) {
                    double remainingDue = oldSplit.getCombinedStealthAmount() - oldSplit.getPaidAmount();
                    double allocation = Math.min(tempPaymentReceived, remainingDue);

                    double newPaidAmount = oldSplit.getPaidAmount() + allocation;
                    amountApplied += allocation;
                    tempPaymentReceived -= allocation;

                    DocumentReference oldTxRef = db.collection("Users").document(userId).collection("Transactions").document(oldTx.getTransactionId());
                    batch.update(oldTxRef, "splits." + currentSelectedReceiveFinMateId + ".paidAmount", newPaidAmount);
                }
            }

            Transaction settlementTx = new Transaction(
                    settlementTxId,
                    "SETTLEMENT",
                    null,
                    title,
                    selectedTransactionTimestamp,
                    paymentReceived,
                    false
            );

            Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
            splitsMap.put(currentSelectedReceiveFinMateId, new Transaction.TransactionSplit(0.0, paymentReceived, amountApplied));
            settlementTx.setSplits(splitsMap);

            DocumentReference settlementRef = db.collection("Users").document(userId).collection("Transactions").document(settlementTxId);
            batch.set(settlementRef, settlementTx);

            updateFinMateBalancesInBatch(batch, userId, currentSelectedReceiveFinMateId, txSnapshot.getDocuments(), settlementTx);

            btnSaveTransaction.setEnabled(false);
            btnSaveTransaction.setText("Saving Settlement...");

            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Settlement Saved Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(this, "Failed to save: " + err, Toast.LENGTH_SHORT).show();
                    btnSaveTransaction.setEnabled(true);
                    btnSaveTransaction.setText("Save Settlement");
                }
            });
        });
    }

    private void saveTakeCreditTransaction() {
        String title = String.valueOf(etTransactionTitle.getText()).trim();
        String totalStr = String.valueOf(etTotalAmount.getText()).trim();

        if (TextUtils.isEmpty(title)) {
            etTransactionTitle.setError("Required");
            return;
        }
        if (TextUtils.isEmpty(totalStr)) {
            etTotalAmount.setError("Required");
            return;
        }
        if (selectedTransactionTimestamp == 0) {
            etTransactionDate.setError("Please select a date");
            Toast.makeText(this, "Please select a transaction date", Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentSelectedReceiveFinMateId == null) {
            Toast.makeText(this, "Please select a person giving credit", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(txSnapshot -> {
            double creditTaken = Double.parseDouble(totalStr);

            WriteBatch batch = db.batch();
            String takeCreditTxId = generateUniqueTransactionId();

            Transaction takeCreditTx = new Transaction(
                    takeCreditTxId,
                    "TAKE_CREDIT",
                    null,
                    title,
                    selectedTransactionTimestamp,
                    creditTaken,
                    false
            );

            Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
            splitsMap.put(currentSelectedReceiveFinMateId, new Transaction.TransactionSplit(0.0, creditTaken, 0.0));
            takeCreditTx.setSplits(splitsMap);

            DocumentReference newTxRef = db.collection("Users").document(userId).collection("Transactions").document(takeCreditTxId);
            batch.set(newTxRef, takeCreditTx);

            updateFinMateBalancesInBatch(batch, userId, currentSelectedReceiveFinMateId, txSnapshot.getDocuments(), takeCreditTx);

            btnSaveTransaction.setEnabled(false);
            btnSaveTransaction.setText("Logging Credit...");

            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Credit Logged Successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                    Toast.makeText(this, "Failed to save: " + err, Toast.LENGTH_SHORT).show();
                    btnSaveTransaction.setEnabled(true);
                    btnSaveTransaction.setText("Take Credit");
                }
            });
        });
    }

    private String getBankInitials(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) return "BANK";

        Map<String, String> shortNameMap = new HashMap<>();
        shortNameMap.put("AU Small Finance Bank", "AUSFB");
        shortNameMap.put("American Express", "AMEX");
        shortNameMap.put("Axis Bank", "AXIS");
        shortNameMap.put("Bandhan Bank", "BANDHAN");
        shortNameMap.put("Bank of Baroda", "BOB");
        shortNameMap.put("Bank of India", "BOI");
        shortNameMap.put("Bank of Maharashtra", "BOM");
        shortNameMap.put("Barclays Bank", "BARB");
        shortNameMap.put("Baroda Gujarat Gramin Bank", "BGGB");
        shortNameMap.put("Baroda Rajasthan Kshetriya Gramin Bank", "BRKGB");
        shortNameMap.put("Baroda U.P. Bank", "BUPB");
        shortNameMap.put("CSB Bank", "CSB");
        shortNameMap.put("Canara Bank", "CAN");
        shortNameMap.put("Capital Small Finance Bank", "CSFB");
        shortNameMap.put("Central Bank of India", "CBI");
        shortNameMap.put("City Union Bank", "CUB");
        shortNameMap.put("Cosmos Co-operative Bank", "CCB");
        shortNameMap.put("DBS Bank", "DBS");
        shortNameMap.put("DCB Bank", "DCB");
        shortNameMap.put("Deutsche Bank", "DB");
        shortNameMap.put("Dhanlaxmi Bank", "DLB");
        shortNameMap.put("ESAF Small Finance Bank", "ESFB");
        shortNameMap.put("Equitas Small Finance Bank", "ESFB");
        shortNameMap.put("Federal Bank", "FED");
        shortNameMap.put("First Abu Dhabi Bank", "FAB");
        shortNameMap.put("HDFC Bank", "HDFC");
        shortNameMap.put("HSBC Bank", "HSBC");
        shortNameMap.put("ICICI Bank Limited", "ICICI");
        shortNameMap.put("IDFC FIRST Bank", "IDFC");
        shortNameMap.put("Indian Bank", "IB");
        shortNameMap.put("Indian Overseas Bank", "IOB");
        shortNameMap.put("IndusInd Bank", "IND");
        shortNameMap.put("Jammu & Kashmir Bank", "J&K");
        shortNameMap.put("Jana Small Finance Bank", "JSFB");
        shortNameMap.put("Karnataka Bank", "KBL");
        shortNameMap.put("Karur Vysya Bank", "KVB");
        shortNameMap.put("Kerala Gramin Bank", "KGB");
        shortNameMap.put("Kotak Mahindra Bank", "KOTAK");
        shortNameMap.put("Nainital Bank", "NB");
        shortNameMap.put("Punjab & Sind Bank", "PSB");
        shortNameMap.put("Punjab National Bank", "PNB");
        shortNameMap.put("RBL Bank", "RBL");
        shortNameMap.put("SBM Bank India", "SBM");
        shortNameMap.put("SVC Co-operative Bank", "SVC");
        shortNameMap.put("Saraswat Co-operative Bank", "SCB");
        shortNameMap.put("South Indian Bank", "SIB");
        shortNameMap.put("Standard Chartered Bank", "SCB");
        shortNameMap.put("State Bank of India", "SBI");
        shortNameMap.put("Suryoday Small Finance Bank", "SSFB");
        shortNameMap.put("Tamilnad Mercantile Bank", "TMB");
        shortNameMap.put("UCO Bank", "UCO");
        shortNameMap.put("Ujjivan Small Finance Bank", "USFB");
        shortNameMap.put("Union Bank of India", "UBI");
        shortNameMap.put("Utkarsh Small Finance Bank", "USFB");
        shortNameMap.put("YES Bank", "YES");

        if (shortNameMap.containsKey(bankName)) {
            return shortNameMap.get(bankName);
        }

        String[] words = bankName.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].length() > 4 ? words[0].substring(0, 4).toUpperCase() : words[0].toUpperCase();
        } else {
            StringBuilder initials = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) initials.append(word.charAt(0));
            }
            return initials.toString().toUpperCase();
        }
    }
}