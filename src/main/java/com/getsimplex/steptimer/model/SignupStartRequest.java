package com.getsimplex.steptimer.model;

/**
 * The JSON body of POST /signup/start, e.g. {"phone":"801-555-0100","region":"US"}.
 * Gson fills these fields in from the request.
 */
public class SignupStartRequest {

    private String phone;
    private String region;

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
}
