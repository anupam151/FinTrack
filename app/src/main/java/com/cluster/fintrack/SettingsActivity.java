package com.cluster.fintrack;

import android.content.Intent;
import android.os.Bundle;
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
import com.google.android.material.navigation.NavigationView;
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
        LinearLayout btnMasterTransactions = findViewById(R.id.btnMasterTransactions);
        TextView btnExportData = findViewById(R.id.btnExportData);
        TextView btnSupport = findViewById(R.id.btnSupport);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            tvUserEmail.setText(user.getEmail());
        }

        ivMenuDrawerSettings.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
            else drawerLayout.openDrawer(GravityCompat.START);
        });

        btnMasterTransactions.setOnClickListener(v -> {
            Intent intent = new Intent(SettingsActivity.this, MasterTransactionsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
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

        // THE FIX: Launch Intents instead of BottomSheets
        btnAddCardSettings.setOnClickListener(v -> startActivity(new Intent(SettingsActivity.this, AddCardActivity.class)));
        btnAddFinMateSettings.setOnClickListener(v -> startActivity(new Intent(SettingsActivity.this, AddFinMateActivity.class)));

        btnExportData.setOnClickListener(v -> Toast.makeText(this, "Exporting Ledger to CSV...", Toast.LENGTH_SHORT).show());
        btnSupport.setOnClickListener(v -> Toast.makeText(this, "Opening Support Email...", Toast.LENGTH_SHORT).show());
    }
}