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
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

@SuppressWarnings("deprecation")
public class SettingsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        drawerLayout = findViewById(R.id.drawerLayoutSettings);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            View mainContent = findViewById(R.id.mainSettingsContent);
            mainContent.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationViewSettings);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageView ivMenuDrawerSettings = findViewById(R.id.ivMenuDrawerSettings);
        TextView tvUserEmail = findViewById(R.id.tvUserEmail);
        TextView btnAddCardSettings = findViewById(R.id.btnAddCardSettings);
        TextView btnAddFinMateSettings = findViewById(R.id.btnAddFinMateSettings);
        LinearLayout btnCurrency = findViewById(R.id.btnCurrency);
        TextView btnExportData = findViewById(R.id.btnExportData);
        TextView btnSupport = findViewById(R.id.btnSupport);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        }

        ivMenuDrawerSettings.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_drawer_dashboard) {
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            } else if (id == R.id.nav_drawer_cards) {
                Intent intent = new Intent(SettingsActivity.this, CardsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            } else if (id == R.id.nav_drawer_ledger) {
                Intent intent = new Intent(SettingsActivity.this, FinMatesActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            } else if (id == R.id.nav_drawer_signout) {
                FirebaseAuth.getInstance().signOut();

                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

                GoogleSignIn.getClient(SettingsActivity.this, gso).signOut().addOnCompleteListener(task -> {
                    Toast.makeText(SettingsActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        btnAddCardSettings.setOnClickListener(v -> showAddCardBottomSheet());
        btnAddFinMateSettings.setOnClickListener(v -> showAddFinMateBottomSheet());

        btnCurrency.setOnClickListener(v -> Toast.makeText(this, "Currency selection coming soon", Toast.LENGTH_SHORT).show());
        btnExportData.setOnClickListener(v -> Toast.makeText(this, "Exporting Ledger to CSV...", Toast.LENGTH_SHORT).show());
        btnSupport.setOnClickListener(v -> Toast.makeText(this, "Opening Support Email...", Toast.LENGTH_SHORT).show());
    }

    @SuppressLint("SetTextI18n")
    private void showAddCardBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        // Resolves layout parameters properly without passing null
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_card, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvCardSheetTitle);
        TextInputEditText etCardName = view.findViewById(R.id.etSheetCardName);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveCard);

        title.setText("Add Credit Card");
        btnSave.setText("Save Card");

        btnSave.setOnClickListener(v -> {
            String name = String.valueOf(etCardName.getText()).trim();
            if (TextUtils.isEmpty(name)) {
                etCardName.setError("Enter Card Name");
                return;
            }
            Toast.makeText(this, "Card Saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
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