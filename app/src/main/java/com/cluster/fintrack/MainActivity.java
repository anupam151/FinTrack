package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // Personal Dashboard Variables
    private TextView tvDashPersonalTotal, tvDashPersonalCard, tvDashPersonalCash;

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
        navigationView.setItemIconTintList(null);
        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            androidx.core.graphics.Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        drawerLayout = findViewById(R.id.drawerLayout);
        ImageView ivMenuDrawer = findViewById(R.id.ivMenuDrawer);
        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipeRefresh);
        ImageButton btnAddCard = findViewById(R.id.btnAddCard);
        ImageButton btnAddPerson = findViewById(R.id.btnAddPerson);
        ImageButton btnPersonalLedger = findViewById(R.id.btnPersonalLedger);

        LinearLayout navItemCards = findViewById(R.id.navItemCards);
        LinearLayout navItemLedger = findViewById(R.id.navItemLedger);
        LinearLayout navSetings = findViewById(R.id.navSetings);

        TextView btnAddCardEmpty = findViewById(R.id.btnAddCardEmpty);
        TextView btnAddFinMateEmpty = findViewById(R.id.btnAddFinMateEmpty);

        // Initialize Personal Dashboard Widgets
        tvDashPersonalTotal = findViewById(R.id.tvDashPersonalTotal);
        tvDashPersonalCard = findViewById(R.id.tvDashPersonalCard);
        tvDashPersonalCash = findViewById(R.id.tvDashPersonalCash);

        MaterialCardView cardDashboardPersonal = findViewById(R.id.cardDashboardPersonal);
        if (cardDashboardPersonal != null) {
            cardDashboardPersonal.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, PersonalLedgerActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
            });
        }

        // Load live Firestore data
        loadCardsFromFirestore();
        loadFinMatesFromFirestore();
        loadPersonalExpensesFromFirestore();

        ivMenuDrawer.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
            else drawerLayout.openDrawer(GravityCompat.START);
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            drawerLayout.postDelayed(() -> {
                if (id == R.id.nav_drawer_dashboard) {
                    Toast.makeText(MainActivity.this, "Already on Dashboard", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_drawer_cards) {
                    Intent intent = new Intent(MainActivity.this, CardsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
                } else if (id == R.id.nav_drawer_ledger) {
                    Intent intent = new Intent(MainActivity.this, FinMatesActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    overridePendingTransition(0, 0);
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
            }, 250);
            return false;
        });

        swipeRefresh.setOnRefreshListener(() -> {
            Toast.makeText(MainActivity.this, "Refreshing financial ledger...", Toast.LENGTH_SHORT).show();
            swipeRefresh.postDelayed(() -> swipeRefresh.setRefreshing(false), 1500);
        });

        btnPersonalLedger.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PersonalLedgerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)));
        btnAddCard.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, CardsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)));
        btnAddPerson.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, FinMatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)));

        // THE FIX: Launch Intent instead of BottomSheet
        btnAddCardEmpty.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddCardActivity.class)));
        btnAddFinMateEmpty.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddFinMateActivity.class)));

        navItemCards.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, CardsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)));
        navItemLedger.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, FinMatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)));
        navSetings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        ImageButton btnAddTransactionHeader = findViewById(R.id.btnAddTransactionHeader);
        if (btnAddTransactionHeader != null) {
            btnAddTransactionHeader.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, AddTransactionActivity.class)));
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadCardsFromFirestore() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        LinearLayout layoutEmptyCards = findViewById(R.id.layoutEmptyCards);
        RecyclerView recyclerViewCards = findViewById(R.id.recyclerViewCards);
        recyclerViewCards.setLayoutManager(new LinearLayoutManager(this));

        TextView tvValueCards = findViewById(R.id.tvValueCards);
        TextView tvValueLimit = findViewById(R.id.tvValueLimit);

        List<Card> cardList = new ArrayList<>();
        CardAdapter adapter = new CardAdapter(this, cardList);
        recyclerViewCards.setAdapter(adapter);

        db.collection("Users").document(userId).collection("Cards")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    cardList.clear();
                    double totalLimitSum = 0.0;
                    int totalCardsCount = 0;

                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Card card = doc.toObject(Card.class);
                            if (card != null) {
                                cardList.add(card);
                                totalLimitSum += card.getTotalLimit();
                                totalCardsCount++;
                            }
                        }
                    }

                    if (tvValueCards != null) tvValueCards.setText(String.valueOf(totalCardsCount));
                    if (tvValueLimit != null) {
                        java.util.Locale indianLocale = new java.util.Locale.Builder().setLanguage("en").setRegion("IN").build();
                        java.text.NumberFormat formatter = java.text.NumberFormat.getCurrencyInstance(indianLocale);
                        formatter.setMaximumFractionDigits(0);
                        tvValueLimit.setText(formatter.format(totalLimitSum));
                    }

                    if (cardList.isEmpty()) {
                        layoutEmptyCards.setVisibility(View.VISIBLE);
                        recyclerViewCards.setVisibility(View.GONE);
                    } else {
                        layoutEmptyCards.setVisibility(View.GONE);
                        recyclerViewCards.setVisibility(View.VISIBLE);
                        cardList.sort((c1, c2) -> Long.compare(c2.getTimestamp(), c1.getTimestamp()));
                        if (cardList.size() > 1) {
                            List<Card> topOneCard = new ArrayList<>();
                            topOneCard.add(cardList.get(0));
                            cardList.clear();
                            cardList.addAll(topOneCard);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void loadFinMatesFromFirestore() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;
        String userId = currentUser.getUid();
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        LinearLayout layoutEmptyFinMates = findViewById(R.id.layoutEmptyFinMates);
        RecyclerView recyclerViewFinMates = findViewById(R.id.recyclerViewFinMates);
        recyclerViewFinMates.setLayoutManager(new LinearLayoutManager(this));

        List<FinMate> finMateList = new ArrayList<>();
        FinMateAdapter adapter = new FinMateAdapter(this, finMateList, null);
        recyclerViewFinMates.setAdapter(adapter);

        db.collection("Users").document(userId).collection("FinMates")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) return;
                    finMateList.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            FinMate finMate = doc.toObject(FinMate.class);
                            if (finMate != null) finMateList.add(finMate);
                        }
                    }

                    if (finMateList.isEmpty()) {
                        layoutEmptyFinMates.setVisibility(View.VISIBLE);
                        recyclerViewFinMates.setVisibility(View.GONE);
                    } else {
                        layoutEmptyFinMates.setVisibility(View.GONE);
                        recyclerViewFinMates.setVisibility(View.VISIBLE);
                        finMateList.sort((f1, f2) -> Long.compare(f2.getTimestamp(), f1.getTimestamp()));
                        if (finMateList.size() > 1) {
                            List<FinMate> topOneFinMate = new ArrayList<>();
                            topOneFinMate.add(finMateList.get(0));
                            finMateList.clear();
                            finMateList.addAll(topOneFinMate);
                        }
                        adapter.notifyDataSetChanged();
                    }
                });
    }

    @SuppressLint("SetTextI18n")
    private void loadPersonalExpensesFromFirestore() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(currentUser.getUid()).collection("Transactions")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    double cumulativeCash = 0.0;
                    Map<String, Double> cardWiseDue = new HashMap<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx == null) continue;

                        String type = tx.getTransactionType();
                        if ("PAY_CREDIT".equals(type) && tx.getSplits() != null) {
                            double amt = tx.getTotalAmount();
                            String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";
                            if ("CASH".equals(cId)) cumulativeCash += amt;
                            else cardWiseDue.merge(cId, amt, Double::sum);
                        }

                        if (tx.getSplits() != null && tx.getSplits().containsKey("self")) {
                            Transaction.TransactionSplit mySplit = tx.getSplits().get("self");
                            if (mySplit != null) {
                                double amt = mySplit.getCombinedStealthAmount();
                                if (amt > 0.01) {
                                    String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";
                                    if ("CASH_SPEND".equals(type) || "CASH".equals(cId)) cumulativeCash += amt;
                                    else if ("CARD_SPEND".equals(type)) cardWiseDue.merge(cId, amt, Double::sum);
                                }
                            }
                        }
                    }

                    double totalCardSpends = 0.0;
                    for (Double val : cardWiseDue.values()) totalCardSpends += val;
                    double currentDue = totalCardSpends + cumulativeCash;

                    Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
                    NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);

                    if (tvDashPersonalTotal != null) tvDashPersonalTotal.setText(formatter.format(currentDue));
                    if (tvDashPersonalCard != null) tvDashPersonalCard.setText(formatter.format(totalCardSpends));
                    if (tvDashPersonalCash != null) tvDashPersonalCash.setText(formatter.format(cumulativeCash));
                });
    }
}