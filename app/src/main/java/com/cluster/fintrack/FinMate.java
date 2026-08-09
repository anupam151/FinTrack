package com.cluster.fintrack;

@SuppressWarnings("unused") // Hides warnings until we display the data on the screen
public class FinMate {
    private String finMateId;
    private String name;
    private String phoneNo;    // General contact number
    private String whatsappNo; // Empty string if not on WhatsApp, or the number if yes
    private String email;
    private String address;
    private long timestamp;

    // --- Financial amounts ---
    private double receivableCardAmount;
    private double receivableCashAmount;
    private double payableAmount;

    // 1. Required empty constructor for Firebase
    public FinMate() {
    }

    // 2. Main constructor
    public FinMate(String finMateId, String name, String phoneNo, String whatsappNo, String email, String address, long timestamp) {
        this.finMateId = finMateId;
        this.name = name;
        this.phoneNo = phoneNo;
        this.whatsappNo = whatsappNo;
        this.email = email;
        this.address = address;
        this.timestamp = timestamp;
        this.receivableCardAmount = 0.0;
        this.receivableCashAmount = 0.0;
        this.payableAmount = 0.0;
    }

    // 3. Getters
    public String getFinMateId() { return finMateId; }
    public String getName() { return name; }
    public String getPhoneNo() { return phoneNo; }
    public String getWhatsappNo() { return whatsappNo; }
    public String getEmail() { return email; }
    public String getAddress() { return address; }
    public long getTimestamp() { return timestamp; }

    public double getReceivableCardAmount() { return receivableCardAmount; }
    public double getReceivableCashAmount() { return receivableCashAmount; }
    public double getPayableAmount() { return payableAmount; }

    // --- Dynamic Total Calculation ---
    public double getTotalReceivable() {
        return receivableCardAmount + receivableCashAmount;
    }

    // 4. Setters
    public void setReceivableCardAmount(double receivableCardAmount) { this.receivableCardAmount = receivableCardAmount; }
    public void setReceivableCashAmount(double receivableCashAmount) { this.receivableCashAmount = receivableCashAmount; }
    public void setPayableAmount(double payableAmount) { this.payableAmount = payableAmount; }
}