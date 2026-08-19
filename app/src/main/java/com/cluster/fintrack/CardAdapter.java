package com.cluster.fintrack;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unused")
@SuppressLint("SetTextI18n") // Silences string concatenation warnings
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

        // Cancel any lingering listeners on recycled views to prevent memory leaks
        holder.cancelListener();

        // 1. Set Static Core Identity
        holder.tvCardName.setText(card.getCardName());
        holder.tvCardLogoText.setText(getBankInitials(card.getBankName()));
        holder.tvBankName.setText(card.getBankName());
        holder.tvLast4Digits.setText(String.format(Locale.getDefault(), "• %s", card.getLast4Digits()));
        holder.tvCardType.setText(card.getCardType());
        holder.tvCardLimit.setText(formatCurrency(card.getTotalLimit()));

        // Set Due Date format
        int billingDay = card.getBillingDay();
        String suffix = getOrdinalSuffix(billingDay);
        String dueText = String.format(Locale.getDefault(), "%d%s of month", billingDay, suffix);
        holder.tvCardDueDate.setText(dueText);

        // Apply custom theme color
        try {
            if (card.getThemeColor() != null && !card.getThemeColor().isEmpty()) {
                int parsedColor = Color.parseColor(card.getThemeColor());
                holder.cardLogoContainer.setCardBackgroundColor(parsedColor);
            }
        } catch (Exception e) {
            holder.cardLogoContainer.setCardBackgroundColor(Color.parseColor("#082561"));
        }

        // --- THE FIX: FETCH LIVE DATA FOR EACH CARD IN REAL-TIME ---
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            holder.listenerRegistration = FirebaseFirestore.getInstance()
                    .collection("Users").document(user.getUid())
                    .collection("Transactions")
                    .whereEqualTo("cardId", card.getCardId())
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) return;

                        double billedSpends = 0.0;
                        double unbilledSpends = 0.0;
                        double totalPayments = 0.0;
                        double unbilledCashback = 0.0;

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null) {
                                if ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                                    if (tx.isBilled()) {
                                        billedSpends += tx.getTotalAmount();
                                    } else {
                                        unbilledSpends += tx.getTotalAmount();
                                        unbilledCashback += tx.getCashbackEarned();
                                    }
                                } else if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                                    totalPayments += tx.getTotalAmount();
                                }
                            }
                        }

                        // Ledger Math
                        double finalBilledDue = billedSpends - totalPayments;
                        double finalUnbilledDue = unbilledSpends;

                        if (finalBilledDue < 0) {
                            finalUnbilledDue += finalBilledDue;
                            finalBilledDue = 0;
                        }
                        if (finalUnbilledDue < 0) {
                            finalUnbilledDue = 0;
                        }

                        double totalUsed = finalBilledDue + finalUnbilledDue;
                        double availableLimit = card.getTotalLimit() - totalUsed;
                        if (availableLimit < 0) availableLimit = 0;

                        // Inject Live Data to UI
                        holder.tvCardUsed.setText(formatCurrency(totalUsed));
                        holder.tvCardAvailable.setText(formatCurrency(availableLimit));
                        holder.tvCardDue.setText(formatCurrency(finalBilledDue));
                        holder.tvCardUnbilled.setText(formatCurrency(finalUnbilledDue));

                        // Dynamic Circular Progress
                        int progress = 0;
                        if (card.getTotalLimit() > 0) {
                            progress = (int) ((totalUsed / card.getTotalLimit()) * 100);
                        }
                        if (progress > 100) progress = 100;
                        holder.cpiCardProgress.setProgress(progress);
                        holder.tvCardProgressPercent.setText(progress + "%\nUsed");

                        // Dynamic Cashback Rendering
                        if (card.isCashbackCard()) {
                            holder.cardCashbackBadge.setVisibility(View.VISIBLE);
                            holder.tvUnbilledCashback.setText("Unbilled CB: " + formatCurrency(unbilledCashback));
                        } else {
                            holder.cardCashbackBadge.setVisibility(View.GONE);
                        }
                    });
        }

        // Standard Click listener -> Launch Card Ledger
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, CardLedgerActivity.class);
            intent.putExtra("CARD_ID", card.getCardId());
            intent.putExtra("BANK_NAME", card.getBankName());
            intent.putExtra("CARD_NAME", card.getCardName());
            intent.putExtra("CARD_TYPE", card.getCardType());
            intent.putExtra("LAST4", card.getLast4Digits());
            intent.putExtra("TOTAL_LIMIT", card.getTotalLimit());
            intent.putExtra("THEME_COLOR", card.getThemeColor());
            context.startActivity(intent);
        });

        // Long Click listener
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

    // Safely remove active snapshot listeners to prevent app lag when scrolling!
    @Override
    public void onViewRecycled(@NonNull CardViewHolder holder) {
        super.onViewRecycled(holder);
        holder.cancelListener();
    }

    private String formatCurrency(double amount) {
        Locale indianLocale = new Locale.Builder().setLanguage("en").setRegion("IN").build();
        NumberFormat formatter = NumberFormat.getCurrencyInstance(indianLocale);
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(2);
        return formatter.format(amount);
    }

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
        MaterialCardView cardCashbackBadge;
        ListenerRegistration listenerRegistration; // Manages the live connection

        TextView tvCardName, tvCardLogoText, tvCardProgressPercent;
        TextView tvBankName, tvLast4Digits, tvCardType, tvUnbilledCashback;
        TextView tvCardLimit, tvCardDue, tvCardUnbilled, tvCardUsed, tvCardAvailable, tvCardDueDate;

        CircularProgressIndicator cpiCardProgress;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardLogoContainer = itemView.findViewById(R.id.cardLogoContainer);
            cardCashbackBadge = itemView.findViewById(R.id.cardCashbackBadge);

            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardLogoText = itemView.findViewById(R.id.tvCardLogoText);

            tvBankName = itemView.findViewById(R.id.tvBankName);
            tvLast4Digits = itemView.findViewById(R.id.tvLast4Digits);
            tvCardType = itemView.findViewById(R.id.tvCardType);
            tvUnbilledCashback = itemView.findViewById(R.id.tvUnbilledCashback);

            tvCardLimit = itemView.findViewById(R.id.tvCardLimit);
            tvCardDue = itemView.findViewById(R.id.tvCardDue);
            tvCardUnbilled = itemView.findViewById(R.id.tvCardUnbilled);
            tvCardUsed = itemView.findViewById(R.id.tvCardUsed);
            tvCardAvailable = itemView.findViewById(R.id.tvCardAvailable);
            tvCardDueDate = itemView.findViewById(R.id.tvCardDueDate);

            tvCardProgressPercent = itemView.findViewById(R.id.tvCardProgressPercent);
            cpiCardProgress = itemView.findViewById(R.id.cpiCardProgress);
        }

        public void cancelListener() {
            if (listenerRegistration != null) {
                listenerRegistration.remove();
                listenerRegistration = null;
            }
        }
    }
}