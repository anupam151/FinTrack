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

public class CardsActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_cards);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        drawerLayout = findViewById(R.id.drawerLayoutCards);
        ViewCompat.setOnApplyWindowInsetsListener(drawerLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            findViewById(R.id.mainCards).setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationViewCards);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageView ivMenuDrawerCards = findViewById(R.id.ivMenuDrawerCards);
        ivMenuDrawerCards.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        LinearLayout navItemDashboard = findViewById(R.id.navItemDashboard);
        LinearLayout navItemLedger = findViewById(R.id.navItemLedger);
        LinearLayout navSetings = findViewById(R.id.navSetings);

        navItemDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        navItemLedger.setOnClickListener(v -> {
            Intent intent = new Intent(this, FinMatesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            finish();
        });

        navSetings.setOnClickListener(v -> {
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
        });

        findViewById(R.id.fabAddCard).setOnClickListener(v -> showAddCardBottomSheet());
    }

    @SuppressLint("SetTextI18n")
    private void showAddCardBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_card, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        // Standard Fields
        TextView title = view.findViewById(R.id.tvCardSheetTitle);
        TextInputEditText etCardName = view.findViewById(R.id.etSheetCardName);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveCard);

        // Color Picker Fields
        com.google.android.material.card.MaterialCardView cardColorPreview = view.findViewById(R.id.cardColorPreview);
        MaterialButton btnPickColor = view.findViewById(R.id.btnPickColor);

        title.setText("Add Credit Card");
        btnSave.setText("Save Card");

        // 1. Set the default starting color (Navy Blue: #082561)
        // We use an array so we can modify it inside the lambda/click listener
        final int[] currentColor = {android.graphics.Color.parseColor("#082561")};

        // 2. Open the Color Picker when the button is clicked
        btnPickColor.setOnClickListener(v -> {
            yuku.ambilwarna.AmbilWarnaDialog colorPickerDialog = new yuku.ambilwarna.AmbilWarnaDialog(this, currentColor[0], new yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onCancel(yuku.ambilwarna.AmbilWarnaDialog dialog) {
                    // User canceled, do nothing
                }

                @Override
                public void onOk(yuku.ambilwarna.AmbilWarnaDialog dialog, int color) {
                    // Update our color variable
                    currentColor[0] = color;
                    // Update the preview box UI instantly
                    cardColorPreview.setCardBackgroundColor(color);
                }
            });
            colorPickerDialog.show();
        });

        // 3. Save button logic
        btnSave.setOnClickListener(v -> {
            String name = String.valueOf(etCardName.getText()).trim();
            if (TextUtils.isEmpty(name)) {
                etCardName.setError("Enter Card Name");
                return;
            }

            // Convert the integer color back to a Hex String (e.g., "#082561") so it's easy to save to Firebase later
            String hexColor = String.format("#%06X", (0xFFFFFF & currentColor[0]));

            Toast.makeText(this, "Card Saved! Color: " + hexColor, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}