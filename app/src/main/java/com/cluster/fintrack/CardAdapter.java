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
    private final OnCardLongClickListener longClickListener; // NEW: Holds the long-click listener

    // --- NEW: Interface for Long Click events ---
    public interface OnCardLongClickListener {
        void onLongClick(Card card, View anchor);
    }

    // Constructor for Activities that DO NOT need long-click (like MainActivity)
    public CardAdapter(Context context, List<Card> cardList) {
        this.context = context;
        this.cardList = cardList;
        this.longClickListener = null;
    }

    // --- NEW: Constructor for Activities that DO need long-click (like CardsActivity) ---
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

        // 1. Set Card Name
        holder.tvCardName.setText(card.getCardName());

        // 2. Generate Bank Logo Initials
        holder.tvCardLogoText.setText(getBankInitials(card.getBankName()));

        // 3. Set Limit & Used
        String limitUsedText = String.format(Locale.getDefault(), "Limit: ₹%.0f  |  Used: ₹0", card.getTotalLimit());
        holder.tvCardLimitUsed.setText(limitUsedText);

        // 4. Set Due Date
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

        // 7. Standard Click listener
        holder.itemView.setOnClickListener(v -> Toast.makeText(context, "Clicked: " + card.getCardName(), Toast.LENGTH_SHORT).show());

        // --- NEW: Long Click listener attached here ---
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onLongClick(card, v);
                return true; // Return true to indicate the long click was handled
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    private String getBankInitials(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) return "BANK";

        // Map containing exact short names from your Excel file
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

        // If the bank is in the exact list, return its short name
        if (shortNameMap.containsKey(bankName)) {
            return shortNameMap.get(bankName);
        }

        // Fallback safety logic for manual user input
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