package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("FieldCanBeLocal")
@SuppressLint("SetTextI18n")
public class CardActiveEmisActivity extends AppCompatActivity {

    private final List<Transaction> emiList = new ArrayList<>();
    private EmiListAdapter adapter;

    private final NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_card_active_emis);

        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainCardActiveEmis), (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        String cardId = getIntent().getStringExtra("CARD_ID");
        String cardName = getIntent().getStringExtra("CARD_NAME");

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());
        TextView tvHeaderTitle = findViewById(R.id.tvHeaderTitle);

        if (cardId != null) {
            tvHeaderTitle.setText("EMIs: " + (cardName != null ? cardName : "Card"));
            fetchActiveEmisForCard(cardId);
        } else {
            // Global view when opened from Navigation Drawer!
            tvHeaderTitle.setText("All Active EMIs");
            fetchAllActiveEmisGlobal();
        }

        RecyclerView recyclerActiveEmis = findViewById(R.id.recyclerActiveEmis);
        recyclerActiveEmis.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EmiListAdapter(emiList, currencyFormatter);
        recyclerActiveEmis.setAdapter(adapter);
    }

    private void fetchActiveEmisForCard(String cardId) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(user.getUid())
                .collection("Transactions")
                .whereEqualTo("cardId", cardId)
                .whereEqualTo("transactionType", "EMI_MASTER")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    emiList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx != null) {
                            emiList.add(tx);
                        }
                    }
                    adapter.notifyItemRangeChanged(0, emiList.size());
                });
    }

    private void fetchAllActiveEmisGlobal() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) return;

        FirebaseFirestore.getInstance().collection("Users").document(user.getUid())
                .collection("Transactions")
                .whereEqualTo("transactionType", "EMI_MASTER")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) return;

                    emiList.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Transaction tx = doc.toObject(Transaction.class);
                        if (tx != null) {
                            emiList.add(tx);
                        }
                    }
                    adapter.notifyItemRangeChanged(0, emiList.size());
                });
    }

    public static class EmiListAdapter extends RecyclerView.Adapter<EmiListAdapter.ViewHolder> {
        private final List<Transaction> emis;
        private final NumberFormat formatter;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

        public EmiListAdapter(List<Transaction> emis, NumberFormat formatter) {
            this.emis = emis;
            this.formatter = formatter;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_active_emi, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Transaction tx = emis.get(position);
            holder.tvEmiTitle.setText(tx.getTitle());

            double totalPrincipal = tx.getTotalAmount();
            double remainingPrincipal = 0.0;
            int totalMonths = 0;
            int paidMonths = 0;

            if (tx.getEmiData() != null) {
                Transaction.EmiData data = tx.getEmiData();
                remainingPrincipal = data.getRemainingEmiPrincipal();
                if (data.getAmortizationSchedule() != null) {
                    totalMonths = data.getAmortizationSchedule().size();
                    for (Transaction.EmiMonth month : data.getAmortizationSchedule()) {
                        if (month.isBilled() || month.isCancelled()) {
                            paidMonths++;
                        }
                    }
                }
            }

            holder.tvTotalPrincipal.setText(formatter.format(totalPrincipal));
            holder.tvRemainingPrincipal.setText(formatter.format(remainingPrincipal));
            holder.tvProgressBadge.setText(paidMonths + " / " + totalMonths + " Paid");

            // Start Date
            String startDateStr = dateFormat.format(new Date(tx.getTimestamp()));
            holder.tvStartDate.setText("Started: " + startDateStr);

            // Estimate Finish Date (Adding months to start timestamp)
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(tx.getTimestamp());
            cal.add(Calendar.MONTH, totalMonths);
            String finishDateStr = dateFormat.format(cal.getTime());
            holder.tvFinishDate.setText("Ends: " + finishDateStr);

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(v.getContext(), EmiDetailsActivity.class);
                intent.putExtra("TRANSACTION_ID", tx.getTransactionId());
                v.getContext().startActivity(intent);
            });
        }

        @Override public int getItemCount() { return emis.size(); }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvEmiTitle, tvProgressBadge, tvTotalPrincipal, tvRemainingPrincipal, tvStartDate, tvFinishDate;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvEmiTitle = itemView.findViewById(R.id.tvEmiTitle);
                tvProgressBadge = itemView.findViewById(R.id.tvProgressBadge);
                tvTotalPrincipal = itemView.findViewById(R.id.tvTotalPrincipal);
                tvRemainingPrincipal = itemView.findViewById(R.id.tvRemainingPrincipal);
                tvStartDate = itemView.findViewById(R.id.tvStartDate);
                tvFinishDate = itemView.findViewById(R.id.tvFinishDate);
            }
        }
    }
}