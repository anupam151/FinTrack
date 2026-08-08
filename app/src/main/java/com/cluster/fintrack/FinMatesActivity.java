package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;

public class FinMatesActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_finmates);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        drawerLayout = findViewById(R.id.drawerLayoutFinMates);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            findViewById(R.id.mainFinMates).setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationViewFinMates);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageView ivMenuDrawerFinMates = findViewById(R.id.ivMenuDrawerFinMates);
        ivMenuDrawerFinMates.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        LinearLayout navItemDashboard = findViewById(R.id.navItemDashboard);
        LinearLayout navItemCards = findViewById(R.id.navItemCards);
        LinearLayout navSetings = findViewById(R.id.navSetings);

        navItemDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        navItemCards.setOnClickListener(v -> {
            Intent intent = new Intent(this, CardsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        navSetings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.fabAddFinMate).setOnClickListener(v -> showAddFinMateBottomSheet());
    }

    @SuppressLint("SetTextI18n")
    private void showAddFinMateBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        // Resolves layout parameters properly without passing null
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_finmate, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvSheetTitle);
        TextInputEditText etName = view.findViewById(R.id.etSheetFinMateName);
        TextInputEditText etWhatsApp = view.findViewById(R.id.etSheetWhatsAppNo);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveFinMate);

        title.setText("Add FinMate");
        btnSave.setText("Save FinMate");

        btnSave.setOnClickListener(v -> {
            String name = String.valueOf(etName.getText()).trim();
            String whatsAppNo = String.valueOf(etWhatsApp.getText()).trim();

            if (TextUtils.isEmpty(name)) {
                etName.setError("Enter Name");
                return;
            }
            if (TextUtils.isEmpty(whatsAppNo) || whatsAppNo.length() < 10) {
                etWhatsApp.setError("Enter 10-digit number");
                return;
            }

            Toast.makeText(this, "FinMate Saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}