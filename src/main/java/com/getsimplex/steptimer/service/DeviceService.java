package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.DeviceRequest;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.model.UserDevice;
import com.getsimplex.steptimer.utils.AlreadyExistsException;
import com.getsimplex.steptimer.utils.JedisClient;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.NotFoundException;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Paired STEDI sensors. Like profiles, every method only touches the logged-in user's own devices.
 *
 * The "active" device is the one tests use. It's also copied to User.deviceNickName, because the
 * older app and routes (/rapidsteptest, /devices/updates/recent) still read that field.
 */
public class DeviceService {

    private static final Gson gson = new Gson();

    static final int MAX_DEVICES = 10;
    static final long CONNECTED_WINDOW_MS = 2 * 60 * 1000;   // reported in during the last 2 minutes = "Connected"
    private static final Pattern DEVICE_ID_RULE = Pattern.compile("^[A-Za-z0-9._:-]{3,64}$");

    // ---------------------------------------------------------------- list

    /** The account's devices: active one first, then newest paired first. */
    public static List<UserDevice> list(User user) throws Exception {
        List<UserDevice> devices = owned(user.getUserName());
        long now = System.currentTimeMillis();
        for (UserDevice device : devices) {
            // Same data /devices/updates/recent reads: the sensor's readings, indexed by deviceId and time.
            Long recent = JedisClient.zcount("DeviceMessageByDeviceId-" + device.getDeviceId(), now - CONNECTED_WINDOW_MS, now);
            device.setConnectedNow(recent != null && recent > 0);
        }
        devices.sort(Comparator.comparing((UserDevice d) -> !d.isActive())
                .thenComparing(UserDevice::getPairedAt, Comparator.reverseOrder()));
        return devices;
    }

    // ---------------------------------------------------------------- pair

    public static UserDevice pair(User user, String requestBody) throws Exception {
        DeviceRequest request = read(requestBody);
        String deviceId = request.getDeviceId() == null ? "" : request.getDeviceId().trim();
        if (!DEVICE_ID_RULE.matcher(deviceId).matches()) {
            throw new IllegalArgumentException("deviceId must be 3-64 letters, numbers, dots, dashes, colons or underscores");
        }
        List<UserDevice> existing = owned(user.getUserName());
        if (existing.stream().anyMatch(d -> d.getDeviceId().equalsIgnoreCase(deviceId))) {
            throw new AlreadyExistsException("This device is already paired");
        }
        if (existing.size() >= MAX_DEVICES) {
            throw new IllegalArgumentException("An account can pair at most " + MAX_DEVICES + " devices");
        }

        UserDevice device = new UserDevice();
        device.setDeviceId(deviceId);
        device.setOwnerUserName(user.getUserName());
        device.setNickname(text(request.getNickname(), 40, "nickname", "STEDI · " + lastFour(deviceId)));
        device.setLocation(text(request.getLocation(), 40, "location", null));
        device.setPairedAt(System.currentTimeMillis());
        save(device);

        if (existing.stream().noneMatch(UserDevice::isActive)) {
            makeActive(user, device);   // the first device paired becomes the one tests use
        }
        return device;
    }

    // ---------------------------------------------------------------- edit

    public static UserDevice update(User user, String deviceId, String requestBody) throws Exception {
        UserDevice device = getOwned(user, deviceId);
        DeviceRequest request = read(requestBody);
        if (request.getNickname() != null) {
            device.setNickname(text(request.getNickname(), 40, "nickname", device.getNickname()));
        }
        if (request.getLocation() != null) {
            device.setLocation(text(request.getLocation(), 40, "location", null));
        }
        save(device);
        if (Boolean.TRUE.equals(request.getActive()) && !device.isActive()) {
            makeActive(user, device);
        }
        return device;
    }

    // ---------------------------------------------------------------- unpair

    public static void unpair(User user, String deviceId) throws Exception {
        UserDevice device = getOwned(user, deviceId);
        String key = key(user.getUserName(), device.getDeviceId());
        JedisData.deleteJedis(UserDevice.class, key);
        JedisClient.zrem("UserDeviceByOwner-" + user.getUserName(), key);

        if (device.isActive()) {
            // Hand "active" to the most recently paired device that's left, or to nobody.
            List<UserDevice> left = owned(user.getUserName());
            left.sort(Comparator.comparing(UserDevice::getPairedAt, Comparator.reverseOrder()));
            if (left.isEmpty()) {
                setUserDeviceNickName(user, null);
            } else {
                makeActive(user, left.get(0));
            }
        }
    }

    // ---------------------------------------------------------------- used by tests

    /** Called after a test is saved: updates "last used" if this account has that device paired. Never fails a test. */
    static void markUsed(User user, String deviceId, long when) {
        if (deviceId == null || deviceId.isBlank()) return;
        try {
            Optional<UserDevice> device = JedisData.getEntityById(UserDevice.class, key(user.getUserName(), deviceId));
            if (device.isPresent()) {
                device.get().setLastUsedAt(when);
                save(device.get());
            }
        } catch (Exception ignored) {
            // "last used" is nice to have; a failure here must never lose the test itself
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The ownership check: someone else's device and a missing one both answer "Device not found". */
    private static UserDevice getOwned(User user, String deviceId) throws Exception {
        Optional<UserDevice> device = deviceId == null ? Optional.empty()
                : JedisData.getEntityById(UserDevice.class, key(user.getUserName(), deviceId.trim()));
        if (device.isEmpty()) {
            throw new NotFoundException("Device not found");
        }
        return device.get();
    }

    /** Marks one device active, all the others inactive, and copies it to User.deviceNickName. */
    private static void makeActive(User user, UserDevice chosen) throws Exception {
        for (UserDevice device : owned(user.getUserName())) {
            boolean shouldBeActive = device.getDeviceId().equals(chosen.getDeviceId());
            if (device.isActive() != shouldBeActive) {
                device.setActive(shouldBeActive);
                save(device);
            }
        }
        chosen.setActive(true);
        setUserDeviceNickName(user, chosen.getDeviceId());
    }

    /** Keeps the older field in step: the old app and /devices/updates/recent read User.deviceNickName. */
    private static void setUserDeviceNickName(User user, String deviceId) throws Exception {
        user.setDeviceNickName(deviceId);
        JedisData.updateRedisMap(user, user.getUserName());
    }

    private static List<UserDevice> owned(String ownerUserName) throws Exception {
        return new ArrayList<>(JedisData.getEntitiesByIndex(UserDevice.class, "Owner", ownerUserName));
    }

    private static void save(UserDevice device) throws Exception {
        device.setConnectedNow(null);   // worked out on each read, never stored
        JedisData.loadToJedisWithIndexUsingScore(device, key(device.getOwnerUserName(), device.getDeviceId()),
                device.getPairedAt(), "Owner", device.getOwnerUserName());
    }

    /** "|" can't appear in a deviceId, so owner|deviceId can never be confused between two accounts. */
    private static String key(String ownerUserName, String deviceId) {
        return ownerUserName + "|" + deviceId.toUpperCase();
    }

    private static String text(String value, int maxLength, String name, String fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(name + " must be at most " + maxLength + " characters");
        }
        return value.trim();
    }

    private static String lastFour(String deviceId) {
        String digits = deviceId.replaceAll("[^0-9]", "");
        return digits.length() >= 4 ? digits.substring(digits.length() - 4) : deviceId;
    }

    private static DeviceRequest read(String requestBody) {
        DeviceRequest request = gson.fromJson(requestBody, DeviceRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("Device details are required");
        }
        return request;
    }
}
