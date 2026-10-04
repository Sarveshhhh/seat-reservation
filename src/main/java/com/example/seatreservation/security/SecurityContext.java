package com.example.seatreservation.security;

public class SecurityContext {

    private static final ThreadLocal<String> CURRENT_USER_ID = new ThreadLocal<>();

    private SecurityContext() {
    }

    public static void setCurrentUserId(String userId) {
        CURRENT_USER_ID.set(userId);
    }

    public static String getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    public static void clear() {
        CURRENT_USER_ID.remove();
    }
}
