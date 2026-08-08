package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

public class AddEditCardActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Edge-to-Edge setup
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_edit_card);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAddEditCard), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        // 2. Initialize Views
        ImageView ivBackAddEdit = findViewById(R.id.ivBackAddEdit);
        TextView tvFormTitle = findViewById(R.id.tvFormTitle);

        MaterialAutoCompleteTextView spinBankName = findViewById(R.id.spinBankName);
        TextInputEditText etCardName = findViewById(R.id.etCardName);
        TextInputEditText etTotalLimit = findViewById(R.id.etTotalLimit);
        TextInputEditText etBillingDay = findViewById(R.id.etBillingDay);
        MaterialButton btnSaveCard = findViewById(R.id.btnSaveCard);

        // 3. Setup Bank Dropdown
        String[] banks = new String[]{"HDFC", "SBI", "ICICI", "Axis", "Kotak", "Amex", "Standard Chartered", "IndusInd", "IDFC First"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, banks);
        spinBankName.setAdapter(adapter);

        // 4. Check if Add or Edit Mode (Converted to local variables)
        boolean isEditMode = getIntent().hasExtra("EDIT_CARD_ID");
        String cardIdToEdit = isEditMode ? getIntent().getStringExtra("EDIT_CARD_ID") : null;

        if (isEditMode) {
            // Switch UI to Edit Mode
            tvFormTitle.setText("Edit Credit Card");
            btnSaveCard.setText("Update Card");
            // TO DO: Fetch existing card details from Firebase using cardIdToEdit
        }

        // 5. Click Listeners
        ivBackAddEdit.setOnClickListener(v -> finish());

        btnSaveCard.setOnClickListener(v -> {
            // Safe string extraction to prevent NullPointerException
            String bankName = String.valueOf(spinBankName.getText()).trim();
            String cardName = String.valueOf(etCardName.getText()).trim();
            String limitStr = String.valueOf(etTotalLimit.getText()).trim();
            String billingDayStr = String.valueOf(etBillingDay.getText()).trim();

            // Validation
            if (TextUtils.isEmpty(bankName)) {
                spinBankName.setError("Select a Bank");
                return;
            }
            if (TextUtils.isEmpty(cardName)) {
                etCardName.setError("Enter Card Name");
                return;
            }
            if (TextUtils.isEmpty(limitStr)) {
                etTotalLimit.setError("Enter Total Limit");
                return;
            }
            if (TextUtils.isEmpty(billingDayStr)) {
                etBillingDay.setError("Enter Billing Day");
                return;
            }

            int billingDay = Integer.parseInt(billingDayStr);
            if (billingDay < 1 || billingDay > 31) {
                etBillingDay.setError("Must be between 1 and 31");
                return;
            }

            // Remove errors if valid
            spinBankName.setError(null);
            etCardName.setError(null);
            etTotalLimit.setError(null);
            etBillingDay.setError(null);

            if (isEditMode) {
                // TO DO: Update existing card in Firebase
                Toast.makeText(this, "Card " + cardIdToEdit + " Updated Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                // TO DO: Save new card to Firebase
                Toast.makeText(this, "New Card Added Successfully!", Toast.LENGTH_SHORT).show();
            }

            finish(); // Close activity and go back
        });
    }
}