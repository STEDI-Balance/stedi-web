//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.model;

import org.eclipse.jetty.websocket.api.Session;

/**
 * Created by sean on 8/11/2016.
 */
public class DeviceMessage {

    private String messageOrigin;
    private String sessionKey;
    private String message;
    private Session session;
    private String deviceId;
    private long date;
    private String sensorType;
    private String distance;
    private long timestamp;

    public long getDate() {
        return date;
    }

    public void setDate(long date) {
        this.date = date;
    }

    public String getSensorType() {
        return sensorType;
    }

    public void setSensorType(String sensorType) {
        this.sensorType = sensorType;
    }

    public String getDistance() {
        return distance;
    }

    public void setDistance(String distance) {
        this.distance = distance;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }



    public String getMessageOrigin() {
        return messageOrigin;
    }

    public void setMessageOrigin(String messageOrigin) {
        this.messageOrigin = messageOrigin;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session) {
        this.session = session;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }
}
