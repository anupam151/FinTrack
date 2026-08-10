package com.cluster.fintrack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class Transaction {

    // --- ENUMS FOR TRANSACTION TYPES ---
    // "CARD_SPEND", "CASH_TRANSACTION", "SETTLEMENT", "EMI_CONVERSION", "CARD_PAYMENT"
    private String transactionType;

    private String transactionId;
    private String cardId;     // Null if this is a pure cash transaction
    private String title;      // e.g., "Amazon - Laptop" or "Payment Received"
    private long timestamp;
    private double totalAmount; // The grand total of the transaction

    // --- THE SPLIT ENGINE ---
    // Key: FinMateId (or "self"). Value: The exact absolute amounts.
    private Map<String, TransactionSplit> splits;

    // --- THE EMI ENGINE ---
    private boolean isEmi;
    private EmiData emiData; // Null if not an EMI

    // 1. Required empty constructor for Firebase
    public Transaction() {
    }

    // 2. Main Constructor
    public Transaction(String transactionId, String transactionType, String cardId, String title,
                       long timestamp, double totalAmount, boolean isEmi) {
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.cardId = cardId;
        this.title = title;
        this.timestamp = timestamp;
        this.totalAmount = totalAmount;
        this.isEmi = isEmi;
        this.splits = new HashMap<>();
    }

    // --- GETTERS & SETTERS ---
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public Map<String, TransactionSplit> getSplits() { return splits; }
    public void setSplits(Map<String, TransactionSplit> splits) { this.splits = splits; }

    public boolean isEmi() { return isEmi; }
    public void setEmi(boolean emi) { isEmi = emi; }

    public EmiData getEmiData() { return emiData; }
    public void setEmiData(EmiData emiData) { this.emiData = emiData; }


    // =========================================================================
    // NESTED CLASS 1: THE SPLIT OBJECT
    // This brilliantly handles BOTH Spends and Settlements!
    // =========================================================================
    public static class TransactionSplit {
        // If SPENDING: This is the absolute Bank Principal they owe.
        // If SETTLING: This is how much of their payment goes to clearing Card Debt.
        private double cardAmount;

        // If SPENDING: This is your stealthy Privilege Charge (Cash Profit).
        // If SETTLING: This is how much of their payment goes to clearing Cash Debt.
        private double cashAmount;

        public TransactionSplit() {}

        public TransactionSplit(double cardAmount, double cashAmount) {
            this.cardAmount = cardAmount;
            this.cashAmount = cashAmount;
        }

        public double getCardAmount() { return cardAmount; }
        public void setCardAmount(double cardAmount) { this.cardAmount = cardAmount; }

        public double getCashAmount() { return cashAmount; }
        public void setCashAmount(double cashAmount) { this.cashAmount = cashAmount; }

        // The "Stealth" Total that the FinMate actually sees on their receipt
        public double getCombinedStealthAmount() {
            return cardAmount + cashAmount;
        }
    }

    // =========================================================================
    // NESTED CLASS 2: EMI DATA
    // Holds the Bank's extra fees and the full Amortization Schedule
    // =========================================================================
    public static class EmiData {
        private double bankProcessingFee;
        private double bankProcessingFeeGst;
        private List<EmiMonth> amortizationSchedule;

        public EmiData() {}

        public EmiData(double bankProcessingFee, double bankProcessingFeeGst, List<EmiMonth> amortizationSchedule) {
            this.bankProcessingFee = bankProcessingFee;
            this.bankProcessingFeeGst = bankProcessingFeeGst;
            this.amortizationSchedule = amortizationSchedule;
        }

        public double getBankProcessingFee() { return bankProcessingFee; }
        public void setBankProcessingFee(double bankProcessingFee) { this.bankProcessingFee = bankProcessingFee; }

        public double getBankProcessingFeeGst() { return bankProcessingFeeGst; }
        public void setBankProcessingFeeGst(double bankProcessingFeeGst) { this.bankProcessingFeeGst = bankProcessingFeeGst; }

        public List<EmiMonth> getAmortizationSchedule() { return amortizationSchedule; }
        public void setAmortizationSchedule(List<EmiMonth> amortizationSchedule) { this.amortizationSchedule = amortizationSchedule; }
    }

    // =========================================================================
    // NESTED CLASS 3: EMI MONTH (The Editable Grid Row)
    // =========================================================================
    public static class EmiMonth {
        private int monthNumber;
        private double bankPrincipal;
        private double bankInterest;
        private double bankGst;
        private boolean isBilled; // Turns true when the billing date passes, and it hits the card limit

        public EmiMonth() {}

        public EmiMonth(int monthNumber, double bankPrincipal, double bankInterest, double bankGst, boolean isBilled) {
            this.monthNumber = monthNumber;
            this.bankPrincipal = bankPrincipal;
            this.bankInterest = bankInterest;
            this.bankGst = bankGst;
            this.isBilled = isBilled;
        }

        public int getMonthNumber() { return monthNumber; }
        public void setMonthNumber(int monthNumber) { this.monthNumber = monthNumber; }

        public double getBankPrincipal() { return bankPrincipal; }
        public void setBankPrincipal(double bankPrincipal) { this.bankPrincipal = bankPrincipal; }

        public double getBankInterest() { return bankInterest; }
        public void setBankInterest(double bankInterest) { this.bankInterest = bankInterest; }

        public double getBankGst() { return bankGst; }
        public void setBankGst(double bankGst) { this.bankGst = bankGst; }

        public boolean isBilled() { return isBilled; }
        public void setBilled(boolean billed) { isBilled = billed; }

        public double getTotalBankDueForMonth() {
            return bankPrincipal + bankInterest + bankGst;
        }
    }
}