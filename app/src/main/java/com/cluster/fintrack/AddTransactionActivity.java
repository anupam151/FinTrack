package com.cluster.fintrack;

import android.annotation.SuppressLint;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("SetTextI18n")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class AddTransactionActivity extends AppCompatActivity {

    private ImageView ivBack;
    private TextView tvTransactionTitle;
    private MaterialButton btnTypeSpend, btnTypeReceive;
    private TextInputEditText etTransactionTitle, etTotalAmount;
    private MaterialCardView cardPaymentSource, cardSplitEngine;
    private MaterialAutoCompleteTextView spinPaymentSource;
    private TextView btnAddSplitFinMate;
    private LinearLayout layoutSplitEngineContainer;
    private MaterialButton btnSaveTransaction;

    private final List<FinMate> allFinMatesList = new ArrayList<>();
    private final Map<String, String> sourceNameToIdMap = new HashMap<>();
    private final Map<String, View> activeSplitRows = new HashMap<>();
    private final Map<String, TextInputEditText> activeSplitInputs = new HashMap<>();

    private boolean isSpendMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_transaction);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAddTransaction), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        initializeViews();
        setupListeners();
        fetchFirestoreData();
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v instanceof TextInputEditText) {
                Rect outRect = new Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    if (getWindow() != null) {
                        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), v);
                        controller.hide(WindowInsetsCompat.Type.ime());
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void initializeViews() {
        ivBack = findViewById(R.id.ivBack);
        tvTransactionTitle = findViewById(R.id.tvTransactionTitle);
        btnTypeSpend = findViewById(R.id.btnTypeSpend);
        btnTypeReceive = findViewById(R.id.btnTypeReceive);

        etTransactionTitle = findViewById(R.id.etTransactionTitle);
        etTotalAmount = findViewById(R.id.etTotalAmount);

        cardPaymentSource = findViewById(R.id.cardPaymentSource);
        cardSplitEngine = findViewById(R.id.cardSplitEngine);

        spinPaymentSource = findViewById(R.id.spinPaymentSource);
        btnAddSplitFinMate = findViewById(R.id.btnAddSplitFinMate);
        layoutSplitEngineContainer = findViewById(R.id.layoutSplitEngineContainer);
        btnSaveTransaction = findViewById(R.id.btnSaveTransaction);
    }

    private void setupListeners() {
        ivBack.setOnClickListener(v -> finish());

        btnTypeSpend.setOnClickListener(v -> {
            isSpendMode = true;
            tvTransactionTitle.setText("New Transaction (Spend)");
            cardPaymentSource.setVisibility(View.VISIBLE);
            cardSplitEngine.setVisibility(View.VISIBLE);
            btnSaveTransaction.setText("Save Spend");

            setTabActive(btnTypeSpend, true);
            setTabActive(btnTypeReceive, false);
        });

        btnTypeReceive.setOnClickListener(v -> {
            isSpendMode = false;
            tvTransactionTitle.setText("Settle Up (Receive)");
            cardPaymentSource.setVisibility(View.GONE);
            cardSplitEngine.setVisibility(View.VISIBLE);
            btnSaveTransaction.setText("Save Settlement");

            setTabActive(btnTypeReceive, true);
            setTabActive(btnTypeSpend, false);
        });

        btnAddSplitFinMate.setOnClickListener(v -> showAddPersonDialog());
        btnSaveTransaction.setOnClickListener(v -> validateAndSaveTransaction());
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
                    for (DocumentSnapshot doc : snapshot) {
                        FinMate finMate = doc.toObject(FinMate.class);
                        if (finMate != null) {
                            allFinMatesList.add(finMate);
                        }
                    }
                });
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
                imm.showSoftInput(etAmount, 0); // Replaced deprecated SHOW_IMPLICIT with 0
            }
        });
    }

    private void validateAndSaveTransaction() {
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
                entry.getValue().setError("Enter amount");
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

        if (isSpendMode && cardId == null) {
            Toast.makeText(this, "Please select a valid payment source", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String transactionId = db.collection("Users").document(userId).collection("Transactions").document().getId();

        String txType = isSpendMode ? "CARD_SPEND" : "SETTLEMENT";
        if (cardId != null && cardId.equals("CASH")) txType = "CASH_SPEND";

        Transaction transaction = new Transaction(
                transactionId,
                txType,
                cardId != null && !cardId.equals("CASH") ? cardId : null,
                title,
                System.currentTimeMillis(),
                totalAmount,
                false
        );

        Map<String, Transaction.TransactionSplit> splitsMap = new HashMap<>();
        for (Map.Entry<String, Double> entry : validatedSplits.entrySet()) {
            String personId = entry.getKey();
            double amt = entry.getValue();

            if (personId.equals("self")) {
                splitsMap.put("self", new Transaction.TransactionSplit(amt, 0.0));
            } else {
                splitsMap.put(personId, new Transaction.TransactionSplit(amt, 0.0));
            }
        }
        transaction.setSplits(splitsMap);

        btnSaveTransaction.setEnabled(false);
        btnSaveTransaction.setText("Saving Transaction...");

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
                        btnSaveTransaction.setText("Save Transaction");
                    }
                });
    }
}