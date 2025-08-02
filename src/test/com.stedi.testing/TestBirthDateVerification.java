package com.stedi.testing;

import com.getsimplex.steptimer.service.BirthDateVerificationService;
import java.lang.reflect.Method;

public class TestBirthDateVerification {
    
    public static void main(String[] args) {
        testBirthDateVerification();
    }
    
    public static void testBirthDateVerification() {
        try {
            // Use reflection to access the private verifyBirthDate method for testing
            Method verifyMethod = BirthDateVerificationService.class.getDeclaredMethod("verifyBirthDate", String.class, String.class);
            verifyMethod.setAccessible(true);
            
            // Test cases
            System.out.println("Testing birth date verification:");
            
            // Test 1: Valid match - birth date 1990-06-15 with input 06151990
            boolean result1 = (Boolean) verifyMethod.invoke(null, "1990-06-15", "06151990");
            System.out.println("Test 1 (1990-06-15 vs 06151990): " + result1 + " (Expected: true)");
            
            // Test 2: Valid match - birth date 2000-01-01 with input 01012000
            boolean result2 = (Boolean) verifyMethod.invoke(null, "2000-01-01", "01012000");
            System.out.println("Test 2 (2000-01-01 vs 01012000): " + result2 + " (Expected: true)");
            
            // Test 3: Valid match - birth date 1985-12-25 with input 12251985
            boolean result3 = (Boolean) verifyMethod.invoke(null, "1985-12-25", "12251985");
            System.out.println("Test 3 (1985-12-25 vs 12251985): " + result3 + " (Expected: true)");
            
            // Test 4: Invalid - wrong day
            boolean result4 = (Boolean) verifyMethod.invoke(null, "1990-06-15", "06161990");
            System.out.println("Test 4 (1990-06-15 vs 06161990): " + result4 + " (Expected: false)");
            
            // Test 5: Invalid - wrong month
            boolean result5 = (Boolean) verifyMethod.invoke(null, "1990-06-15", "07151990");
            System.out.println("Test 5 (1990-06-15 vs 07151990): " + result5 + " (Expected: false)");
            
            // Test 6: Invalid - wrong year
            boolean result6 = (Boolean) verifyMethod.invoke(null, "1990-06-15", "06151991");
            System.out.println("Test 6 (1990-06-15 vs 06151991): " + result6 + " (Expected: false)");
            
            // Test 7: Edge case - year 2025 with input 01012025
            boolean result7 = (Boolean) verifyMethod.invoke(null, "2025-01-01", "01012025");
            System.out.println("Test 7 (2025-01-01 vs 01012025): " + result7 + " (Expected: true)");
            
            // Test 8: Edge case - year 1931 with input 01011931
            boolean result8 = (Boolean) verifyMethod.invoke(null, "1931-01-01", "01011931");
            System.out.println("Test 8 (1931-01-01 vs 01011931): " + result8 + " (Expected: true)");
            
            System.out.println("\nBirth date verification tests completed!");
            
        } catch (Exception e) {
            System.err.println("Error running tests: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
