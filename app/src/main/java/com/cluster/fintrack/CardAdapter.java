package com.cluster.fintrack;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;
import java.util.Locale;

@SuppressWarnings("unused")
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    private final Context context;
    private final List<Card> cardList;

    public CardAdapter(Context context, List<Card> cardList) {
        this.context = context;
        this.cardList = cardList;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_dashboard_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        Card card = cardList.get(position);

        // 1. Set Card Name
        holder.tvCardName.setText(card.getCardName());

        // 2. Generate Bank Logo Initials
        holder.tvCardLogoText.setText(getBankInitials(card.getBankName()));

        // 3. Set Limit & Used using Locale.getDefault() to fix the warning
        String limitUsedText = String.format(Locale.getDefault(), "Limit: ₹%.0f  |  Used: ₹0", card.getTotalLimit());
        holder.tvCardLimitUsed.setText(limitUsedText);

        // 4. Set Due Date using Locale.getDefault()
        String dueText = String.format(Locale.getDefault(), "Billing Day: %dth of month", card.getBillingDay());
        holder.tvCardDue.setText(dueText);

        // 5. Circular Progress Indicator
        holder.cpiCardProgress.setProgress(0);
        holder.tvCardProgressPercent.setText("0%\nUsed");

        // 6. Apply custom theme color
        try {
            if (card.getThemeColor() != null && !card.getThemeColor().isEmpty()) {
                int parsedColor = Color.parseColor(card.getThemeColor());
                holder.cardLogoContainer.setCardBackgroundColor(parsedColor);
            }
        } catch (Exception e) {
            holder.cardLogoContainer.setCardBackgroundColor(Color.parseColor("#082561"));
        }

        // 7. Click listener
        holder.itemView.setOnClickListener(v -> Toast.makeText(context, "Clicked: " + card.getCardName(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    private String getBankInitials(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) return "BANK";
        String[] words = bankName.trim().split("\\s+");
        if (words.length == 1) {
            return words[0].length() > 4 ? words[0].substring(0, 4).toUpperCase() : words[0].toUpperCase();
        } else {
            StringBuilder initials = new StringBuilder();
            for (String word : words) {
                if (!word.isEmpty()) initials.append(word.charAt(0));
            }
            return initials.toString().toUpperCase();
        }
    }

    public static class CardViewHolder extends RecyclerView.ViewHolder {
        com.google.android.material.card.MaterialCardView cardLogoContainer;
        TextView tvCardName, tvCardLimitUsed, tvCardDue, tvCardLogoText, tvCardProgressPercent;
        CircularProgressIndicator cpiCardProgress;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardLogoContainer = itemView.findViewById(R.id.cardLogoContainer);
            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardLimitUsed = itemView.findViewById(R.id.tvCardLimitUsed);
            tvCardDue = itemView.findViewById(R.id.tvCardDue);
            tvCardLogoText = itemView.findViewById(R.id.tvCardLogoText);
            tvCardProgressPercent = itemView.findViewById(R.id.tvCardProgressPercent);
            cpiCardProgress = itemView.findViewById(R.id.cpiCardProgress);
        }
    }
}