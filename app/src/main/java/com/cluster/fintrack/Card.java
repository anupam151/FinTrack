package com.cluster.fintrack;

@SuppressWarnings("unused") // This tells Android Studio to hide those warnings!
public class Card {
    private String cardId;
    private String bankName;
    private String cardName;
    private String cardType;
    private String last4Digits;
    private double totalLimit;
    private int billingDay;
    private String themeColor;
    private long timestamp;

    // 1. Required empty constructor for Firebase
    public Card() {
    }

    // 2. Main constructor to build the card
    public Card(String cardId, String bankName, String cardName, String cardType, String last4Digits, double totalLimit, int billingDay, String themeColor, long timestamp) {
        this.cardId = cardId;
        this.bankName = bankName;
        this.cardName = cardName;
        this.cardType = cardType;
        this.last4Digits = last4Digits;
        this.totalLimit = totalLimit;
        this.billingDay = billingDay;
        this.themeColor = themeColor;
        this.timestamp = timestamp;
    }

    // 3. Getters so Firebase can read the data
    public String getCardId() { return cardId; }
    public String getBankName() { return bankName; }
    public String getCardName() { return cardName; }
    public String getCardType() { return cardType; }
    public String getLast4Digits() { return last4Digits; }
    public double getTotalLimit() { return totalLimit; }
    public int getBillingDay() { return billingDay; }
    public String getThemeColor() { return themeColor; }
    public long getTimestamp() { return timestamp; }
}