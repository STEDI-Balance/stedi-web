package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.RecoveryCode;
import com.getsimplex.steptimer.model.RecoveryRequest;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.AlreadyExistsException;
import com.getsimplex.steptimer.utils.JedisData;
import com.getsimplex.steptimer.utils.SendGmail;
import com.getsimplex.steptimer.utils.SendText;
import com.google.gson.Gson;

import java.util.Optional;
import java.util.logging.Logger;

import static com.getsimplex.steptimer.service.SignupService.*;

/**
 * Account recovery for someone who has a new phone number:
 *   1. POST /recovery/start   email a code to the account's recovery email
 *   2. POST /recovery/verify  email code is right -> text a code to the NEW phone
 *   3. POST /recovery/confirm text code is right  -> move the account to the new phone, log in
 * Both codes are 4 digits, last 10 minutes, and allow 5 wrong guesses.
 */
public class RecoveryService {

    private static final Gson gson = new Gson();
    private static final Logger logger = Logger.getLogger(RecoveryService.class.getName());

    /** The one answer /recovery/start always gives, whether or not the email has an account. */
    public static final String START_REPLY = "If that email belongs to an account, we sent it a code.";

    // ---------------------------------------------------------------- step 1

    public static void start(String requestBody) throws Exception {
        RecoveryRequest request = read(requestBody);
        String email = clean(request.getRecoveryEmail()).toLowerCase();

        // Find the account. If there isn't one, quietly do nothing: the route still says
        // START_REPLY, so nobody can use this to find out which emails have accounts.
        Optional<User> user = findUserByEmail(email);
        if (user.isEmpty()) {
            logger.info("Recovery requested for an email with no account");
            return;
        }

        // Too soon since the last code? Also quietly do nothing (an error here would
        // reveal that the email has an account).
        long now = System.currentTimeMillis();
        Optional<RecoveryCode> previous = JedisData.getEntityById(RecoveryCode.class, email);
        if (previous.isPresent() && now - previous.get().getCreatedAt() < RESEND_WAIT_MS) {
            return;
        }

        RecoveryCode recovery = new RecoveryCode();
        recovery.setRecoveryEmail(email);
        recovery.setUserName(user.get().getUserName());
        recovery.setStage(RecoveryCode.EMAIL_SENT);
        recovery.setCode(newFourDigitCode());
        recovery.setAttempts(0);
        recovery.setCreatedAt(now);
        recovery.setExpiresAt(now + CODE_LIFETIME_MS);
        save(recovery);

        if (SendGmail.isConfigured()) {
            SendGmail.sendPrivate(email, "Your STEDI recovery code",
                    "Your STEDI account recovery code is: " + recovery.getCode()
                    + "\n\nIf you didn't ask to recover your account, you can ignore this email.");
        } else {
            logger.info("Email is not configured, so no email was sent. The recovery code is in Redis (RecoveryCodeMap).");
        }
    }

    // ---------------------------------------------------------------- step 2

    public static void verify(String requestBody) throws Exception {
        RecoveryRequest request = read(requestBody);
        String email = clean(request.getRecoveryEmail()).toLowerCase();
        String region = regionOrUS(request.getRegion());
        String newPhone = standardPhone(request.getNewPhone(), region);   // checked before the code: typos don't use a guess

        RecoveryCode recovery = checkCode(email, RecoveryCode.EMAIL_SENT, clean(request.getCode()));

        if (phoneIsRegistered(newPhone)) {
            throw new AlreadyExistsException("That phone number already belongs to an account");
        }

        // Email proved who they are. Now prove they own the new phone: a fresh code, texted to it.
        long now = System.currentTimeMillis();
        recovery.setStage(RecoveryCode.NEW_PHONE_TEXTED);
        recovery.setNewPhone(newPhone);
        recovery.setNewRegion(region);
        recovery.setCode(newFourDigitCode());     // the email code stops working here
        recovery.setAttempts(0);
        recovery.setCreatedAt(now);
        recovery.setExpiresAt(now + CODE_LIFETIME_MS);
        save(recovery);

        if (SendText.isConfigured()) {
            SendText.send(newPhone, "Your STEDI code to confirm your new number is: " + recovery.getCode(), region);
        } else {
            logger.info("Twilio is not configured, so no SMS was sent. The new-phone code is in Redis (RecoveryCodeMap).");
        }
    }

    // ---------------------------------------------------------------- step 3

    /** Moves the account to the new phone and returns a session token. */
    public static String confirm(String requestBody) throws Exception {
        RecoveryRequest request = read(requestBody);
        String email = clean(request.getRecoveryEmail()).toLowerCase();

        RecoveryCode recovery = checkCode(email, RecoveryCode.NEW_PHONE_TEXTED, clean(request.getCode()));

        // Check again: someone could have signed up with that phone in the last few minutes.
        if (phoneIsRegistered(recovery.getNewPhone())) {
            JedisData.deleteJedis(RecoveryCode.class, email);
            throw new AlreadyExistsException("That phone number already belongs to an account");
        }

        Optional<User> user = JedisData.getEntityById(User.class, recovery.getUserName());
        if (user.isEmpty()) {
            JedisData.deleteJedis(RecoveryCode.class, email);
            throw new InvalidCodeException("This recovery can't be finished. Start again.");
        }

        UserPhoneService.moveToPhone(user.get(), recovery.getNewPhone(), recovery.getNewRegion());
        JedisData.deleteJedis(RecoveryCode.class, email);   // used up: can't be replayed

        return TokenService.createUserTokenSpecificTimeout(user.get().getUserName(),
                System.currentTimeMillis() + TOKEN_LIFETIME_MS);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Same rules as sign-up codes: same message for missing, expired, wrong stage or wrong code,
     * every wrong guess counted, and the recovery deleted after 5.
     */
    private static RecoveryCode checkCode(String email, String expectedStage, String attempt) throws Exception {
        final String wrong = "That code is wrong or has expired. Request a new code.";

        Optional<RecoveryCode> saved = JedisData.getEntityById(RecoveryCode.class, email);
        if (saved.isEmpty() || !expectedStage.equals(saved.get().getStage())) {
            throw new InvalidCodeException(wrong);
        }
        RecoveryCode recovery = saved.get();

        if (System.currentTimeMillis() > recovery.getExpiresAt()) {
            JedisData.deleteJedis(RecoveryCode.class, email);
            throw new InvalidCodeException(wrong);
        }

        if (!recovery.getCode().equals(attempt)) {
            recovery.setAttempts(recovery.getAttempts() + 1);
            if (recovery.getAttempts() >= MAX_WRONG_GUESSES) {
                JedisData.deleteJedis(RecoveryCode.class, email);
                throw new InvalidCodeException("Too many wrong codes. Start again.");
            }
            save(recovery);
            throw new InvalidCodeException(wrong);
        }
        return recovery;
    }

    private static RecoveryRequest read(String requestBody) {
        RecoveryRequest request = gson.fromJson(requestBody, RecoveryRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("A recovery email is required");
        }
        return request;
    }

    private static void save(RecoveryCode recovery) throws Exception {
        JedisData.loadToJedis(recovery, recovery.getRecoveryEmail(), recovery.getExpiresAt());
    }
}
