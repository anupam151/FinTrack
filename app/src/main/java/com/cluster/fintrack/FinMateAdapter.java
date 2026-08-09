package com.cluster.fintrack;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

@SuppressWarnings({"unused", "SetTextI18n"})
public class FinMateAdapter extends RecyclerView.Adapter<FinMateAdapter.FinMateViewHolder> {

    private final Context context;
    private final List<FinMate> finMateList;
    private final OnFinMateLongClickListener longClickListener;

    public interface OnFinMateLongClickListener {
        void onLongClick(FinMate finMate, View anchor);
    }

    // Constructor with long click listener
    public FinMateAdapter(Context context, List<FinMate> finMateList, OnFinMateLongClickListener longClickListener) {
        this.context = context;
        this.finMateList = finMateList;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public FinMateViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_dashboard_finmate, parent, false);
        return new FinMateViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FinMateViewHolder holder, int position) {
        FinMate finmate = finMateList.get(position);

        // 1. Set Name and Mobile Number
        holder.tvFinmateName.setText(finmate.getName());
        holder.tvFinmateMobile.setText(finmate.getPhoneNo() != null && !finmate.getPhoneNo().isEmpty() ? finmate.getPhoneNo() : "");

        // 2. Set Avatar Initials
        holder.tvFinmateInitials.setText(getInitials(finmate.getName()));

        // 3. Dynamic Financial Calculations from FinMate Model
        double totalReceivable = finmate.getTotalReceivable();
        double payableAmount = finmate.getPayableAmount();

        // Net balance: Positive means Receivable, Negative means Payable
        double netBalance = totalReceivable - payableAmount;

        // 4. Format Currency Helper (using non-deprecated Locale builder)
        Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
        NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(2);

        if (netBalance >= 0) {
            // === RECEIVABLE VIEW ===
            holder.tvFinmateBalanceLabel.setText("Receivable");
            holder.tvFinmateBalanceAmount.setText(formatter.format(netBalance));
            holder.tvFinmateBalanceAmount.setTextColor(Color.parseColor("#388E3C")); // Green

            // Populate Sub-options for Credit Card and Cash
            holder.tvReceivableCardAmount.setText(formatter.format(finmate.getReceivableCardAmount()));
            holder.tvReceivableCashAmount.setText(formatter.format(finmate.getReceivableCashAmount()));
            holder.layoutReceivableSubOptions.setVisibility(View.VISIBLE);
        } else {
            // === PAYABLE VIEW ===
            holder.tvFinmateBalanceLabel.setText("Payable");
            holder.tvFinmateBalanceAmount.setText(formatter.format(Math.abs(netBalance)));
            holder.tvFinmateBalanceAmount.setTextColor(Color.parseColor("#D32F2F")); // Red

            // Hide sub-options for payables
            holder.layoutReceivableSubOptions.setVisibility(View.GONE);
        }

        // 5. WhatsApp Button Visibility and Action
        if (finmate.getWhatsappNo() == null || finmate.getWhatsappNo().isEmpty()) {
            holder.ivWhatsApp.setVisibility(View.GONE);
        } else {
            holder.ivWhatsApp.setVisibility(View.VISIBLE);

            holder.ivWhatsApp.setOnClickListener(v -> {
                String url = "https://api.whatsapp.com/send?phone=+91" + finmate.getWhatsappNo();
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                context.startActivity(intent);
            });
        }

        // 6. Standard item click listener
        holder.itemView.setOnClickListener(v -> Toast.makeText(context, "Clicked: " + finmate.getName(), Toast.LENGTH_SHORT).show());

        // 7. Long click listener for Edit/Delete menu
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(finmate, v);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return finMateList.size();
    }

    // --- HELPER LOGIC THAT TURNS "Anupam Das" INTO "AD" ---
    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] words = name.trim().split("\\s+");
        if (words.length == 1) {
            return String.valueOf(words[0].charAt(0)).toUpperCase();
        } else {
            return (String.valueOf(words[0].charAt(0)) + words[words.length - 1].charAt(0)).toUpperCase();
        }
    }

    public static class FinMateViewHolder extends RecyclerView.ViewHolder {
        TextView tvFinmateName, tvFinmateMobile, tvFinmateInitials;
        TextView tvFinmateBalanceLabel, tvFinmateBalanceAmount;
        TextView tvReceivableCardAmount, tvReceivableCashAmount;
        LinearLayout layoutReceivableSubOptions;
        ImageView ivWhatsApp;

        public FinMateViewHolder(@NonNull View itemView) {
            super(itemView);
            tvFinmateName = itemView.findViewById(R.id.tvFinmateName);
            tvFinmateMobile = itemView.findViewById(R.id.tvFinmateMobile);
            tvFinmateInitials = itemView.findViewById(R.id.tvFinmateInitials);

            tvFinmateBalanceLabel = itemView.findViewById(R.id.tvFinmateBalanceLabel);
            tvFinmateBalanceAmount = itemView.findViewById(R.id.tvFinmateBalanceAmount);

            tvReceivableCardAmount = itemView.findViewById(R.id.tvReceivableCardAmount);
            tvReceivableCashAmount = itemView.findViewById(R.id.tvReceivableCashAmount);
            layoutReceivableSubOptions = itemView.findViewById(R.id.layoutReceivableSubOptions);

            ivWhatsApp = itemView.findViewById(R.id.ivWhatsApp);
        }
    }
}