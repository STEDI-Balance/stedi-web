package com.getsimplex.steptimer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** The input rules used by sign-up and recovery (SignupService helpers). */
class SignupRulesTest {

    // @ParameterizedTest runs the same check once for every value listed.
    @ParameterizedTest
    @ValueSource(strings = {"8015550100", "801-555-0100", "(801) 555-0100", "801.555.0100", "+1 801 555 0100", "1-801-555-0100"})
    void anyCommonPhoneStyleBecomesTheStandardSpelling(String typed) throws Exception {
        assertEquals("+1801-555-0100", SignupService.standardPhone(typed, "US"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"801-555-010", "12345", "banana"})
    void numbersThatArentRealAreRefused(String typed) {
        assertThrows(Exception.class, () -> SignupService.standardPhone(typed, "US"));
    }

    @Test
    void aMissingPhoneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> SignupService.standardPhone("  ", "US"));
        assertThrows(IllegalArgumentException.class, () -> SignupService.standardPhone(null, "US"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"alexmorgan", "alex_morgan", "am1", "a_very_long_username_of_30_chr"})
    void goodUsernames(String userName) {
        assertTrue(SignupService.USERNAME_RULE.matcher(userName).matches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"am", "alex morgan", "alex@example.com", "alex-morgan", "AlexMorgan", "a_very_long_username_of_31_chrs"})
    void badUsernames(String userName) {
        // Capital letters fail here because verify() lower-cases the username before checking it.
        assertFalse(SignupService.USERNAME_RULE.matcher(userName).matches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex@example.com", "a.b+stedi@clinic.org"})
    void goodEmails(String email) {
        assertTrue(SignupService.EMAIL_RULE.matcher(email).matches());
    }

    @ParameterizedTest
    @ValueSource(strings = {"alex", "alex@", "alex@example", "alex @example.com", "a@@b.com"})
    void badEmails(String email) {
        assertFalse(SignupService.EMAIL_RULE.matcher(email).matches());
    }

    @Test
    void codesAreAlwaysFourDigits() {
        for (int i = 0; i < 1000; i++) {
            assertTrue(SignupService.newFourDigitCode().matches("\\d{4}"));
        }
    }

    @Test
    void regionDefaultsToUS() {
        assertEquals("US", SignupService.regionOrUS(null));
        assertEquals("US", SignupService.regionOrUS(" "));
        assertEquals("GB", SignupService.regionOrUS(" gb "));
    }
}
