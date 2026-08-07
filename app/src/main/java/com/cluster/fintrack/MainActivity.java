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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Enable modern Edge-to-Edge display
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);

        // 2. Set dark status bar icons since background is white
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);

        // 3. Handle Edge-to-Edge Window Insets on the DrawerLayout and Main Content
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawerLayout), (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());

            // Apply padding to main content view so it avoids system bars
            View mainContent = findViewById(R.id.main);
            mainContent.setPadding(0, insets.top, 0, insets.bottom);

            return windowInsets;
        });

        // 4. Handle Edge-to-Edge Window Insets on the Navigation View so drawer items don't hide under bars
        NavigationView navigationView = findViewById(R.id.navigationView);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        // 5. Initialize UI components
        drawerLayout = findViewById(R.id.drawerLayout);
        ImageView ivMenuDrawer = findViewById(R.id.ivMenuDrawer);
        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        TextView btnAddCard = findViewById(R.id.btnAddCard);
        TextView btnAddPerson = findViewById(R.id.btnAddPerson);

        LinearLayout navItemCards = findViewById(R.id.navItemCards);
        LinearLayout navItemLedger = findViewById(R.id.navItemLedger);
        LinearLayout navItemEmi = findViewById(R.id.navItemEmi);

        // 6. Setup Side Drawer Toggle
        ivMenuDrawer.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        // 7. Handle Side Drawer Item Clicks based on updated menu items
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_drawer_dashboard) {
                Toast.makeText(MainActivity.this, "Already on Dashboard", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_drawer_cards) {
                Toast.makeText(MainActivity.this, "Redirecting to Cards List...", Toast.LENGTH_SHORT).show();
                // TO DO: Intent to CardsListActivity
            } else if (id == R.id.nav_drawer_ledger) {
                Toast.makeText(MainActivity.this, "Redirecting to FinMates List...", Toast.LENGTH_SHORT).show();
                // TO DO: Intent to FinMateListActivity
            } else if (id == R.id.nav_drawer_signout) {
                FirebaseAuth.getInstance().signOut();

                Toast.makeText(MainActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();

                // Redirect to LoginActivity and clear task stack
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            // Close drawer smoothly after selection
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        // 8. Setup Swipe to Refresh Action
        swipeRefresh.setOnRefreshListener(() -> {
            // TO DO: Fetch updated credit card data and peer balances from Firebase here later
            Toast.makeText(MainActivity.this, "Refreshing financial ledger...", Toast.LENGTH_SHORT).show();

            // Stop the refreshing animation after 1.5 seconds
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1500);
        });

        // 9. Setup Section 1: Card Slot Clicks with Toast verification
        View cardSlot1 = findViewById(R.id.cardItem1);
        View cardSlot2 = findViewById(R.id.cardItem2);

        cardSlot1.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked Card Slot 1 (HDFC)", Toast.LENGTH_SHORT).show());
        cardSlot2.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked Card Slot 2 (SBI)", Toast.LENGTH_SHORT).show());

        // 10. Setup Section 2: FinMates Ledger Slot Clicks with Toast verification
        View ledgerSlot1 = findViewById(R.id.ledgerItem1);
        View ledgerSlot2 = findViewById(R.id.ledgerItem2);

        ledgerSlot1.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked FinMate Slot 1 (Rahul)", Toast.LENGTH_SHORT).show());
        ledgerSlot2.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Clicked FinMate Slot 2 (Amit)", Toast.LENGTH_SHORT).show());

        // 11. Setup Button Clicks ("More" links)
        btnAddCard.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Opening All Credit Cards (More)...", Toast.LENGTH_SHORT).show();
            // TO DO: Intent to CardsListActivity
        });

        btnAddPerson.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Opening Full FinMates Ledger (More)...", Toast.LENGTH_SHORT).show();
            // TO DO: Intent to LedgerListActivity
        });

        // 12. Setup Bottom Navigation Bar Actions
        navItemCards.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Navigating to Cards Tab", Toast.LENGTH_SHORT).show();
            // TO DO: Switch fragment or start CardsActivity
        });

        navItemLedger.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Navigating to FinMates Tab", Toast.LENGTH_SHORT).show();
            // TO DO: Switch fragment or start FinMatesActivity
        });

        navItemEmi.setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, "Navigating to EMIs Tab", Toast.LENGTH_SHORT).show();
            // TO DO: Switch fragment or start EMIsActivity
        });
    }
}