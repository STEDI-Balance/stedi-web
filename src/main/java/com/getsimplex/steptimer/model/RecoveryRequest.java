package com.getsimplex.steptimer.model;

/**
 * The JSON body for all three /recovery routes. Each route uses only some fields:
 *   /recovery/start   {"recoveryEmail":"alex@example.com"}
 *   /recovery/verify  {"recoveryEmail":"alex@example.com","code":"1234","newPhone":"801-555-0177","region":"US"}
 *   /recovery/confirm {"recoveryEmail":"alex@example.com","code":"5678"}
 */
public class RecoveryRequest {

    private String recoveryEmail;
    private String code;
    private String newPhone;
    private String region;

    public String getRecoveryEmail() { return recoveryEmail; }
    public String getCode() { return code; }
    public String getNewPhone() { return newPhone; }
    public String getRegion() { return region; }
}
