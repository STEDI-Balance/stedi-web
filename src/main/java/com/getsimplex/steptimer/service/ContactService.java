package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.EmailMessage;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.SendGmail;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * POST /contact: the "Contact us" form.
 *
 * Older apps send {name, toAddress, subject, messageText} and keep working exactly as before.
 * The redesigned app can also send {category, replyEmail}; the category is added to the
 * subject and the message so support can sort messages at a glance.
 *
 * Delivery still uses SendGmail.send() (the sponsor's current setup, which CCs info@stedi.me),
 * so nothing changes on the live server. On a local computer, set CONTACT_EMAIL_DISABLED=true
 * (run-local.ps1 does) to log the email instead of really sending it.
 */
public class ContactService {

    private static final Gson gson = new Gson();
    private static final Logger logger = Logger.getLogger(ContactService.class.getName());

    // Figma "How can we help?" topics -> the label put in the email.
    private static final Map<String, String> CATEGORIES = Map.of(
            "app", "Issues with the app",
            "device", "Issues with your device",
            "account", "Account & family",
            "data", "Data & scores");
    private static final Pattern EMAIL_RULE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int MAX_MESSAGE_LENGTH = 2000;
    static final int MAX_SUBJECT_LENGTH = 120;

    // One message per account every 30 seconds, so a script can't use STEDI's email to send spam.
    private static final ConcurrentHashMap<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    /** Thrown when an account sends messages too fast. The route turns it into HTTP 429. */
    public static class TooManyMessagesException extends Exception {
        public TooManyMessagesException(String message) { super(message); }
    }

    public static void send(User user, String requestBody) throws Exception {
        EmailMessage message = gson.fromJson(requestBody, EmailMessage.class);
        if (message == null) {
            throw new IllegalArgumentException("A message is required");
        }

        // Where the email goes: toAddress (older apps) or replyEmail (redesigned app).
        String toAddress = firstFilled(message.getToAddress(), message.getReplyEmail());
        if (toAddress == null || !EMAIL_RULE.matcher(toAddress).matches()) {
            throw new IllegalArgumentException("A valid reply email is required");
        }
        String text = message.getMessageText() == null ? "" : message.getMessageText().trim();
        if (text.isEmpty() || text.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Message must be 1-" + MAX_MESSAGE_LENGTH + " characters");
        }
        String subject = firstFilled(message.getSubject(), "Contact");
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("Subject must be at most " + MAX_SUBJECT_LENGTH + " characters");
        }
        String categoryLabel = null;
        if (message.getCategory() != null && !message.getCategory().isBlank()) {
            categoryLabel = CATEGORIES.get(message.getCategory().trim().toLowerCase());
            if (categoryLabel == null) {
                throw new IllegalArgumentException("category must be one of " + CATEGORIES.keySet());
            }
        }

        // Only after the message is valid: count it against this account's limit.
        RateLimiter limiter = limiters.computeIfAbsent(user.getUserName(), name -> RateLimiter.create(1.0 / 30));
        if (!limiter.tryAcquire()) {
            throw new TooManyMessagesException("Please wait a moment before sending another message");
        }

        // Put the category up front so support can sort messages, and say who sent it.
        String fullSubject = categoryLabel == null ? subject : "[" + categoryLabel + "] " + subject;
        String body = (categoryLabel == null ? "" : "Topic: " + categoryLabel + "\n")
                + "From STEDI account: " + user.getUserName() + "\n"
                + "Reply to: " + toAddress + "\n\n"
                + text;
        String name = firstFilled(message.getName(), user.getUserName());

        if ("true".equalsIgnoreCase(System.getenv("CONTACT_EMAIL_DISABLED"))) {
            logger.info("CONTACT_EMAIL_DISABLED is set, so this email was NOT sent:\n"
                    + "To: " + toAddress + "\nSubject: " + fullSubject + "\n" + body);
            return;
        }
        SendGmail.send(toAddress, body, fullSubject, name);
    }

    private static String firstFilled(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return null;
    }
}
