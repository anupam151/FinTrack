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

import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

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
        holder.tvCardLogoText.setText(BankUtils.getBankInitials(card.getBankName())); // CENTRALIZED UTIL
        holder.tvBankName.setText(card.getBankName());
        holder.tvLast4Digits.setText(String.format(Locale.getDefault(), "• %s", card.getLast4Digits()));
        holder.tvCardType.setText(card.getCardType());
        holder.tvCardLimit.setText(formatCurrency(card.getTotalLimit()));

        // --- DYNAMIC BILLING DATE CALCULATION ---
        int billingDay = card.getBillingDay();
        Calendar calendar = Calendar.getInstance();
        int currentDay = calendar.get(Calendar.DAY_OF_MONTH);

        // If today is past or on the billing day, the next bill generates next month
        if (currentDay >= billingDay) {
            calendar.add(Calendar.MONTH, 1);
        }

        // Safeguard: If billing day is 31, but month is Feb (28 days), cap it safely
        int maxDaysInTargetMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
        int targetDay = Math.min(billingDay, maxDaysInTargetMonth);

        calendar.set(Calendar.DAY_OF_MONTH, targetDay);

        String suffix = getOrdinalSuffix(targetDay);
        SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMM yyyy", Locale.getDefault());
        String dueText = String.format(Locale.getDefault(), "%d%s %s", targetDay, suffix, monthYearFormat.format(calendar.getTime()));

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

        // --- FETCH LIVE DATA FOR EACH CARD IN REAL-TIME ---
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

                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Transaction tx = doc.toObject(Transaction.class);
                            if (tx != null) {
                                if ("CARD_SPEND".equals(tx.getTransactionType()) || "PAY_CREDIT".equals(tx.getTransactionType())) {
                                    if (tx.isBilled()) {
                                        billedSpends += tx.getTotalAmount();
                                    } else {
                                        unbilledSpends += tx.getTotalAmount();
                                    }
                                } else if ("CARD_PAYMENT".equals(tx.getTransactionType())) {
                                    totalPayments += tx.getTotalAmount();
                                }
                                // Include EMI_MASTER to block the credit card limit globally
                                else if ("EMI_MASTER".equals(tx.getTransactionType()) && tx.getEmiData() != null) {
                                    unbilledSpends += tx.getEmiData().getRemainingEmiPrincipal();
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

    public static class CardViewHolder extends RecyclerView.ViewHolder {
        androidx.cardview.widget.CardView cardLogoContainer;
        ListenerRegistration listenerRegistration; // Manages the live connection

        TextView tvCardName, tvCardLogoText, tvCardProgressPercent;
        TextView tvBankName, tvLast4Digits, tvCardType;
        TextView tvCardLimit, tvCardDue, tvCardUnbilled, tvCardUsed, tvCardAvailable, tvCardDueDate;

        CircularProgressIndicator cpiCardProgress;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardLogoContainer = itemView.findViewById(R.id.cardLogoContainer);

            tvCardName = itemView.findViewById(R.id.tvCardName);
            tvCardLogoText = itemView.findViewById(R.id.tvCardLogoText);

            tvBankName = itemView.findViewById(R.id.tvBankName);
            tvLast4Digits = itemView.findViewById(R.id.tvLast4Digits);
            tvCardType = itemView.findViewById(R.id.tvCardType);

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