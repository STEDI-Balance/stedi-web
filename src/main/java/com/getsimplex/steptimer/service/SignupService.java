package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.Customer;
import com.getsimplex.steptimer.model.SignupCode;
import com.getsimplex.steptimer.model.SignupStartRequest;
import com.getsimplex.steptimer.model.SignupVerifyRequest;
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
import java.util.regex.Pattern;

/**
 * Phone-only sign-up, in two steps:
 *   1. POST /signup/start  texts a 4-digit code to a phone number that has no account yet.
 *   2. POST /signup/verify checks the code and creates the account (no password; SMS login only).
 */
public class SignupService {

    private static final Gson gson = new Gson();
    private static final Logger logger = Logger.getLogger(SignupService.class.getName());
    private static final SecureRandom random = new SecureRandom();

    static final long CODE_LIFETIME_MS = 10 * 60 * 1000;   // a code works for 10 minutes
    static final long RESEND_WAIT_MS = 30 * 1000;           // matches the Figma "Resend in 0:24" countdown
    static final int MAX_WRONG_GUESSES = 5;                 // then the code is thrown away
    static final long TOKEN_LIFETIME_MS = 100L * 365 * 24 * 60 * 60 * 1000;  // 100 years, same as SMS login

    // Figma: "Letters, numbers and underscores only." 3 to 30 characters.
    // No "@" allowed, so a new username can never clash with an older account whose username is an email.
    private static final Pattern USERNAME_RULE = Pattern.compile("^[a-z0-9_]{3,30}$");
    // A simple "looks like an email" check: something@something.something
    private static final Pattern EMAIL_RULE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** Asking for a new code too soon. The route turns it into HTTP 429. */
    public static class TooSoonException extends Exception {
        public TooSoonException(String message) { super(message); }
    }

    /** Wrong, expired or missing code. The route turns it into HTTP 400. */
    public static class InvalidCodeException extends Exception {
        public InvalidCodeException(String message) { super(message); }
    }

    // ---------------------------------------------------------------- step 1

    public static void start(String requestBody) throws Exception {
        // 1. Read the JSON body into a Java object.
        SignupStartRequest request = gson.fromJson(requestBody, SignupStartRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("A phone number is required");
        }
        String region = regionOrUS(request.getRegion());

        // 2. Check it's a real number and put it in the standard spelling, e.g. +1801-555-0100.
        String phone = standardPhone(request.getPhone(), region);

        // 3. Refuse if an account already has this phone (same lookup as CreateNewUser.java).
        if (phoneIsRegistered(phone)) {
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
        //    Only 10,000 possible codes, so verify() allows just a few wrong guesses.
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

    // ---------------------------------------------------------------- step 2

    /** Checks the code, creates the account, and returns a session token (the app is logged in straight away). */
    public static String verify(String requestBody) throws Exception {
        // 1. Read the body and check every field is filled in correctly,
        //    before touching the code, so a typo in the email doesn't use up a guess.
        SignupVerifyRequest request = gson.fromJson(requestBody, SignupVerifyRequest.class);
        if (request == null) {
            throw new IllegalArgumentException("Sign-up details are required");
        }
        String region = regionOrUS(request.getRegion());
        String phone = standardPhone(request.getPhone(), region);

        String userName = clean(request.getUserName()).toLowerCase();
        if (!USERNAME_RULE.matcher(userName).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 letters, numbers or underscores");
        }
        String fullName = clean(request.getFullName());
        if (fullName.isEmpty()) {
            throw new IllegalArgumentException("Full name is required");
        }
        String recoveryEmail = clean(request.getRecoveryEmail()).toLowerCase();
        if (!EMAIL_RULE.matcher(recoveryEmail).matches()) {
            throw new IllegalArgumentException("A valid recovery email is required");
        }
        if (!request.isAgreedToTerms()) {
            throw new IllegalArgumentException("You must agree to the Terms of Use and Privacy Policy");
        }

        // 2. Check the code (counts wrong guesses; throws InvalidCodeException if it's wrong).
        SignupCode signupCode = checkCode(phone, clean(request.getCode()));

        // 3. Make sure nobody else has taken the phone, username or email in the meantime.
        //    These answer 409 but keep the code, so the person can fix the field and try again.
        if (phoneIsRegistered(phone)) {
            throw new AlreadyExistsException("Phone number is already registered");
        }
        if (JedisData.getEntityById(User.class, userName).isPresent()) {
            throw new AlreadyExistsException("That username is taken");
        }
        if (emailIsTaken(recoveryEmail)) {
            throw new AlreadyExistsException("That recovery email is already used by another account");
        }

        // 4. Create the User. No password: this account signs in with SMS codes only
        //    (LoginController refuses password login for it).
        long now = System.currentTimeMillis();
        User user = new User();
        user.setUserName(userName);
        user.setEmail(recoveryEmail);        // the recovery email lives in the existing email field
        user.setPhone(phone);
        user.setWhatsAppPhone(phone);
        user.setRegion(region);
        user.setLocked(false);
        user.setAgreedToTermsOfUseDate(now);
        user.setAgreedToPrivacyPolicyDate(now);
        user.setAgreedToTextMessageDate(now);  // they asked us to text them a code
        // Saves UserMap[userName], adds it to the User sorted set scored by phone digits
        // (so SMS login can find it), and adds the index UserByEmail-<email> (so emailIsTaken can).
        JedisData.loadToJedisWithIndex(user, userName, phoneDigits(phone), "Email", recoveryEmail);

        // 5. Create the matching Customer, which holds the person's name. The rest of the
        //    server (tests, risk score) finds people through it. Birthday, gender, height
        //    and weight get added on the next Figma screen ("Nice to meet you") via PUT /customer.
        Customer customer = new Customer();
        customer.setCustomerName(fullName);
        customer.setEmail(recoveryEmail);
        customer.setPhone(phone);
        customer.setWhatsAppPhone(phone);
        customer.setRegion(region);
        CustomerService.createOrUpdateCustomer(customer, true);  // true: replace any leftover record for this phone

        // 6. Throw the code away so it can't be used twice, and log the new account in.
        JedisData.deleteJedis(SignupCode.class, signupCode.getPhone());
        return TokenService.createUserTokenSpecificTimeout(userName, now + TOKEN_LIFETIME_MS);
    }

    /** Finds the code saved for this phone and compares it. Every wrong guess is counted. */
    private static SignupCode checkCode(String phone, String attempt) throws Exception {
        Optional<SignupCode> saved = JedisData.getEntityById(SignupCode.class, phone);
        if (saved.isEmpty()) {
            throw new InvalidCodeException("That code is wrong or has expired. Request a new code.");
        }
        SignupCode signupCode = saved.get();

        if (System.currentTimeMillis() > signupCode.getExpiresAt()) {
            JedisData.deleteJedis(SignupCode.class, phone);
            throw new InvalidCodeException("That code is wrong or has expired. Request a new code.");
        }

        if (!signupCode.getCode().equals(attempt)) {
            signupCode.setAttempts(signupCode.getAttempts() + 1);
            if (signupCode.getAttempts() >= MAX_WRONG_GUESSES) {
                JedisData.deleteJedis(SignupCode.class, phone);   // too many guesses: code is gone
                throw new InvalidCodeException("Too many wrong codes. Request a new code.");
            }
            JedisData.loadToJedis(signupCode, phone, signupCode.getExpiresAt());  // save the new count
            throw new InvalidCodeException("That code is wrong or has expired. Request a new code.");
        }
        return signupCode;
    }

    // ---------------------------------------------------------------- small helpers

    /** Checks the phone is real and returns the server's standard spelling, e.g. +1801-555-0100. */
    private static String standardPhone(String rawPhone, String region) throws Exception {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new IllegalArgumentException("A phone number is required");
        }
        // Any style works: 8015550100, 801-555-0100, (801) 555-0100, +1 801 555 0100 ...
        // but too few or too many digits (801-555-010) is refused.
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        if (!phoneUtil.isValidNumber(phoneUtil.parse(rawPhone, region))) {
            throw new IllegalArgumentException("Not a valid phone number");
        }
        return SendText.getFormattedPhone(rawPhone, region);
    }

    private static long phoneDigits(String phone) {
        return Long.parseLong(phone.replaceAll("[^0-9]", ""));   // +1801-555-0100 becomes 18015550100
    }

    private static boolean phoneIsRegistered(String phone) throws Exception {
        long digits = phoneDigits(phone);
        List<User> users = JedisData.getEntitiesByScore(User.class, digits, digits);
        return !users.isEmpty();
    }

    /** Older accounts use their email as the username; newer ones are found through the email index. */
    private static boolean emailIsTaken(String email) throws Exception {
        return JedisData.getEntityById(User.class, email).isPresent()
                || !JedisData.getEntitiesByIndex(User.class, "Email", email).isEmpty();
    }

    private static String regionOrUS(String region) {
        return (region == null || region.isBlank()) ? "US" : region.trim().toUpperCase();
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
