package com.getsimplex.steptimer.service;

import com.getsimplex.steptimer.model.Customer;
import com.getsimplex.steptimer.model.User;
import com.getsimplex.steptimer.utils.JedisData;

import java.util.Optional;

/**
 * Moves an account to a new phone number. Same steps as Sean's phone change in
 * PATCH /user/:username (feature/settings-save-flow), pulled into one method so
 * account recovery can use it too.
 */
public class UserPhoneService {

    /** newPhone must already be in the standard spelling, e.g. +1801-555-0177. */
    public static void moveToPhone(User user, String newPhone, String region) throws Exception {
        String oldPhone = user.getPhone();

        // 1. Update the User and save it again. Re-saving with the new phone digits as the
        //    score moves it in the User sorted set, so SMS login finds it at the new number.
        user.setPhone(newPhone);
        user.setWhatsAppPhone(newPhone);
        user.setRegion(region);
        long newDigits = Long.parseLong(newPhone.replaceAll("[^0-9]", ""));
        JedisData.loadToJedis(user, user.getUserName(), newDigits);

        // 2. The Customer record is saved under the phone number, so it moves too:
        //    save it under the new phone, then delete the old one.
        if (oldPhone != null) {
            Optional<Customer> customerOptional = CustomerService.findCustomerByPhone(oldPhone);
            if (customerOptional.isPresent()) {
                Customer customer = customerOptional.get();
                customer.setPhone(newPhone);
                customer.setWhatsAppPhone(newPhone);
                customer.setRegion(region);
                CustomerService.createOrUpdateCustomer(customer, true);
                CustomerService.deleteCustomer(oldPhone);
            }
        }
        // Known gap (also true for PATCH /user): older Rapid Step Tests are stored under
        // the old phone number ($.<phone> in RapidStepTest) and are not moved. The new
        // BalanceTest records (step 4) are stored under a profile ID, so they won't have this problem.
    }
}
