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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    private final Context context;
    private final List<Card> cardList;
    private final OnCardLongClickListener longClickListener;

    public interface OnCardLongClickListener {
        void onLongClick(Card card, View anchor);
    }

    public CardAdapter(Context context, List<Card> cardList) {
        this.context = context;
        this.cardList = cardList;
        this.longClickListener = null;
    }

    public CardAdapter(Context context, List<Card> cardList, OnCardLongClickListener longClickListener) {
        this.context = context;
        this.cardList = cardList;
        this.longClickListener = longClickListener;
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

        // 1. Set Card Name & Bank Initials
        holder.tvCardName.setText(card.getCardName());
        holder.tvCardLogoText.setText(getBankInitials(card.getBankName()));

        // 2. Calculate Financials (Used is hardcoded to 0 for now)
        double totalLimit = card.getTotalLimit();
        double usedLimit = 0.0;
        double availableLimit = totalLimit - usedLimit;

        // 3. Set Financial Values to the new TextViews
        holder.tvCardLimit.setText(String.format(Locale.getDefault(), "₹%.0f", totalLimit));
        holder.tvCardUsed.setText(String.format(Locale.getDefault(), "₹%.0f", usedLimit));
        holder.tvCardAvailable.setText(String.format(Locale.getDefault(), "₹%.0f", availableLimit));
        holder.tvCardDue.setText("₹0"); // Hardcoded for now

        // 4. Set Due Date with proper ordinal suffix (e.g., 1st, 2nd, 3rd, 21st, etc.)
        int billingDay = card.getBillingDay();
        String suffix = getOrdinalSuffix(billingDay);
        String dueText = String.format(Locale.getDefault(), "%d%s of month", billingDay, suffix);
        holder.tvCardDueDate.setText(dueText);

        // 5. Circular Progress Indicator (0% for now)
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

        // 7. Standard Click listener
        holder.itemView.setOnClickListener(v -> Toast.makeText(context, "Clicked: " + card.getCardName(), Toast.LENGTH_SHORT).show());

        // 8. Long Click listener
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(card, v);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    // Helper method to generate correct English ordinal suffixes (st, nd, rd, th)
    private String getOrdinalSuffix(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1:  return "st";
            case 2:  return "nd";
            case 3:  return "rd";
            default: return "th";
        }
    }

    private String getBankInitials(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) return "BANK";

        Map<String, String> shortNameMap = new HashMap<>();
        shortNameMap.put("AU Small Finance Bank", "AUSFB");
        shortNameMap.put("American Express", "AMEX");
        shortNameMap.put("Axis Bank", "AXIS");
        shortNameMap.put("Bandhan Bank", "BANDHAN");
        shortNameMap.put("Bank of Baroda", "BOB");
        shortNameMap.put("Bank of India", "BOI");
        shortNameMap.put("Bank of Maharashtra", "BOM");
        shortNameMap.put("Barclays Bank", "BARB");
        shortNameMap.put("Baroda Gujarat Gramin Bank", "BGGB");
        shortNameMap.put("Baroda Rajasthan Kshetriya Gramin Bank", "BRKGB");
        shortNameMap.put("Baroda U.P. Bank", "BUPB");
        shortNameMap.put("CSB Bank", "CSB");
        shortNameMap.put("Canara Bank", "CAN");
        shortNameMap.put("Capital Small Finance Bank", "CSFB");
        shortNameMap.put("Central Bank of India", "CBI");
        shortNameMap.put("City Union Bank", "CUB");
        shortNameMap.put("Cosmos Co-operative Bank", "CCB");
        shortNameMap.put("DBS Bank", "DBS");
        shortNameMap.put("DCB Bank", "DCB");
        shortNameMap.put("Deutsche Bank", "DB");
        shortNameMap.put("Dhanlaxmi Bank", "DLB");
        shortNameMap.put("ESAF Small Finance Bank", "ESFB");
        shortNameMap.put("Equitas Small Finance Bank", "ESFB");
        shortNameMap.put("Federal Bank", "FED");
        shortNameMap.put("First Abu Dhabi Bank", "FAB");
        shortNameMap.put("HDFC Bank", "HDFC");
        shortNameMap.put("HSBC Bank", "HSBC");
        shortNameMap.put("ICICI Bank Limited", "ICICI");
        shortNameMap.put("IDFC FIRST Bank", "IDFC");
        shortNameMap.put("Indian Bank", "IB");
        shortNameMap.put("Indian Overseas Bank", "IOB");
        shortNameMap.put("IndusInd Bank", "IND");
        shortNameMap.put("Jammu & Kashmir Bank", "J&K");
        shortNameMap.put("Jana Small Finance Bank", "JSFB");
        shortNameMap.put("Karnataka Bank", "KBL");
        shortNameMap.put("Karur Vysya Bank", "KVB");
        shortNameMap.put("Kerala Gramin Bank", "KGB");
        shortNameMap.put("Kotak Mahindra Bank", "KOTAK");
        shortNameMap.put("Nainital Bank", "NB");
        shortNameMap.put("Punjab & Sind Bank", "PSB");
        shortNameMap.put("Punjab National Bank", "PNB");
        shortNameMap.put("RBL Bank", "RBL");
        shortNameMap.put("SBM Bank India", "SBM");
        shortNameMap.put("SVC Co-operative Bank", "SVC");
        shortNameMap.put("Saraswat Co-operative Bank", "SCB");
        shortNameMap.put("South Indian Bank", "SIB");
        shortNameMap.put("Standard Chartered Bank", "SCB");
        shortNameMap.put("State Bank of India", "SBI");
        shortNameMap.put("Suryoday Small Finance Bank", "SSFB");
        shortNameMap.put("Tamilnad Mercantile Bank", "TMB");
        shortNameMap.put("UCO Bank", "UCO");
        shortNameMap.put("Ujjivan Small Finance Bank", "USFB");
        shortNameMap.put("Union Bank of India", "UBI");
        shortNameMap.put("Utkarsh Small Finance Bank", "USFB");
        shortNameMap.put("YES Bank", "YES");

        if (shortNameMap.containsKey(bankName)) {
            return shortNameMap.get(bankName);
        }

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
        androidx.cardview.widget.CardView cardLogoContainer;

        TextView tvCardName, tvCardLogoText, tvCardProgressPercent;
        TextView tvCardLimit, tvCardDue, tvCardUsed, tvCardAvailable, tvCardDueDate;

        CircularProgressIndicator cpiCardProgress;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardLogoContainer = itemView.findViewById(R.id.cardLogoContainer);
            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardLogoText = itemView.findViewById(R.id.tvCardLogoText);

            tvCardLimit = itemView.findViewById(R.id.tvCardLimit);
            tvCardDue = itemView.findViewById(R.id.tvCardDue);
            tvCardUsed = itemView.findViewById(R.id.tvCardUsed);
            tvCardAvailable = itemView.findViewById(R.id.tvCardAvailable);
            tvCardDueDate = itemView.findViewById(R.id.tvCardDueDate);

            tvCardProgressPercent = itemView.findViewById(R.id.tvCardProgressPercent);
            cpiCardProgress = itemView.findViewById(R.id.cpiCardProgress);
        }
    }
}