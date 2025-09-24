//Copyright 2021 Sean Murdock

package com.getsimplex.steptimer.model;

import java.util.Date;

/**
 * Created by sean on 9/7/2016.
 */
public class Customer {

    private String customerName;
    private String email;
    private String phone;
    private String whatsAppPhone;
    private String birthDay;
    private String region;
    private long weight;
    private long height;



    private Date lastWalkerDate;

    private String gender;
    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
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

    public String getBirthDay() {
        return birthDay;
    }

    public void setBirthDay(String birthDay) {
        this.birthDay = birthDay;
    }

    public String getGender(){ return gender; }
    public void setGender(String gender) {
        this.gender = gender;
    }

    public Date getLastWalkerDate() {
        return lastWalkerDate;
    }

    public void setLastWalkerDate(Date lastWalkerDate) {
        this.lastWalkerDate = lastWalkerDate;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public long getWeight() {
        return weight;
    }

    public void setWeight(long weight) {
        this.weight = weight;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

}
