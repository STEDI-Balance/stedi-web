package com.getsimplex.steptimer.model;

/**
 * A STEDI sensor paired with an account (Figma "Devices": "Your devices · 1 connected · 2 paired").
 * The same physical sensor can be paired with more than one account (a shared home or clinic),
 * so the record's key is owner + deviceId.
 *
 * Saved in Redis as:
 *   UserDeviceMap[<owner>|<deviceId>]   -> this object as JSON
 *   UserDevice (sorted set)             -> that key, scored by pairedAt
 *   UserDeviceByOwner-<ownerUserName>   -> that key, scored by pairedAt (one account's devices)
 */
public class UserDevice {

    private String deviceId;        // the sensor's own ID, the same one it sends with every reading (e.g. STEDI-0241)
    private String ownerUserName;
    private String nickname;        // shown name, e.g. "STEDI · 0241"
    private String location;        // e.g. "Living room" (optional)
    private boolean active;         // the one used for tests; exactly one per account when any are paired
    private long pairedAt;
    private Long lastUsedAt;        // when a test last used it (Figma: "last used 12 Oct")
    private Boolean connectedNow;   // not saved: worked out on each read (did it report in the last 2 minutes?)

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getOwnerUserName() { return ownerUserName; }
    public void setOwnerUserName(String ownerUserName) { this.ownerUserName = ownerUserName; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public long getPairedAt() { return pairedAt; }
    public void setPairedAt(long pairedAt) { this.pairedAt = pairedAt; }

    public Long getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Long lastUsedAt) { this.lastUsedAt = lastUsedAt; }

    public Boolean getConnectedNow() { return connectedNow; }
    public void setConnectedNow(Boolean connectedNow) { this.connectedNow = connectedNow; }
}
