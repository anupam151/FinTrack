package com.cluster.fintrack;

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
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
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
import com.google.firebase.firestore.WriteBatch;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class AddTransactionActivity extends AppCompatActivity {

    private ImageView ivBack;
    private TextView tvTransactionTitle;
    private MaterialButton btnTypeSpend, btnTypeReceive;
    private TextInputEditText etTransactionTitle, etTotalAmount, etTransactionDate;

    private NestedScrollView mainScrollView;

    // SPEND UI ELEMENTS
    private MaterialCardView cardPaymentSource, cardSplitEngine;
    private MaterialAutoCompleteTextView spinPaymentSource;
    private TextView btnAddSplitFinMate;
    private LinearLayout layoutSplitEngineContainer;

    // RECEIVE UI ELEMENTS
    private MaterialCardView cardReceivePerson, cardReceiveUnpaidList, cardReceiveAdvance;
    private MaterialAutoCompleteTextView spinReceivePerson;
    private TextView tvNoUnpaidTx, tvAdvanceTitle, tvAdvanceAmount;
    private ImageView ivAdvanceIcon;
    private LinearLayout layoutUnpaidTransactionsContainer;

    private MaterialButton btnSaveTransaction;

    private final List<FinMate> allFinMatesList = new ArrayList<>();
    private final Map<String, String> sourceNameToIdMap = new HashMap<>();
    private final Map<String, String> finMateNameToIdMap = new HashMap<>();

    private final Map<String, View> activeSplitRows = new HashMap<>();
    private final Map<String, TextInputEditText> activeSplitInputs = new HashMap<>();

    private final LinkedHashMap<String, Transaction> selectedUnpaidTransactions = new LinkedHashMap<>();
    private String currentSelectedReceiveFinMateId = null;

    private boolean isSpendMode = true;
    private long selectedTransactionTimestamp = 0;
    private float touchDownX, touchDownY;
    private double previousCheckedSum = 0.0;

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
            return windowInsets;
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

        cardPaymentSource = findViewById(R.id.cardPaymentSource);
        cardSplitEngine = findViewById(R.id.cardSplitEngine);
        spinPaymentSource = findViewById(R.id.spinPaymentSource);
        btnAddSplitFinMate = findViewById(R.id.btnAddSplitFinMate);
        layoutSplitEngineContainer = findViewById(R.id.layoutSplitEngineContainer);

        cardReceivePerson = findViewById(R.id.cardReceivePerson);
        cardReceiveUnpaidList = findViewById(R.id.cardReceiveUnpaidList);
        cardReceiveAdvance = findViewById(R.id.cardReceiveAdvance);
        spinReceivePerson = findViewById(R.id.spinReceivePerson);
        tvNoUnpaidTx = findViewById(R.id.tvNoUnpaidTx);
        tvAdvanceTitle = findViewById(R.id.tvAdvanceTitle);
        tvAdvanceAmount = findViewById(R.id.tvAdvanceAmount);
        ivAdvanceIcon = findViewById(R.id.ivAdvanceIcon);
        layoutUnpaidTransactionsContainer = findViewById(R.id.layoutUnpaidTransactionsContainer);

        btnSaveTransaction = findViewById(R.id.btnSaveTransaction);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        etTotalAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isSpendMode) {
                    calculateAdvanceOrPartial();
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

            cardPaymentSource.setVisibility(View.VISIBLE);
            cardSplitEngine.setVisibility(View.VISIBLE);
            cardReceivePerson.setVisibility(View.GONE);
            cardReceiveUnpaidList.setVisibility(View.GONE);
            cardReceiveAdvance.setVisibility(View.GONE);

            btnSaveTransaction.setText(R.string.save_spend);

            setTabActive(btnTypeSpend, true);
            setTabActive(btnTypeReceive, false);

            etTotalAmount.setText("");
        });

        btnTypeReceive.setOnClickListener(v -> {
            isSpendMode = false;
            tvTransactionTitle.setText(R.string.settle_up_receive);

            cardPaymentSource.setVisibility(View.GONE);
            cardSplitEngine.setVisibility(View.GONE);
            cardReceivePerson.setVisibility(View.VISIBLE);
            cardReceiveUnpaidList.setVisibility(View.VISIBLE);

            btnSaveTransaction.setText(R.string.save_settlement);

            setTabActive(btnTypeReceive, true);
            setTabActive(btnTypeSpend, false);

            etTotalAmount.setText("");
            previousCheckedSum = 0.0;
            selectedUnpaidTransactions.clear();
            cardReceiveAdvance.setVisibility(View.GONE);
        });

        spinReceivePerson.setOnItemClickListener((parent, view, position, id) -> {
            String selectedName = parent.getItemAtPosition(position).toString();
            String selectedFinMateId = finMateNameToIdMap.get(selectedName);

            if (selectedFinMateId != null) {
                currentSelectedReceiveFinMateId = selectedFinMateId;
                fetchUnpaidTransactions(selectedFinMateId, selectedName);
            }
        });

        btnAddSplitFinMate.setOnClickListener(v -> showAddPersonDialog());
        btnSaveTransaction.setOnClickListener(v -> validateAndSaveTransaction());
    }

    private void updateDateDisplay() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
        etTransactionDate.setText(sdf.format(new Date(selectedTransactionTimestamp)));
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

    private void fetchFirestoreData() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(userId).collection("Cards")
                .get()
                .addOnSuccessListener(snapshot -> {
                    sourceNameToIdMap.clear();
                    List<String> sourceNames = new ArrayList<>();
                    sourceNames.add("Cash (Personal Liquidity)");
                    sourceNameToIdMap.put("Cash (Personal Liquidity)", "CASH");

                    for (DocumentSnapshot doc : snapshot) {
                        Card card = doc.toObject(Card.class);
                        if (card != null) {
                            String displayName = card.getCardName() + " - " + card.getBankName() + " (... " + card.getLast4Digits() + ")";
                            sourceNames.add(displayName);
                            sourceNameToIdMap.put(displayName, card.getCardId());
                        }
                    }

                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, sourceNames);
                    spinPaymentSource.setAdapter(adapter);
                    if (!sourceNames.isEmpty()) {
                        spinPaymentSource.setText(sourceNames.get(0), false);
                    }
                });

        db.collection("Users").document(userId).collection("FinMates")
                .get()
                .addOnSuccessListener(snapshot -> {
                    allFinMatesList.clear();
                    finMateNameToIdMap.clear();
                    List<String> finMateNames = new ArrayList<>();

                    for (DocumentSnapshot doc : snapshot) {
                        FinMate finMate = doc.toObject(FinMate.class);
                        if (finMate != null) {
                            allFinMatesList.add(finMate);
                            finMateNames.add(finMate.getName());
                            finMateNameToIdMap.put(finMate.getName(), finMate.getFinMateId());
                        }
                    }

                    ArrayAdapter<String> receiveAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, finMateNames);
                    spinReceivePerson.setAdapter(receiveAdapter);
                });
    }

    private void fetchUnpaidTransactions(String finMateId, String finMateName) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        layoutUnpaidTransactionsContainer.removeAllViews();
        selectedUnpaidTransactions.clear();
        previousCheckedSum = 0.0;
        cardReceiveAdvance.setVisibility(View.GONE);

        tvNoUnpaidTx.setVisibility(View.VISIBLE);
        tvNoUnpaidTx.setText(getString(R.string.fetching_unpaid_dues, finMateName));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        db.collection("Users").document(currentUser.getUid()).collection("FinMates").document(finMateId)
                .get()
                .addOnSuccessListener(finMateDoc -> {
                    FinMate targetMate = finMateDoc.toObject(FinMate.class);
                    double globalAdvanceCredit = 0.0;
                    if (targetMate != null) {
                        double net = targetMate.getTotalReceivable() - targetMate.getPayableAmount();
                        if (net < 0) {
                            globalAdvanceCredit = Math.abs(net);
                        }
                    }

                    final double availableAdvance = globalAdvanceCredit;

                    db.collection("Users").document(currentUser.getUid()).collection("Transactions")
                            .orderBy("timestamp", Query.Direction.DESCENDING)
                            .get()
                            .addOnSuccessListener(snapshot -> {
                                layoutUnpaidTransactionsContainer.removeAllViews();
                                boolean hasUnpaid = false;

                                Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
                                NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);
                                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

                                double remainingAdvancePool = availableAdvance;

                                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                                    Transaction tx = doc.toObject(Transaction.class);

                                    if (tx != null && !"SETTLEMENT".equals(tx.getTransactionType()) && tx.getSplits() != null && tx.getSplits().containsKey(finMateId)) {
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
                                                        calculateAdvanceOrPartial();
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
                });
    }

    private void calculateAdvanceOrPartial() {
        if (isSpendMode) return;

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
            @Override public void beforeTextChanged(@NonNull CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(@NonNull CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(@NonNull Editable s) {}
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
        View rowView = getLayoutInflater().inflate(R.layout.item_split_row, layoutSplitEngineContainer, false);

        TextView tvName = rowView.findViewById(R.id.tvSplitFinMateName);
        TextInputEditText etAmount = rowView.findViewById(R.id.etSplitAmount);
        ImageView btnRemove = rowView.findViewById(R.id.btnRemoveSplitRow);

        tvName.setText(personName);

        btnRemove.setOnClickListener(v -> {
            layoutSplitEngineContainer.removeView(rowView);
            activeSplitRows.remove(personId);
            activeSplitInputs.remove(personId);
        });

        layoutSplitEngineContainer.addView(rowView);
        activeSplitRows.put(personId, rowView);
        activeSplitInputs.put(personId, etAmount);

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
            saveSpendTransaction();
        } else {
            saveReceiveTransaction();
        }
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

        if (activeSplitInputs.isEmpty()) {
            Toast.makeText(this, "Please add at least one person to split with!", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Double> validatedSplits = new HashMap<>();
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
        String transactionId = db.collection("Users").document(userId).collection("Transactions").document().getId();

        String txType = cardId.equals("CASH") ? "CASH_SPEND" : "CARD_SPEND";

        db.collection("Users").document(userId).collection("Transactions").get().addOnSuccessListener(txSnapshot -> {
            Map<String, Double> netCredits = new HashMap<>();

            for (DocumentSnapshot doc : txSnapshot.getDocuments()) {
                Transaction tx = doc.toObject(Transaction.class);
                if (tx != null && tx.getSplits() != null) {
                    for (Map.Entry<String, Transaction.TransactionSplit> splitEntry : tx.getSplits().entrySet()) {
                        String mateId = splitEntry.getKey();
                        Transaction.TransactionSplit split = splitEntry.getValue();
                        if (split == null) continue;

                        Double existingBal = netCredits.get(mateId);
                        double currentBalance = (existingBal != null) ? existingBal : 0.0;

                        if ("SETTLEMENT".equals(tx.getTransactionType())) {
                            currentBalance += split.getCombinedStealthAmount();
                        } else {
                            currentBalance -= (split.getCombinedStealthAmount() - split.getPaidAmount());
                        }
                        netCredits.put(mateId, currentBalance);
                    }
                }
            }

            Transaction transaction = new Transaction(
                    transactionId,
                    txType,
                    !cardId.equals("CASH") ? cardId : null,
                    title,
                    selectedTransactionTimestamp,
                    totalAmount,
                    false
            );

            Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
            for (Map.Entry<String, Double> entry : validatedSplits.entrySet()) {
                String personId = entry.getKey();
                double amt = entry.getValue();

                double amountPaidForThisSplit = 0.0;
                Double creditObj = netCredits.get(personId);
                double creditAvailable = (creditObj != null) ? creditObj : 0.0;

                if (creditAvailable > 0) {
                    amountPaidForThisSplit = Math.min(creditAvailable, amt);
                }

                double cardSplitAmt = txType.equals("CARD_SPEND") ? amt : 0.0;
                double cashSplitAmt = txType.equals("CASH_SPEND") ? amt : 0.0;

                splitsMap.put(personId, new Transaction.TransactionSplit(cardSplitAmt, cashSplitAmt, amountPaidForThisSplit));
            }
            transaction.setSplits(splitsMap);

            btnSaveTransaction.setEnabled(false);
            btnSaveTransaction.setText(R.string.saving_transaction);

            db.collection("Users").document(userId).collection("Transactions").document(transactionId)
                    .set(transaction)
                    .addOnCompleteListener(task -> {
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

    private void saveReceiveTransaction() {
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

        double paymentReceived = Double.parseDouble(totalStr);
        double originalPaymentReceived = paymentReceived;

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        WriteBatch batch = db.batch();

        String settlementTxId = db.collection("Users").document(userId).collection("Transactions").document().getId();

        Transaction settlementTx = new Transaction(
                settlementTxId,
                "SETTLEMENT",
                null,
                title,
                selectedTransactionTimestamp,
                originalPaymentReceived,
                false
        );

        Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
        splitsMap.put(currentSelectedReceiveFinMateId, new Transaction.TransactionSplit(0.0, originalPaymentReceived, 0.0));
        settlementTx.setSplits(splitsMap);

        DocumentReference settlementRef = db.collection("Users").document(userId).collection("Transactions").document(settlementTxId);
        batch.set(settlementRef, settlementTx);

        for (Transaction oldTx : selectedUnpaidTransactions.values()) {
            if (paymentReceived <= 0) break;

            Transaction.TransactionSplit oldSplit = oldTx.getSplits().get(currentSelectedReceiveFinMateId);
            if (oldSplit != null) {
                double remainingDue = oldSplit.getCombinedStealthAmount() - oldSplit.getPaidAmount();
                double allocation = Math.min(paymentReceived, remainingDue);

                double newPaidAmount = oldSplit.getPaidAmount() + allocation;
                paymentReceived -= allocation;

                DocumentReference oldTxRef = db.collection("Users").document(userId).collection("Transactions").document(oldTx.getTransactionId());
                batch.update(oldTxRef, "splits." + currentSelectedReceiveFinMateId + ".paidAmount", newPaidAmount);
            }
        }

        btnSaveTransaction.setEnabled(false);
        btnSaveTransaction.setText(R.string.saving_settlement);

        batch.commit().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Settlement Saved Successfully!", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                String err = task.getException() != null ? task.getException().getMessage() : "Unknown error";
                Toast.makeText(this, "Failed to save: " + err, Toast.LENGTH_SHORT).show();
                btnSaveTransaction.setEnabled(true);
                btnSaveTransaction.setText(R.string.save_settlement);
            }
        });
    }
}