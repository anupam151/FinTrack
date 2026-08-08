package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.TextUtils;
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
import com.google.android.material.textfield.TextInputEditText;

public class AddEditFinMateActivity extends AppCompatActivity {

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_add_edit_finmate);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainAddEditFinMate), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageView ivBackAddEditFinMate = findViewById(R.id.ivBackAddEditFinMate);
        TextView tvFinMateFormTitle = findViewById(R.id.tvFinMateFormTitle);
        TextInputEditText etFinMateName = findViewById(R.id.etFinMateName);
        TextInputEditText etWhatsAppNo = findViewById(R.id.etWhatsAppNo);
        MaterialButton btnSaveFinMate = findViewById(R.id.btnSaveFinMate);

        boolean isEditMode = getIntent().hasExtra("EDIT_FINMATE_ID");
        String finMateIdToEdit = isEditMode ? getIntent().getStringExtra("EDIT_FINMATE_ID") : null;

        if (isEditMode) {
            tvFinMateFormTitle.setText("Edit FinMate");
            btnSaveFinMate.setText("Update FinMate");
            // TO DO: Fetch existing data from Firebase using finMateIdToEdit
        }

        ivBackAddEditFinMate.setOnClickListener(v -> finish());

        btnSaveFinMate.setOnClickListener(v -> {
            String name = String.valueOf(etFinMateName.getText()).trim();
            String whatsAppNo = String.valueOf(etWhatsAppNo.getText()).trim();

            if (TextUtils.isEmpty(name)) {
                etFinMateName.setError("Enter Name");
                return;
            }
            if (TextUtils.isEmpty(whatsAppNo)) {
                etWhatsAppNo.setError("Enter WhatsApp Number");
                return;
            }
            if (whatsAppNo.length() < 10) {
                etWhatsAppNo.setError("Enter a valid 10-digit number");
                return;
            }

            etFinMateName.setError(null);
            etWhatsAppNo.setError(null);

            if (isEditMode) {
                Toast.makeText(this, "FinMate " + finMateIdToEdit + " Updated Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "New FinMate Added Successfully!", Toast.LENGTH_SHORT).show();
            }

            finish();
        });
    }
}