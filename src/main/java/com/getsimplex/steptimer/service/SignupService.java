package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.SignupCode;
import com.getsimplex.steptimer.model.SignupStartRequest;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.AlreadyExistsException;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.SendText;
import com.google.gson.Gson;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Phone-only sign-up, step 1 of 2.
 * POST /signup/start texts a 4-digit code to a phone number that has no account yet.
 * (Step 2, POST /signup/verify, checks the code and creates the account.)
 */
public class SignupService {

    private static final Gson gson = new Gson();
    private static final Logger logger = Logger.getLogger(SignupService.class.getName());
    private static final SecureRandom random = new SecureRandom();

    static final long CODE_LIFETIME_MS = 10 * 60 * 1000;   // a code works for 10 minutes
    static final long RESEND_WAIT_MS = 30 * 1000;           // matches the Figma "Resend in 0:24" countdown

    /** Thrown when someone asks for a new code too soon. The route turns it into HTTP 429. */
    public static class TooSoonException extends Exception {
        public TooSoonException(String message) { super(message); }
    }

    public static void start(String requestBody) throws Exception {
        // 1. Read the JSON body into a Java object.
        SignupStartRequest request = gson.fromJson(requestBody, SignupStartRequest.class);
        if (request == null || request.getPhone() == null || request.getPhone().isBlank()) {
            throw new IllegalArgumentException("A phone number is required");
        }
        String region = (request.getRegion() == null || request.getRegion().isBlank()) ? "US" : request.getRegion();

        // 2. Make sure it's a real phone number for that country. Any style works:
        //    8015550100, 801-555-0100, (801) 555-0100, +1 801 555 0100 ...
        //    but too few or too many digits (801-555-010) is refused.
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        if (!phoneUtil.isValidNumber(phoneUtil.parse(request.getPhone(), region))) {
            throw new IllegalArgumentException("Not a valid phone number");
        }

        //    Then put it in the server's standard format: (801) 555-0100 becomes +1801-555-0100.
        String phone = SendText.getFormattedPhone(request.getPhone(), region);
        long phoneDigits = Long.parseLong(phone.replaceAll("[^0-9]", ""));

        // 3. Refuse if an account already has this phone (same lookup as CreateNewUser.java).
        List<User> existing = JedisData.getEntitiesByScore(User.class, phoneDigits, phoneDigits);
        if (!existing.isEmpty()) {
            throw new AlreadyExistsException("Phone number is already registered");
        }

        // 4. Don't allow a new code more than once every 30 seconds.
        long now = System.currentTimeMillis();
        Optional<SignupCode> previous = JedisData.getEntityById(SignupCode.class, phone);
        if (previous.isPresent() && now - previous.get().getCreatedAt() < RESEND_WAIT_MS) {
            throw new TooSoonException("Please wait before requesting another code");
        }

        // 5. Make a new 4-digit code (matches the 4 boxes in Figma and the login codes)
        //    and save it. This replaces any older code for this phone.
        //    Only 10,000 possible codes, so /signup/verify allows just a few wrong guesses.
        SignupCode signupCode = new SignupCode();
        signupCode.setPhone(phone);
        signupCode.setRegion(region);
        signupCode.setCode(String.format("%04d", random.nextInt(10_000)));
        signupCode.setCreatedAt(now);
        signupCode.setExpiresAt(now + CODE_LIFETIME_MS);
        signupCode.setAttempts(0);
        JedisData.loadToJedis(signupCode, phone, signupCode.getExpiresAt());

        // 6. Text it. On a computer without Twilio settings we skip sending;
        //    read the code from Redis instead: HGET SignupCodeMap +1801-555-0100
        if (SendText.isConfigured()) {
            SendText.send(phone, "Your STEDI sign-up code is: " + signupCode.getCode(), region);
        } else {
            logger.info("Twilio is not configured, so no SMS was sent. The sign-up code for " + phone + " is in Redis (SignupCodeMap).");
        }
    }
}
