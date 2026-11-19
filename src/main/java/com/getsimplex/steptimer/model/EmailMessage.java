package com.getsimplex.steptimer.model;

public class EmailMessage {
    private String messageText;
    private String toAddress;
    private String subject;

    private String name;

    // Added for the redesigned "Contact us" form (both optional, so older apps keep working):
    private String category;     // app | device | account | data  (Figma: "Issues with your device" ...)
    private String replyEmail;   // where support should reply; used as toAddress when that's left out

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getReplyEmail() { return replyEmail; }
    public void setReplyEmail(String replyEmail) { this.replyEmail = replyEmail; }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;

    }

    public String getToAddress() {
        return toAddress;
    }

    public void setToAddress(String toAddress) {
        this.toAddress = toAddress;
    }


    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
     public String getName(){
        return name;
     }
    public void setName(String name) {
        this.name = name;
    }
}
