package com.getsimplex.steptimer.model;

/**
 * A 4-digit code texted to a phone number during sign-up (POST /signup/start).
 *
 * Saved in Redis as SignupCodeMap[phone] -> this object as JSON.
 * The key is the phone number (not the code), so two people can never
 * overwrite each other's codes, and requesting a new code replaces the old one.
 */
public class SignupCode {

    private String phone;      // formatted by SendText.getFormattedPhone, e.g. +1801-555-0100
    private String region;     // e.g. US
    private String code;       // 4 digits, kept as text so leading zeros survive ("0482")
    private long createdAt;    // when it was sent (milliseconds since 1970)
    private long expiresAt;    // when it stops working
    private int attempts;      // wrong guesses so far (used by /signup/verify)

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(long expiresAt) { this.expiresAt = expiresAt; }

    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
