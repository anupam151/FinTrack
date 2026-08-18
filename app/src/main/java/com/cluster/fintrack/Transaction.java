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

    private boolean billed;

    // Tracks which specific monthly statement this belongs to (e.g., "August 2026")
    private String billedMonth;

    // NEW: Tracks the exact millisecond the statement was generated for the 72-hour undo window
    private long statementGeneratedAt = 0;

    // --- THE SPLIT ENGINE ---
    // Key: FinMateId (or "self"). Value: The exact absolute amounts.
    private Map<String, TransactionSplit> splits;

    // --- THE EMI ENGINE ---
    private boolean isEmi;
    private EmiData emiData; // Null if not an EMI

    // 1. Required empty constructor for Firebase
    public Transaction() {
    }

    // 2. Main Constructor (AddTransactionActivity uses this)
    public Transaction(String transactionId, String transactionType, String cardId, String title,
                       long timestamp, double totalAmount, boolean isEmi) {
        this.transactionId = transactionId;
        this.transactionType = transactionType;
        this.cardId = cardId;
        this.title = title;
        this.timestamp = timestamp;
        this.totalAmount = totalAmount;
        this.isEmi = isEmi;
        this.billed = false;
        this.billedMonth = null; // Defaults to null until a bill is formally generated!
        this.statementGeneratedAt = 0;
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

    public boolean isBilled() { return billed; }
    public void setBilled(boolean billed) { this.billed = billed; }

    public String getBilledMonth() { return billedMonth; }
    public void setBilledMonth(String billedMonth) { this.billedMonth = billedMonth; }

    public long getStatementGeneratedAt() { return statementGeneratedAt; }
    public void setStatementGeneratedAt(long statementGeneratedAt) { this.statementGeneratedAt = statementGeneratedAt; }

    public Map<String, TransactionSplit> getSplits() { return splits; }
    public void setSplits(Map<String, TransactionSplit> splits) { this.splits = splits; }

    public boolean isEmi() { return isEmi; }
    public void setEmi(boolean emi) { isEmi = emi; }

    public EmiData getEmiData() { return emiData; }
    public void setEmiData(EmiData emiData) { this.emiData = emiData; }


    // =========================================================================
    // NESTED CLASS 1: THE SPLIT OBJECT
    // =========================================================================
    public static class TransactionSplit {
        private double cardAmount;
        private double cashAmount;
        private double paidAmount;

        public TransactionSplit() {}

        public TransactionSplit(double cardAmount, double cashAmount, double paidAmount) {
            this.cardAmount = cardAmount;
            this.cashAmount = cashAmount;
            this.paidAmount = paidAmount;
        }

        public double getCardAmount() { return cardAmount; }
        public void setCardAmount(double cardAmount) { this.cardAmount = cardAmount; }

        public double getCashAmount() { return cashAmount; }
        public void setCashAmount(double cashAmount) { this.cashAmount = cashAmount; }

        public double getPaidAmount() { return paidAmount; }
        public void setPaidAmount(double paidAmount) { this.paidAmount = paidAmount; }

        // The "Stealth" Total that the FinMate actually sees on their receipt
        public double getCombinedStealthAmount() {
            return cardAmount + cashAmount;
        }
    }

    // =========================================================================
    // NESTED CLASS 2: EMI DATA
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
    // NESTED CLASS 3: EMI MONTH
    // =========================================================================
    public static class EmiMonth {
        private int monthNumber;
        private double bankPrincipal;
        private double bankInterest;
        private double bankGst;
        private boolean isBilled;

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
        public void setBilled(boolean billed) { this.isBilled = billed; }

        public double getTotalBankDueForMonth() {
            return bankPrincipal + bankInterest + bankGst;
        }
    }
}