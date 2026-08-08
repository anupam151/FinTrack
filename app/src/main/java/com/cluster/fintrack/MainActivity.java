package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // UI References for Bottom Sheet so the Contact Picker can update them
    private TextInputEditText currentEtFinMateName;
    private TextInputEditText currentEtWhatsAppNo;

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

        // Call our real-time Firebase FinMates loader!
        loadFinMatesFromFirebase();

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
    private void showAddCardBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_add_edit_card, findViewById(android.R.id.content), false);
        dialog.setContentView(view);

        TextView title = view.findViewById(R.id.tvCardSheetTitle);
        com.google.android.material.textfield.MaterialAutoCompleteTextView spinBankName = view.findViewById(R.id.spinSheetBankName);
        TextInputEditText etCardName = view.findViewById(R.id.etSheetCardName);
        TextInputEditText etTotalLimit = view.findViewById(R.id.etSheetTotalLimit);
        TextInputEditText etBillingDay = view.findViewById(R.id.etSheetBillingDay);

        com.google.android.material.card.MaterialCardView cardColorPreview = view.findViewById(R.id.cardColorPreview);
        MaterialButton btnPickColor = view.findViewById(R.id.btnPickColor);
        TextView btnResetColor = view.findViewById(R.id.btnResetColor);
        MaterialButton btnSave = view.findViewById(R.id.btnSheetSaveCard);

        title.setText("Add Credit Card");
        btnSave.setText("Save Card");

        final int[] currentColor = {android.graphics.Color.parseColor("#082561")};
        final int defaultColor = android.graphics.Color.parseColor("#082561");

        btnPickColor.setOnClickListener(v -> {
            yuku.ambilwarna.AmbilWarnaDialog colorPickerDialog = new yuku.ambilwarna.AmbilWarnaDialog(this, currentColor[0], new yuku.ambilwarna.AmbilWarnaDialog.OnAmbilWarnaListener() {
                @Override
                public void onCancel(yuku.ambilwarna.AmbilWarnaDialog dialog) {}

                @Override
                public void onOk(yuku.ambilwarna.AmbilWarnaDialog dialog, int color) {
                    currentColor[0] = color;
                    cardColorPreview.setCardBackgroundColor(color);
                }
            });
            colorPickerDialog.show();
        });

        btnResetColor.setOnClickListener(v -> {
            currentColor[0] = defaultColor;
            cardColorPreview.setCardBackgroundColor(defaultColor);
        });

        btnSave.setOnClickListener(v -> {
            String bankName = String.valueOf(spinBankName.getText()).trim();
            String cardName = String.valueOf(etCardName.getText()).trim();
            String limitStr = String.valueOf(etTotalLimit.getText()).trim();
            String billingDayStr = String.valueOf(etBillingDay.getText()).trim();

            if (TextUtils.isEmpty(bankName)) { spinBankName.setError("Required"); return; }
            if (TextUtils.isEmpty(cardName)) { etCardName.setError("Required"); return; }
            if (TextUtils.isEmpty(limitStr)) { etTotalLimit.setError("Required"); return; }
            if (TextUtils.isEmpty(billingDayStr)) { etBillingDay.setError("Required"); return; }

            double totalLimit = Double.parseDouble(limitStr);
            int billingDay = Integer.parseInt(billingDayStr);

            String themeColorHex = String.format("#%06X", (0xFFFFFF & currentColor[0]));

            if (FirebaseAuth.getInstance().getCurrentUser() == null) {
                Toast.makeText(this, "Error: User not logged in!", Toast.LENGTH_SHORT).show();
                return;
            }

            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
            DatabaseReference cardsRef = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("Cards");

            String newCardId = cardsRef.push().getKey();

            Card newCard = new Card(newCardId, bankName, cardName, totalLimit, billingDay, themeColorHex, System.currentTimeMillis());

            if (newCardId != null) {
                btnSave.setEnabled(false);
                btnSave.setText("Saving...");

                cardsRef.child(newCardId).setValue(newCard).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Card Saved Successfully!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        String errorMessage = task.getException() != null ? task.getException().getMessage() : "Unknown error occurred";
                        Toast.makeText(this, "Failed to save: " + errorMessage, Toast.LENGTH_SHORT).show();
                        btnSave.setEnabled(true);
                        btnSave.setText("Save Card");
                    }
                });
            }
        });

        dialog.show();
    }

    @SuppressLint("SetTextI18n")
    private void showAddFinMateBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
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
            DatabaseReference finMatesRef = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("FinMates");

            String newFinMateId = finMatesRef.push().getKey();

            FinMate newFinMate = new FinMate(newFinMateId, name, contactNo, finalWhatsAppNo, email, address, System.currentTimeMillis());

            if (newFinMateId != null) {
                btnSave.setEnabled(false);
                btnSave.setText("Saving...");

                finMatesRef.child(newFinMateId).setValue(newFinMate).addOnCompleteListener(task -> {
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
            }
        });

        dialog.show();
    }

    private void loadFinMatesFromFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) return;

        String userId = currentUser.getUid();
        DatabaseReference finMatesRef = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("FinMates");

        LinearLayout layoutEmptyFinMates = findViewById(R.id.layoutEmptyFinMates);
        RecyclerView recyclerViewFinMates = findViewById(R.id.recyclerViewFinMates);

        recyclerViewFinMates.setLayoutManager(new LinearLayoutManager(this));

        List<FinMate> finMateList = new ArrayList<>();
        FinMateAdapter adapter = new FinMateAdapter(this, finMateList);
        recyclerViewFinMates.setAdapter(adapter);

        finMatesRef.addValueEventListener(new ValueEventListener() {
            @SuppressLint("NotifyDataSetChanged")
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                finMateList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    FinMate finMate = dataSnapshot.getValue(FinMate.class);
                    if (finMate != null) {
                        finMateList.add(finMate);
                    }
                }

                if (finMateList.isEmpty()) {
                    layoutEmptyFinMates.setVisibility(View.VISIBLE);
                    recyclerViewFinMates.setVisibility(View.GONE);
                } else {
                    layoutEmptyFinMates.setVisibility(View.GONE);
                    recyclerViewFinMates.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Failed to load FinMates: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private int getSimulatedTotalCards() {
        return 0;
    }
}