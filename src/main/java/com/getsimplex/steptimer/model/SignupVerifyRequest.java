package com.getsimplex.steptimer.model;

/**
 * The JSON body of POST /signup/verify. Everything from the Figma "Create account" screen
 * plus the 4-digit code from the text message, e.g.
 * {"phone":"801-555-0123","region":"US","code":"7530","userName":"alexmorgan",
 *  "fullName":"Alex Morgan","recoveryEmail":"alex@example.com","agreedToTerms":true,"wantsTips":false}
 */
public class SignupVerifyRequest {

    private String phone;
    private String region;
    private String code;
    private String userName;
    private String fullName;
    private String recoveryEmail;
    private boolean agreedToTerms;   // "I agree to the Terms of Use and Privacy Policy" (required)
    private boolean wantsTips;       // "Send me STEDI tips and updates" (optional)

    public String getPhone() { return phone; }
    public String getRegion() { return region; }
    public String getCode() { return code; }
    public String getUserName() { return userName; }
    public String getFullName() { return fullName; }
    public String getRecoveryEmail() { return recoveryEmail; }
    public boolean isAgreedToTerms() { return agreedToTerms; }
    public boolean isWantsTips() { return wantsTips; }
}
