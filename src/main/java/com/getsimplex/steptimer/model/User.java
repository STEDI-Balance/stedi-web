//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.model;

import javax.crypto.SecretKey;
import java.util.Calendar;
import java.util.Date;

/**
 * Created by sean on 9/7/2016.
 */
public class User {

    private String userName;
    private String password;
    private String verifyPassword;
    private String email;
    private String phone;
    private String whatsAppPhone;
    private String  birthDate;
    private String deviceNickName;
    private boolean locked;

    private long agreedToTextMessageDate;
    private long agreedToPrivacyPolicyDate;

    private long agreedToCookiePolicyDate;

    private long agreedToTermsOfUseDate;


    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVerifyPassword() {
        return verifyPassword;
    }

    public void setVerifyPassword(String verifyPassword) {
        this.verifyPassword = verifyPassword;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getWhatsAppPhone() {
        return whatsAppPhone;
    }

    public void setWhatsAppPhone(String whatsAppPhone) {
        this.whatsAppPhone = whatsAppPhone;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public String getDeviceNickName() {
        return deviceNickName;
    }

    public void setDeviceNickName(String deviceNickName) {
        this.deviceNickName = deviceNickName;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public long getAgreedToPrivacyPolicyDate() {
        return agreedToPrivacyPolicyDate;
    }

    public void setAgreedToPrivacyPolicyDate(long agreedToPrivacyPolicyDate) {
        this.agreedToPrivacyPolicyDate = agreedToPrivacyPolicyDate;
    }

    public long getAgreedToCookiePolicyDate() {
        return agreedToCookiePolicyDate;
    }

    public void setAgreedToCookiePolicyDate(long agreedToCookiePolicyDate) {
        this.agreedToCookiePolicyDate = agreedToCookiePolicyDate;
    }

    public long getAgreedToTermsOfUseDate() {
        return agreedToTermsOfUseDate;
    }

    public void setAgreedToTermsOfUseDate(long agreedToTermsOfUseDate) {
        this.agreedToTermsOfUseDate = agreedToTermsOfUseDate;
    }

    public long getAgreedToTextMessageDate() {
        return agreedToTextMessageDate;
    }

    public void setAgreedToTextMessageDate(long agreedToTextMessageDate) {
        this.agreedToTextMessageDate = agreedToTextMessageDate;
    }
}
