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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout), (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            View mainContent = findViewById(R.id.main);
            mainContent.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        NavigationView navigationView = findViewById(R.id.navigationView);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        drawerLayout = findViewById(R.id.drawerLayout);
        ImageView ivMenuDrawer = findViewById(R.id.ivMenuDrawer);
        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        TextView btnAddCard = findViewById(R.id.btnAddCard);
        TextView btnAddPerson = findViewById(R.id.btnAddPerson);

        LinearLayout navItemCards = findViewById(R.id.navItemCards);
        LinearLayout navItemLedger = findViewById(R.id.navItemLedger);
        LinearLayout navSetings = findViewById(R.id.navSetings);

        LinearLayout layoutEmptyCards = findViewById(R.id.layoutEmptyCards);
        LinearLayout layoutCardsContainer = findViewById(R.id.layoutCardsContainer);
        View cardItem1 = findViewById(R.id.cardItem1);
        View cardDivider = findViewById(R.id.cardDivider);
        View cardItem2 = findViewById(R.id.cardItem2);

        LinearLayout layoutEmptyFinMates = findViewById(R.id.layoutEmptyFinMates);
        LinearLayout layoutFinMatesContainer = findViewById(R.id.layoutFinMatesContainer);
        View ledgerItem1 = findViewById(R.id.ledgerItem1);
        View ledgerDivider = findViewById(R.id.ledgerDivider);
        View ledgerItem2 = findViewById(R.id.ledgerItem2);

        TextView btnAddCardEmpty = findViewById(R.id.btnAddCardEmpty);
        TextView btnAddFinMateEmpty = findViewById(R.id.btnAddFinMateEmpty);

        int totalCards = getSimulatedTotalCards();
        if (totalCards == 0) {
            layoutEmptyCards.setVisibility(View.VISIBLE);
            layoutCardsContainer.setVisibility(View.GONE);
        } else {
            layoutEmptyCards.setVisibility(View.GONE);
            layoutCardsContainer.setVisibility(View.VISIBLE);
            cardItem1.setVisibility(View.VISIBLE);
            if (totalCards == 1) {
                cardDivider.setVisibility(View.GONE);
                cardItem2.setVisibility(View.GONE);
            } else {
                cardDivider.setVisibility(View.VISIBLE);
                cardItem2.setVisibility(View.VISIBLE);
            }
        }

        int totalFinMates = getSimulatedTotalFinMates();
        if (totalFinMates == 0) {
            layoutEmptyFinMates.setVisibility(View.VISIBLE);
            layoutFinMatesContainer.setVisibility(View.GONE);
        } else {
            layoutEmptyFinMates.setVisibility(View.GONE);
            layoutFinMatesContainer.setVisibility(View.VISIBLE);
            ledgerItem1.setVisibility(View.VISIBLE);
            if (totalFinMates == 1) {
                ledgerDivider.setVisibility(View.GONE);
                ledgerItem2.setVisibility(View.GONE);
            } else {
                ledgerDivider.setVisibility(View.VISIBLE);
                ledgerItem2.setVisibility(View.VISIBLE);
            }
        }

        ivMenuDrawer.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_drawer_dashboard) {
                Toast.makeText(MainActivity.this, "Already on Dashboard", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_drawer_cards) {
                Intent intent = new Intent(MainActivity.this, CardsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            } else if (id == R.id.nav_drawer_ledger) {
                Intent intent = new Intent(MainActivity.this, FinMatesActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            } else if (id == R.id.nav_drawer_signout) {
                FirebaseAuth.getInstance().signOut();

                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();

                GoogleSignIn.getClient(MainActivity.this, gso).signOut().addOnCompleteListener(task -> {
                    Toast.makeText(MainActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        swipeRefresh.setOnRefreshListener(() -> {
            Toast.makeText(MainActivity.this, "Refreshing financial ledger...", Toast.LENGTH_SHORT).show();
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1500);
        });

        cardItem1.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked Card Slot 1", Toast.LENGTH_SHORT).show());
        cardItem2.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked Card Slot 2", Toast.LENGTH_SHORT).show());

        ledgerItem1.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked FinMate Slot 1", Toast.LENGTH_SHORT).show());
        ledgerItem2.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked FinMate Slot 2", Toast.LENGTH_SHORT).show());

        btnAddCard.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CardsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });

        btnAddPerson.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FinMatesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });

        btnAddCardEmpty.setOnClickListener(v -> showAddCardBottomSheet());
        btnAddFinMateEmpty.setOnClickListener(v -> showAddFinMateBottomSheet());

        navItemCards.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CardsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });

        navItemLedger.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, FinMatesActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });

        navSetings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    @SuppressLint("SetTextI18n")
    private void showAddCardBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        // Using findViewById(android.R.id.content) resolves the layout parameters properly without null
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
        // Using findViewById(android.R.id.content) resolves the layout parameters properly without null
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

    private int getSimulatedTotalCards() {
        return 0;
    }

    private int getSimulatedTotalFinMates() {
        return 0;
    }
}