package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
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
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
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

    // UI References for Bottom Sheet so the Contact Picker can update them
    private TextInputEditText currentEtFinMateName;
    private TextInputEditText currentEtWhatsAppNo;

    // Personal Dashboard Variables
    private TextView tvDashPersonalTotal, tvDashPersonalCard, tvDashPersonalCash;

    // Launchers for Permissions and Contact Picker
    private androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher;
    private androidx.activity.result.ActivityResultLauncher<Intent> contactPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Initialize Contact Picker Launcher
        contactPickerLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        android.net.Uri contactUri = result.getData().getData();
                        extractContactInfo(contactUri);
                    }
                });

        // 2. Initialize Permission Launcher
        requestPermissionLauncher = registerForActivityResult(
                new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        launchContactPicker();
                    } else {
                        Toast.makeText(this, "Permission required to import contacts", Toast.LENGTH_SHORT).show();
                    }
                });

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

        // Load live Firestore data for Cards, FinMates, and Personal Expenses
        loadCardsFromFirestore();
        loadFinMatesFromFirestore();
        loadPersonalExpensesFromFirestore();

        ivMenuDrawer.setOnClickListener(v -> {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else {
                drawerLayout.openDrawer(GravityCompat.START);
            }
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();

            // 1. Close the drawer IMMEDIATELY
            drawerLayout.closeDrawer(GravityCompat.START);

            // 2. Wait for the drawer to finish sliding closed (~250ms) before starting the new Activity.
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

        btnPersonalLedger.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, PersonalLedgerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
        });

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

        // THE FIX: Launch AddCardActivity instead of bottom sheet
        btnAddCardEmpty.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddCardActivity.class);
            startActivity(intent);
        });

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

        ImageButton btnAddTransactionHeader = findViewById(R.id.btnAddTransactionHeader);
        if (btnAddTransactionHeader != null) {
            btnAddTransactionHeader.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, AddTransactionActivity.class);
                startActivity(intent);
            });
        }
    }

    // --- CONTACT PICKER HELPER METHODS ---
    private void launchContactPicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        contactPickerLauncher.launch(intent);
    }

    private void extractContactInfo(android.net.Uri contactUri) {
        String[] projection = new String[]{
                android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
        };

        try (android.database.Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER);

                String name = cursor.getString(nameIndex);
                String number = cursor.getString(numberIndex);

                if (number != null) {
                    number = number.replaceAll("[^0-9]", "");
                    if (number.length() >= 10) {
                        number = number.substring(number.length() - 10);
                    }
                }

                if (currentEtFinMateName != null) currentEtFinMateName.setText(name);
                if (currentEtWhatsAppNo != null) currentEtWhatsAppNo.setText(number);

                Toast.makeText(this, "Contact Imported!", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Failed to read contact", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("SetTextI18n")
    private void showAddFinMateBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this) {
            @Override
            public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    View v = getCurrentFocus();
                    if (v instanceof android.widget.EditText) {
                        android.graphics.Rect outRect = new android.graphics.Rect();
                        v.getGlobalVisibleRect(outRect);
                        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                            v.clearFocus();
                            android.view.inputmethod.InputMethodManager imm =
                                    (android.view.inputmethod.InputMethodManager) getContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                            }
                        }
                    }
                }
                return super.dispatchTouchEvent(event);
            }
        };
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_finmate, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvSheetTitle);
        TextView btnImportContact = view.findViewById(R.id.btnImportContact);

        currentEtFinMateName = view.findViewById(R.id.etSheetFinMateName);
        currentEtWhatsAppNo = view.findViewById(R.id.etSheetContactNo);

        TextInputEditText etEmail = view.findViewById(R.id.etSheetEmail);
        TextInputEditText etAddress = view.findViewById(R.id.etSheetAddress);
        RadioGroup radioGroupWhatsApp = view.findViewById(R.id.radioGroupWhatsApp);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveFinMate);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        dialog.setOnShowListener(d -> {
            currentEtFinMateName.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(currentEtFinMateName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        });

        title.setText("Add FinMate");
        btnSave.setText("Save FinMate");

        btnImportContact.setOnClickListener(v -> {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                launchContactPicker();
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.READ_CONTACTS);
            }
        });

        btnSave.setOnClickListener(v -> {
            String name = String.valueOf(currentEtFinMateName.getText()).trim();
            String contactNo = String.valueOf(currentEtWhatsAppNo.getText()).trim();
            String email = String.valueOf(etEmail.getText()).trim();
            String address = String.valueOf(etAddress.getText()).trim();

            if (TextUtils.isEmpty(name)) {
                currentEtFinMateName.setError("Enter Name");
                return;
            }

            if (TextUtils.isEmpty(contactNo) || contactNo.length() < 10) {
                currentEtWhatsAppNo.setError("Enter valid 10-digit number");
                return;
            }

            int selectedId = radioGroupWhatsApp.getCheckedRadioButtonId();
            if (selectedId == -1) {
                Toast.makeText(this, "Please select if this number is on WhatsApp", Toast.LENGTH_SHORT).show();
                return;
            }

            boolean isWhatsApp = (selectedId == R.id.radioYes);
            String finalWhatsAppNo = isWhatsApp ? contactNo : "";

            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String newFinMateId = db.collection("Users").document(userId).collection("FinMates").document().getId();

            FinMate newFinMate = new FinMate(newFinMateId, name, contactNo, finalWhatsAppNo, email, address, System.currentTimeMillis());

            btnSave.setEnabled(false);
            btnSave.setText("Saving...");

            db.collection("Users").document(userId).collection("FinMates").document(newFinMateId)
                    .set(newFinMate)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "FinMate Saved Successfully!", Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } else {
                            String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                            Toast.makeText(this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
                            btnSave.setEnabled(true);
                            btnSave.setText("Save FinMate");
                        }
                    });
        });

        dialog.show();
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
                    if (error != null) {
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                        Toast.makeText(MainActivity.this, "Failed to load cards: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

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

                    if (tvValueCards != null) {
                        tvValueCards.setText(String.valueOf(totalCardsCount));
                    }

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
                    if (error != null) {
                        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                        Toast.makeText(MainActivity.this, "Failed to load FinMates: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }

                    finMateList.clear();
                    if (snapshot != null) {
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            FinMate finMate = doc.toObject(FinMate.class);
                            if (finMate != null) {
                                finMateList.add(finMate);
                            }
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

        FirebaseFirestore.getInstance()
                .collection("Users").document(currentUser.getUid()).collection("Transactions")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    double cumulativeCash = 0.0;
                    Map<String, Double> cardWiseDue = new HashMap<>();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx == null) continue;

                        String type = tx.getTransactionType();

                        // 2. Paybacks
                        if ("PAY_CREDIT".equals(type) && tx.getSplits() != null) {
                            double amt = tx.getTotalAmount();
                            String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";
                            if ("CASH".equals(cId)) {
                                cumulativeCash += amt;
                            } else {
                                cardWiseDue.merge(cId, amt, Double::sum);
                            }
                        }

                        // 3. Normal Spends
                        if (tx.getSplits() != null && tx.getSplits().containsKey("self")) {
                            Transaction.TransactionSplit mySplit = tx.getSplits().get("self");
                            if (mySplit != null) {
                                double amt = mySplit.getCombinedStealthAmount();
                                if (amt > 0.01) {
                                    String cId = tx.getCardId() != null ? tx.getCardId() : "CASH";
                                    if ("CASH_SPEND".equals(type) || "CASH".equals(cId)) {
                                        cumulativeCash += amt;
                                    } else if ("CARD_SPEND".equals(type)) {
                                        cardWiseDue.merge(cId, amt, Double::sum);
                                    }
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