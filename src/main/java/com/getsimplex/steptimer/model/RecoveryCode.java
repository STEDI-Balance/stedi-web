package com.getsimplex.steptimer.model;

/**
 * One account-recovery attempt ("I have a new phone number").
 * Saved in Redis as RecoveryCodeMap[recoveryEmail] -> this object as JSON.
 *
 * It moves through two stages:
 *   EMAIL_SENT       a 4-digit code was emailed           (POST /recovery/start)
 *   NEW_PHONE_TEXTED a 4-digit code was texted to the new phone (POST /recovery/verify)
 * and is deleted once the account has moved            (POST /recovery/confirm).
 */
public class RecoveryCode {

    public static final String EMAIL_SENT = "EMAIL_SENT";
    public static final String NEW_PHONE_TEXTED = "NEW_PHONE_TEXTED";

    private String recoveryEmail;
    private String userName;     // the account being recovered
    private String stage;        // EMAIL_SENT or NEW_PHONE_TEXTED
    private String code;         // the code for the current stage (email code, then phone code)
    private int attempts;        // wrong guesses for the current code
    private long createdAt;
    private long expiresAt;
    private String newPhone;     // filled in at stage NEW_PHONE_TEXTED
    private String newRegion;

    public String getRecoveryEmail() { return recoveryEmail; }
    public void setRecoveryEmail(String recoveryEmail) { this.recoveryEmail = recoveryEmail; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getStage() { return stage; }
    public void setStage(String stage) { this.stage = stage; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public String getNewPhone() { return newPhone; }
    public void setNewPhone(String newPhone) { this.newPhone = newPhone; }

    public String getNewRegion() { return newRegion; }
    public void setNewRegion(String newRegion) { this.newRegion = newRegion; }
}
