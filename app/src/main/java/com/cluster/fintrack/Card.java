package com.cluster.fintrack;

@SuppressWarnings("unused") // Tells Android Studio to ignore "never used" warnings for Firebase models
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
    private boolean isCashbackCard; // NEW CASHBACK TRACKER

    public Card() {
        // Required empty constructor for Firebase
    }

    public Card(String cardId, String bankName, String cardName, String cardType, String last4Digits,
                double totalLimit, int billingDay, String themeColor, long timestamp, boolean isCashbackCard) {
        this.cardId = cardId;
        this.bankName = bankName;
        this.cardName = cardName;
        this.cardType = cardType;
        this.last4Digits = last4Digits;
        this.totalLimit = totalLimit;
        this.billingDay = billingDay;
        this.themeColor = themeColor;
        this.timestamp = timestamp;
        this.isCashbackCard = isCashbackCard;
    }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getCardName() { return cardName; }
    public void setCardName(String cardName) { this.cardName = cardName; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public String getLast4Digits() { return last4Digits; }
    public void setLast4Digits(String last4Digits) { this.last4Digits = last4Digits; }

    public double getTotalLimit() { return totalLimit; }
    public void setTotalLimit(double totalLimit) { this.totalLimit = totalLimit; }

    public int getBillingDay() { return billingDay; }
    public void setBillingDay(int billingDay) { this.billingDay = billingDay; }

    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isCashbackCard() { return isCashbackCard; }
    public void setCashbackCard(boolean cashbackCard) { this.isCashbackCard = cashbackCard; }
}