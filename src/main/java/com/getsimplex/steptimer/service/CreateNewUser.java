//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.service;

import com.google.gson.Gson;
import com.getsimplex.steptimer.model.User;
import org.apache.commons.codec.digest.DigestUtils;
import spark.Request;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.getsimplex.steptimer.utils.*;

import java.util.function.Predicate;

/**
 * Created by Mandy on 10/4/2016.
 */
public class CreateNewUser {

    private static Gson gson = new Gson();
    private static JedisClient jedisClient = new JedisClient();

    public static String handleRequest (Request request) throws Exception{
        String newUserRequest = request.body();
        User createUser = gson.fromJson(newUserRequest, User.class);
        return createUser(createUser);
    }

    public static void deleteUser(String userName) throws Exception{
        JedisData.deleteJedis(User.class, userName);
    }

    public static void updatePassword(String userName, String password) throws Exception{
        JedisData.getFromRedisMap(userName, User.class);
    }
    public static String createUser(User createUser) throws Exception{
        if(createUser.getPhone().isEmpty() && !createUser.getWhatsAppPhone().isEmpty()){
            createUser.setPhone(createUser.getWhatsAppPhone());
        }

        User addUser = new User();

        addUser.setLocked(false);//new users start off with a clean slate

        String userName = createUser.getUserName();
        String password = createUser.getPassword();
        String verifyPassword = createUser.getVerifyPassword();
        String email = createUser.getEmail();
        String phone = !createUser.getPhone().isEmpty() ?SendText.getFormattedPhone(createUser.getPhone(), createUser.getRegion()):null;
        String standardizedPhoneDigitsOnly= phone.replaceAll("[^0-9]","");
        String bday = createUser.getBirthDate();
        String deviceId = createUser.getDeviceNickName();


        if (userName != null && !userName.isEmpty()) {

            List<User> existingUsers = JedisData.getEntitiesByScore(User.class,  Long.valueOf(standardizedPhoneDigitsOnly), Long.valueOf(standardizedPhoneDigitsOnly));

            Optional<User> existingUser = JedisData.getEntityById(User.class, createUser.getEmail());
            if (existingUsers.size()>0 || existingUser.isPresent()) {

                throw new AlreadyExistsException("Username already exists");

            } else {
                addUser.setUserName(userName);
            }
        }

        if(!password.equals(verifyPassword)){ //add validation - create method complexPW
            throw new Exception("Passwords do not match");
        }else if(!validatePassword(password)) {
            throw new Exception ("Your password must contain a lowercase and uppercase letter, a number, a special character, and be between 6 and 40 characters long.");
        }else{
            String sha256HashPass = DigestUtils.sha256Hex(verifyPassword);
            addUser.setPassword(sha256HashPass);
            addUser.setVerifyPassword(sha256HashPass);
        }

        if (email != null && !email.isEmpty()) {
                addUser.setEmail(email);
        }

        if (phone != null && !phone.isEmpty()) {

            addUser.setPhone(phone);
        }

        if(createUser.getWhatsAppPhone() != null && !createUser.getWhatsAppPhone().isEmpty()){
            String whatsAppPhone = SendText.getFormattedPhone(createUser.getWhatsAppPhone(), createUser.getRegion());
            addUser.setWhatsAppPhone(whatsAppPhone);
        }

        if (bday != null ){
            String birthdate = bday;
            addUser.setBirthDate(birthdate);
        }

        if (deviceId != null && !deviceId.isEmpty()){
            addUser.setDeviceNickName(deviceId);
        }

        addUser.setAgreedToTermsOfUseDate(createUser.getAgreedToTermsOfUseDate());
        addUser.setAgreedToCookiePolicyDate(createUser.getAgreedToCookiePolicyDate());
        addUser.setAgreedToPrivacyPolicyDate(createUser.getAgreedToPrivacyPolicyDate());
        addUser.setAgreedToTextMessageDate(createUser.getAgreedToTextMessageDate());
        addUser.setAgreedToShareAnonymousData(createUser.getAgreedToShareAnonymousData());



        //SAVE USER TO REDIS
        JedisData.loadToJedis(addUser, addUser.getUserName(), Long.valueOf(standardizedPhoneDigitsOnly));

        return TokenService.createUserToken(addUser.getUserName());
    }

    // code borrowed from http://www.java2novice.com/java-collections-and-util/regex/valid-password/
    // must contain one digit, one lower case char, one upper case char, some special chars, length should be within 6 to 15 chars.

    private static Pattern pswPtrn =
            Pattern.compile("((?=.*\\d)(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#&()–[{}]:;',?/*~$^+=<>]).{6,40})");

    // (?=.*  this means search to the right in the string for any number of characters
    // \d this means a digit (0-9)

    // so now we know we have found one digit

    // [] this means a character set
    // a-z this defines the range between a and z

    // so now we know we have found one lower case letter

    // A-Z this defines the range between a and z

    // now we know we have found one upper case letter

    // [] this means a character set
    // @#$%! this defines the set

    // now we know we have found a special character

    public static boolean validatePassword(String password){

        Matcher mtch = pswPtrn.matcher(password);
        if(mtch.matches()){
            return true;
        }
        return false;
    }
}
