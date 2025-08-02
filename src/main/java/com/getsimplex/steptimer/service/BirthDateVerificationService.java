//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.BirthDateVerification;
import com.getsimplex.steptimer.model.Customer;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.NotFoundException;
import com.getsimplex.steptimer.utils.SendText;
import com.google.gson.Gson;
import spark.Request;

import java.util.Date;
import java.util.Optional;
import java.util.logging.Logger;

public class BirthDateVerificationService {
    private static Gson gson = new Gson();
    private static Logger logger = Logger.getLogger(BirthDateVerificationService.class.getName());

    public static String handleRequest(Request request, String phoneNumber) throws Exception {
        logger.info("Starting birth date verification for phone: " + phoneNumber);
        
        String requestBody = request.body();
        BirthDateVerification birthDateVerification = gson.fromJson(requestBody, BirthDateVerification.class);
        
        if (birthDateVerification.getDateOfBirth() == null || birthDateVerification.getDateOfBirth().trim().isEmpty()) {
            throw new IllegalArgumentException("Date of birth is required");
        }
        
        String providedDateOfBirth = birthDateVerification.getDateOfBirth().trim();
        
        // Validate format MMDDYYYY (8 digits)
        if (!providedDateOfBirth.matches("\\d{8}")) {
            throw new IllegalArgumentException("Date of birth must be in MMDDYYYY format (8 digits)");
        }
        
        // Find user by phone number
        String region = request.queryParams("region");
        if (region == null || region.isEmpty()) {
            region = "US"; // default to US
        }
        
        String formattedPhoneNumber = SendText.getFormattedPhone(phoneNumber, region);
        User user = FindUser.getUserByPhone(formattedPhoneNumber, region);
        
        if (user == null) {
            logger.warning("Unable to find user with phone number: " + formattedPhoneNumber);
            throw new NotFoundException("Unable to find user with phone number: " + phoneNumber);
        }
        
        // Find customer associated with the user
        Optional<Customer> customerOptional = CustomerService.findCustomerByPhone(user.getPhone());
        if (!customerOptional.isPresent()) {
            logger.warning("Unable to find customer for phone: " + user.getPhone());
            throw new NotFoundException("Unable to find customer for phone: " + phoneNumber);
        }
        
        Customer customer = customerOptional.get();
        
        // Verify birth date
        if (verifyBirthDate(customer.getBirthDay(), providedDateOfBirth)) {
            logger.info("Birth date verification successful for phone: " + formattedPhoneNumber);
            
            // Create token with same expiration as 2FA (100 years as per existing twoFactorLogin method)
            Long expiration = new Date().getTime() + 100L * 365L * 24L * 60L * 60L * 1000L;
            String loginToken = TokenService.createUserTokenSpecificTimeout(user.getUserName(), expiration);
            
            logger.info("Login token created successfully for user: " + user.getUserName());
            return loginToken;
        } else {
            logger.warning("Birth date verification failed for phone: " + formattedPhoneNumber);
            throw new IllegalArgumentException("Birth date does not match our records");
        }
    }
    
    /**
     * Verify if the provided birth date matches the stored birth date
     * @param storedBirthDay The stored birth date in format YYYY-MM-DD
     * @param providedDateOfBirth The provided birth date in format MMDDYYYY
     * @return true if they match, false otherwise
     */
    private static boolean verifyBirthDate(String storedBirthDay, String providedDateOfBirth) {
        try {
            if (storedBirthDay == null || storedBirthDay.trim().isEmpty()) {
                logger.warning("Stored birth date is null or empty");
                return false;
            }
            
            // Parse stored birth date (format: YYYY-MM-DD)
            String[] storedParts = storedBirthDay.split("-");
            if (storedParts.length != 3) {
                logger.warning("Invalid stored birth date format: " + storedBirthDay);
                return false;
            }
            
            String storedYear = storedParts[0];
            String storedMonth = storedParts[1];
            String storedDay = storedParts[2];
            
            // Parse provided birth date (format: MMDDYYYY)
            String providedMonth = providedDateOfBirth.substring(0, 2);
            String providedDay = providedDateOfBirth.substring(2, 4);
            String providedYear = providedDateOfBirth.substring(4, 8);
            
            // Compare the dates
            boolean matches = storedYear.equals(providedYear) && 
                            storedMonth.equals(providedMonth) && 
                            storedDay.equals(providedDay);
            
            logger.info("Birth date comparison - Stored: " + storedBirthDay + 
                       ", Provided (converted): " + providedYear + "-" + providedMonth + "-" + providedDay + 
                       ", Match: " + matches);
            
            return matches;
            
        } catch (Exception e) {
            logger.severe("Error verifying birth date: " + e.getMessage());
            return false;
        }
    }
}
