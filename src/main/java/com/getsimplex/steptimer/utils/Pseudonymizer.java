package com.getsimplex.steptimer.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * Replaces participant keys (phone numbers) with keyed HMAC-SHA256 pseudonyms for aggregate analysis.
 * Set MDC_PSEUDONYM_SECRET so pseudonyms are stable across restarts; without it a random
 * per-process secret is used, which is safe but changes the pseudonyms on every restart.
 */
public class Pseudonymizer {

    private static Logger logger = Logger.getLogger(Pseudonymizer.class.getName());

    private final byte[] secret;

    public Pseudonymizer(byte[] secret){
        this.secret = secret.clone();
    }

    public static Pseudonymizer fromEnvironment(){
        String secret = System.getenv("MDC_PSEUDONYM_SECRET");
        if (secret == null || secret.isEmpty()){
            logger.warning("MDC_PSEUDONYM_SECRET is not set; using a random per-process pseudonym secret");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            return new Pseudonymizer(random);
        }
        return new Pseudonymizer(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String pseudonym(String participantKey){
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "P" + hex(mac.doFinal(participantKey.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e){
            throw new IllegalStateException("Unable to create pseudonym", e);
        }
    }

    public static String sha256Hex(String value){
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e){
            throw new IllegalStateException("Unable to hash value", e);
        }
    }

    private static String hex(byte[] bytes){
        StringBuilder builder = new StringBuilder();
        for (byte b : bytes){
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
