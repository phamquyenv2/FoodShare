package com.datn.foodshare.util;

public final class PhoneNumberUtil {

    private PhoneNumberUtil() {
    }

    public static String normalizeVietnamese(String phone) {
        String normalized = phone == null ? "" : phone.trim().replace(" ", "");
        if (normalized.startsWith("+84")) {
            return "0" + normalized.substring(3);
        }
        return normalized;
    }

    public static String toInternational(String phone) {
        String normalized = normalizeVietnamese(phone);
        return normalized.startsWith("0") ? "84" + normalized.substring(1) : normalized;
    }
}
