package com.cluster.fintrack;

import java.util.HashMap;
import java.util.Map;

public class BankUtils {

    // Loads into memory exactly once!
    private static final Map<String, String> BANK_INITIALS_MAP = new HashMap<>();

    static {
        // --- PUBLIC SECTOR BANKS ---
        BANK_INITIALS_MAP.put("Bank of Baroda", "BOB");
        BANK_INITIALS_MAP.put("Bank of India", "BOI");
        BANK_INITIALS_MAP.put("Bank of Maharashtra", "BOM");
        BANK_INITIALS_MAP.put("Canara Bank", "CAN");
        BANK_INITIALS_MAP.put("Central Bank of India", "CBI");
        BANK_INITIALS_MAP.put("Indian Bank", "IB");
        BANK_INITIALS_MAP.put("Indian Overseas Bank", "IOB");
        BANK_INITIALS_MAP.put("Punjab National Bank", "PNB");
        BANK_INITIALS_MAP.put("Punjab & Sind Bank", "PSB");
        BANK_INITIALS_MAP.put("State Bank of India", "SBI");
        BANK_INITIALS_MAP.put("UCO Bank", "UCO");
        BANK_INITIALS_MAP.put("Union Bank of India", "UBI");
        BANK_INITIALS_MAP.put("Axis Bank", "AXIS");
        BANK_INITIALS_MAP.put("Bandhan Bank", "BANDHAN");
        BANK_INITIALS_MAP.put("CSB Bank", "CSB");
        BANK_INITIALS_MAP.put("City Union Bank", "CUB");
        BANK_INITIALS_MAP.put("DCB Bank", "DCB");
        BANK_INITIALS_MAP.put("Dhanlaxmi Bank", "DLB");
        BANK_INITIALS_MAP.put("Federal Bank", "FED");
        BANK_INITIALS_MAP.put("HDFC Bank", "HDFC");
        BANK_INITIALS_MAP.put("ICICI Bank Limited", "ICICI");
        BANK_INITIALS_MAP.put("ICICI Bank", "ICICI");
        BANK_INITIALS_MAP.put("IDBI Bank", "IDBI");
        BANK_INITIALS_MAP.put("IDFC FIRST Bank", "IDFC");
        BANK_INITIALS_MAP.put("IndusInd Bank", "IND");
        BANK_INITIALS_MAP.put("Jammu & Kashmir Bank", "J&K");
        BANK_INITIALS_MAP.put("Karnataka Bank", "KBL");
        BANK_INITIALS_MAP.put("Karur Vysya Bank", "KVB");
        BANK_INITIALS_MAP.put("Kotak Mahindra Bank", "KOTAK");
        BANK_INITIALS_MAP.put("Nainital Bank", "NB");
        BANK_INITIALS_MAP.put("RBL Bank", "RBL");
        BANK_INITIALS_MAP.put("South Indian Bank", "SIB");
        BANK_INITIALS_MAP.put("Tamilnad Mercantile Bank", "TMB");
        BANK_INITIALS_MAP.put("YES Bank", "YES");
        BANK_INITIALS_MAP.put("AU Small Finance Bank", "AUSFB");
        BANK_INITIALS_MAP.put("Capital Small Finance Bank", "CSFB");
        BANK_INITIALS_MAP.put("Equitas Small Finance Bank", "ESFB");
        BANK_INITIALS_MAP.put("ESAF Small Finance Bank", "ESFB");
        BANK_INITIALS_MAP.put("Fincare Small Finance Bank", "FSFB");
        BANK_INITIALS_MAP.put("Jana Small Finance Bank", "JSFB");
        BANK_INITIALS_MAP.put("North East Small Finance Bank", "NESFB");
        BANK_INITIALS_MAP.put("Shivalik Small Finance Bank", "SSFB");
        BANK_INITIALS_MAP.put("Suryoday Small Finance Bank", "SSFB");
        BANK_INITIALS_MAP.put("Ujjivan Small Finance Bank", "USFB");
        BANK_INITIALS_MAP.put("Unity Small Finance Bank", "USFB");
        BANK_INITIALS_MAP.put("Utkarsh Small Finance Bank", "USFB");
        BANK_INITIALS_MAP.put("Airtel Payments Bank", "APBL");
        BANK_INITIALS_MAP.put("Fino Payments Bank", "FPB");
        BANK_INITIALS_MAP.put("India Post Payments Bank", "IPPB");
        BANK_INITIALS_MAP.put("Jio Payments Bank", "JPB");
        BANK_INITIALS_MAP.put("NSDL Payments Bank", "NPB");
        BANK_INITIALS_MAP.put("Paytm Payments Bank", "PPBL");
        BANK_INITIALS_MAP.put("Baroda Gujarat Gramin Bank", "BGGB");
        BANK_INITIALS_MAP.put("Baroda Rajasthan Kshetriya Gramin Bank", "BRKGB");
        BANK_INITIALS_MAP.put("Baroda U.P. Bank", "BUPB");
        BANK_INITIALS_MAP.put("Kerala Gramin Bank", "KGB");
        BANK_INITIALS_MAP.put("Cosmos Co-operative Bank", "CCB");
        BANK_INITIALS_MAP.put("Saraswat Co-operative Bank", "SCB");
        BANK_INITIALS_MAP.put("SVC Co-operative Bank", "SVC");
        BANK_INITIALS_MAP.put("American Express", "AMEX");
        BANK_INITIALS_MAP.put("Bank of America", "BOA");
        BANK_INITIALS_MAP.put("Barclays Bank", "BARB");
        BANK_INITIALS_MAP.put("BNP Paribas", "BNP");
        BANK_INITIALS_MAP.put("Citibank", "CITI");
        BANK_INITIALS_MAP.put("DBS Bank", "DBS");
        BANK_INITIALS_MAP.put("Deutsche Bank", "DB");
        BANK_INITIALS_MAP.put("First Abu Dhabi Bank", "FAB");
        BANK_INITIALS_MAP.put("HSBC Bank", "HSBC");
        BANK_INITIALS_MAP.put("Qatar National Bank", "QNB");
        BANK_INITIALS_MAP.put("SBM Bank India", "SBM");
        BANK_INITIALS_MAP.put("Standard Chartered Bank", "SCB");
        BANK_INITIALS_MAP.put("Standard Chartered", "SCB");
    }

    public static String getBankInitials(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) return "BANK";

        // Check if we have an exact match in our massive list
        if (BANK_INITIALS_MAP.containsKey(bankName)) {
            return BANK_INITIALS_MAP.get(bankName);
        }

        // Brilliant Fallback: For completely unknown/custom banks (e.g., "My Custom Bank" -> "MCB")
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
}