package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.graphics.Insets;
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
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("deprecation")
public class FinMatesActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;

    // UI Elements
    private RecyclerView recyclerViewFinMates;
    private TextView tvEmptyFinMatesList;
    private SwipeRefreshLayout swipeRefreshFinMates;
    private TextInputEditText etSearchFinMate;
    private ImageView ivSortFinMates;
    private ImageView ivSortOrder;

    // Adapter & Data Lists
    private FinMateAdapter finMateAdapter;
    private final List<FinMate> masterFinMateList = new ArrayList<>();
    private final List<FinMate> displayedFinMateList = new ArrayList<>();

    // Sorting State (0 = Recent, 1 = Name, 2 = Receivable, 3 = Payable)
    private int currentSortMode = 0;
    private boolean isAscending = false;

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
        navigationView.setItemIconTintList(null);

        ViewCompat.setOnApplyWindowInsetsListener(navigationView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return windowInsets;
        });

        ImageButton btnAddTransactionHeader = findViewById(R.id.btnAddTransactionHeader);
        if (btnAddTransactionHeader != null) {
            btnAddTransactionHeader.setOnClickListener(v -> startActivity(new Intent(FinMatesActivity.this, AddTransactionActivity.class)));
        }

        ImageView ivMenuDrawerFinMates = findViewById(R.id.ivMenuDrawerFinMates);
        ivMenuDrawerFinMates.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            drawerLayout.closeDrawer(GravityCompat.START);

            drawerLayout.postDelayed(() -> {
                if (id == R.id.nav_drawer_dashboard) {
                    Intent intent = new Intent(FinMatesActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    disableWindowAnimations();
                    finish();
                } else if (id == R.id.nav_drawer_cards) {
                    Intent intent = new Intent(FinMatesActivity.this, CardsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                    startActivity(intent);
                    disableWindowAnimations();
                    finish();
                } else if (id == R.id.nav_drawer_ledger) {
                    Toast.makeText(FinMatesActivity.this, "Already on FinMates", Toast.LENGTH_SHORT).show();
                } else if (id == R.id.nav_drawer_signout) {
                    FirebaseAuth.getInstance().signOut();
                    GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                            .requestIdToken(getString(R.string.default_web_client_id))
                            .requestEmail()
                            .build();

                    GoogleSignIn.getClient(FinMatesActivity.this, gso).signOut().addOnCompleteListener(task -> {
                        Toast.makeText(FinMatesActivity.this, "Signed out successfully", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(FinMatesActivity.this, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    });
                }
            }, 250);
            return false;
        });

        recyclerViewFinMates = findViewById(R.id.recyclerViewFinMates);
        tvEmptyFinMatesList = findViewById(R.id.tvEmptyFinMatesList);
        swipeRefreshFinMates = findViewById(R.id.swipeRefreshFinMates);
        etSearchFinMate = findViewById(R.id.etSearchFinMate);
        ivSortFinMates = findViewById(R.id.ivSortFinMates);
        ivSortOrder = findViewById(R.id.ivSortOrderFinMates);

        recyclerViewFinMates.setLayoutManager(new LinearLayoutManager(this));
        finMateAdapter = new FinMateAdapter(this, displayedFinMateList, this::showEditDeleteMenu);
        recyclerViewFinMates.setAdapter(finMateAdapter);

        swipeRefreshFinMates.setOnRefreshListener(this::fetchFinMatesFromFirestore);
        setupSearchAndSort();

        // Bottom Navigation Listeners
        findViewById(R.id.navItemDashboard).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            disableWindowAnimations();
            finish();
        });

        findViewById(R.id.navItemCards).setOnClickListener(v -> {
            Intent intent = new Intent(this, CardsActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            disableWindowAnimations();
            finish();
        });

        findViewById(R.id.navSetings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));

        // THE FIX: Launch Intent instead of BottomSheet
        findViewById(R.id.fabAddFinMate).setOnClickListener(v -> startActivity(new Intent(this, AddFinMateActivity.class)));

        fetchFinMatesFromFirestore();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            View v = getCurrentFocus();
            if (v != null && v.getId() == R.id.etSearchFinMate) {
                android.graphics.Rect outRect = new android.graphics.Rect();
                v.getGlobalVisibleRect(outRect);
                if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
                    v.clearFocus();
                    if (getWindow() != null) {
                        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), v);
                        controller.hide(WindowInsetsCompat.Type.ime());
                    }
                }
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void disableWindowAnimations() {
        if (android.os.Build.VERSION.SDK_INT >= 34) overrideActivityTransition(android.app.Activity.OVERRIDE_TRANSITION_OPEN, 0, 0);
        else overridePendingTransition(0, 0);
    }

    // Refresh UI automatically when returning from AddFinMateActivity
    @Override
    protected void onResume() {
        super.onResume();
        fetchFinMatesFromFirestore();
    }

    private void setupSearchAndSort() {
        if (etSearchFinMate == null || ivSortFinMates == null || ivSortOrder == null) return;

        etSearchFinMate.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterFinMates(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        ivSortFinMates.setOnClickListener(v -> {
            androidx.appcompat.view.ContextThemeWrapper wrapper = new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);
            PopupMenu popup = new PopupMenu(wrapper, v);
            popup.getMenu().add(0, 0, 0, "Recent Activity");
            popup.getMenu().add(0, 1, 0, "Name (A-Z)");
            popup.getMenu().add(0, 2, 0, "Total Receivable");
            popup.getMenu().add(0, 3, 0, "Total Payable");

            popup.setOnMenuItemClickListener(item -> {
                currentSortMode = item.getItemId();
                filterFinMates(etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
                return true;
            });
            popup.show();
        });

        ivSortOrder.setOnClickListener(v -> {
            isAscending = !isAscending;
            ivSortOrder.animate().rotation(isAscending ? 180f : 0f).setDuration(200).start();
            filterFinMates(etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
        });
    }

    @SuppressLint("NotifyDataSetChanged")
    private void filterFinMates(String query) {
        List<FinMate> tempFilteredList = new ArrayList<>();

        if (TextUtils.isEmpty(query)) tempFilteredList.addAll(masterFinMateList);
        else {
            String lowerCaseQuery = query.toLowerCase();
            for (FinMate finMate : masterFinMateList) {
                if (finMate.getName().toLowerCase().contains(lowerCaseQuery) || finMate.getPhoneNo().contains(lowerCaseQuery)) {
                    tempFilteredList.add(finMate);
                }
            }
        }

        if (currentSortMode == 0) {
            tempFilteredList.sort((f1, f2) -> isAscending ? Long.compare(f1.getTimestamp(), f2.getTimestamp()) : Long.compare(f2.getTimestamp(), f1.getTimestamp()));
        } else if (currentSortMode == 1) {
            tempFilteredList.sort((f1, f2) -> isAscending ? f1.getName().compareToIgnoreCase(f2.getName()) : f2.getName().compareToIgnoreCase(f1.getName()));
        } else if (currentSortMode == 2) {
            tempFilteredList.sort((f1, f2) -> {
                double r1 = f1.getReceivableCardAmount() + f1.getReceivableCashAmount();
                double r2 = f2.getReceivableCardAmount() + f2.getReceivableCashAmount();
                return isAscending ? Double.compare(r1, r2) : Double.compare(r2, r1);
            });
        } else if (currentSortMode == 3) {
            tempFilteredList.sort((f1, f2) -> isAscending ? Double.compare(f1.getPayableAmount(), f2.getPayableAmount()) : Double.compare(f2.getPayableAmount(), f1.getPayableAmount()));
        }

        displayedFinMateList.clear();
        displayedFinMateList.addAll(tempFilteredList);
        finMateAdapter.notifyDataSetChanged();

        if (displayedFinMateList.isEmpty()) {
            tvEmptyFinMatesList.setVisibility(View.VISIBLE);
            recyclerViewFinMates.setVisibility(View.GONE);
        } else {
            tvEmptyFinMatesList.setVisibility(View.GONE);
            recyclerViewFinMates.setVisibility(View.VISIBLE);
        }
    }

    private void fetchFinMatesFromFirestore() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            swipeRefreshFinMates.setRefreshing(false);
            return;
        }

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        swipeRefreshFinMates.setRefreshing(true);

        FirebaseFirestore.getInstance().collection("Users").document(userId).collection("FinMates")
                .orderBy("timestamp", Query.Direction.DESCENDING).get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    masterFinMateList.clear();
                    if (!queryDocumentSnapshots.isEmpty()) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots) {
                            FinMate finMate = doc.toObject(FinMate.class);
                            if (finMate != null) masterFinMateList.add(finMate);
                        }
                    }
                    filterFinMates(etSearchFinMate != null && etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
                    swipeRefreshFinMates.setRefreshing(false);
                })
                .addOnFailureListener(e -> {
                    swipeRefreshFinMates.setRefreshing(false);
                    if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
                    Toast.makeText(this, "Failed to load FinMates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showEditDeleteMenu(FinMate finMate, View anchor) {
        androidx.appcompat.view.ContextThemeWrapper wrapper = new androidx.appcompat.view.ContextThemeWrapper(this, R.style.CleanPopupMenuTheme);
        androidx.appcompat.widget.PopupMenu popup = new androidx.appcompat.widget.PopupMenu(wrapper, anchor, android.view.Gravity.END);

        popup.getMenu().add(0, 0, 0, "Edit FinMate");
        popup.getMenu().add(0, 1, 0, "Delete FinMate");
        popup.setForceShowIcon(true);

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 0) {
                // THE FIX: Launch AddFinMateActivity in EDIT MODE via Intent
                Intent intent = new Intent(this, AddFinMateActivity.class);
                intent.putExtra("FINMATE_ID", finMate.getFinMateId());
                intent.putExtra("NAME", finMate.getName());
                intent.putExtra("PHONE_NO", finMate.getPhoneNo());
                intent.putExtra("WHATSAPP_NO", finMate.getWhatsappNo());
                intent.putExtra("EMAIL", finMate.getEmail());
                intent.putExtra("ADDRESS", finMate.getAddress());
                intent.putExtra("TIMESTAMP", finMate.getTimestamp());
                // Pass financial metrics to prevent data loss on update
                intent.putExtra("REC_CARD", finMate.getReceivableCardAmount());
                intent.putExtra("REC_CASH", finMate.getReceivableCashAmount());
                intent.putExtra("PAYABLE", finMate.getPayableAmount());
                startActivity(intent);
            } else if (item.getItemId() == 1) {
                showDeleteConfirmationDialog(finMate);
            }
            return true;
        });
        popup.show();
    }

    private void showDeleteConfirmationDialog(FinMate finMate) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Delete FinMate");
        builder.setMessage("Type '" + finMate.getName() + "' to permanently delete this FinMate. This action cannot be undone.");

        android.graphics.drawable.GradientDrawable dialogBackground = new android.graphics.drawable.GradientDrawable();
        dialogBackground.setColor(android.graphics.Color.WHITE);
        dialogBackground.setCornerRadius(48f);
        builder.setBackground(dialogBackground);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, (int) (8 * getResources().getDisplayMetrics().density), padding, 0);

        com.google.android.material.textfield.TextInputLayout textInputLayout = new com.google.android.material.textfield.TextInputLayout(this);
        textInputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        textInputLayout.setBoxCornerRadii(16f, 16f, 16f, 16f);
        textInputLayout.setHint("Enter FinMate name");
        textInputLayout.setErrorEnabled(true);

        com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(textInputLayout.getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setTextColor(android.graphics.Color.parseColor("#082561"));

        textInputLayout.addView(input);
        container.addView(textInputLayout);
        builder.setView(container);

        builder.setPositiveButton("Delete", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        androidx.appcompat.app.AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                androidx.core.view.WindowInsetsControllerCompat controller = new androidx.core.view.WindowInsetsControllerCompat(dialog.getWindow(), input);
                controller.show(androidx.core.view.WindowInsetsCompat.Type.ime());
            }

            android.widget.Button positiveButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
            positiveButton.setTextColor(android.graphics.Color.parseColor("#D32F2F"));
            android.widget.Button negativeButton = dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE);
            negativeButton.setTextColor(android.graphics.Color.parseColor("#667085"));

            positiveButton.setOnClickListener(v -> {
                String typedText = input.getText() != null ? input.getText().toString().trim() : "";
                if (typedText.equals(finMate.getName())) {
                    deleteFinMate(finMate);
                    dialog.dismiss();
                } else {
                    textInputLayout.setError("Name does not match.");
                }
            });

            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) { textInputLayout.setError(null); }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
        });
        dialog.show();
    }

    private void deleteFinMate(FinMate finMate) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance().collection("Users").document(userId).collection("FinMates").document(finMate.getFinMateId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    masterFinMateList.remove(finMate);
                    filterFinMates(etSearchFinMate != null && etSearchFinMate.getText() != null ? etSearchFinMate.getText().toString() : "");
                    Toast.makeText(this, "FinMate Deleted", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}