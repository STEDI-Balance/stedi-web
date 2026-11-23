package com.getsimplex.steptimer.model;

/**
 * The JSON body of POST /devices (pair) and PATCH /devices/:deviceId (edit).
 *   POST  {"deviceId":"STEDI-0241","nickname":"STEDI · 0241","location":"Living room"}
 *   PATCH {"location":"Kitchen"}   or   {"active":true}   (make this the device used for tests)
 */
public class DeviceRequest {

    private String deviceId;
    private String nickname;
    private String location;
    private Boolean active;

    public String getDeviceId() { return deviceId; }
    public String getNickname() { return nickname; }
    public String getLocation() { return location; }
    public Boolean getActive() { return active; }
}
