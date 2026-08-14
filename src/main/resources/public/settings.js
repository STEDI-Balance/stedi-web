//Copyright 2021 Sean Murdock

let settingsToken = "";
let settingsUserName = "";
let currentSettingsUser = null;
let verifiedPhoneStored = "";
let verifiedRegionStored = "US";

const digitsOnly = (s) => String(s || "").replace(/\D/g, "");

/**
 * Best-effort match for display vs stored E.164-style numbers (avoids forcing OTP when only formatting differs).
 */
const phonesLookEquivalent = (input, stored, region) => {
    const a = digitsOnly(input);
    const b = digitsOnly(stored);
    if (!a || !b) return a === b;
    if (a === b) return true;
    const reg = (region || "US").toUpperCase();
    if (reg === "US" || reg === "PR" || reg === "CA") {
        if (a.length === 10 && b.length === 11 && b.startsWith("1") && b.slice(1) === a) return true;
        if (b.length === 10 && a.length === 11 && a.startsWith("1") && a.slice(1) === b) return true;
    }
    return false;
};

const isPhoneOrRegionChangeRequested = () => {
    if (!$("#changePhoneToggle").is(":checked")) return false;
    const region = ($("#countryCode").val() || "US").trim();
    const storedReg = (verifiedRegionStored || "US").trim();
    if (region !== storedReg) return true;
    const phone = ($("#phone").val() || "").trim();
    return !phonesLookEquivalent(phone, verifiedPhoneStored, region);
};

$(document).ready(function(){
    const hashparts = location.hash.split("#");
    if (hashparts.length < 2 || hashparts[1] === "") {
        window.location = "/";
        return;
    }

    settingsToken = hashparts[1];
    $("#backToTimer").attr("href", "/timer.html#" + settingsToken);
    $("#sendVerificationBtn").on("click", sendPhoneVerification);
    $("#saveSettingsBtn").on("click", saveSettings);
    $("#changePhoneToggle").on("change", onChangePhoneToggle);
    validateSettingsToken();
});

const settingsHeaders = () => {
    return {
        "suresteps.session.token": settingsToken,
        "content-type": "application/json"
    };
};

const validateSettingsToken = () => {
    $.ajax({
        type: "GET",
        url: "/validate/" + settingsToken,
        success: function(data){
            if (data === "") {
                window.location = "/";
                return;
            }

            settingsUserName = data;
            $("#settingsEmail").html(data);
            loadSettingsUser();
        },
        error: function(){
            window.location = "/";
        },
        contentType: "application/text",
        dataType: "text"
    });
};

const applyVerifiedDisplay = () => {
    const label = verifiedPhoneStored ? verifiedPhoneStored : "(none on file)";
    $("#verifiedPhoneLabel").text(label);
    const hint = verifiedRegionStored ? " (region " + verifiedRegionStored + ")" : "";
    $("#verifiedRegionHint").text(hint);
};

const onChangePhoneToggle = () => {
    const on = $("#changePhoneToggle").is(":checked");
    $("#phoneChangePanel").toggle(on);
    if (on) {
        $("#phone").val(verifiedPhoneStored || "");
        $("#countryCode").val(verifiedRegionStored || "US");
        $("#oneTimePassword").val("");
    } else {
        $("#oneTimePassword").val("");
    }
};

const loadSettingsUser = () => {
    $.ajax({
        type: "GET",
        url: "/user/" + encodeURIComponent(settingsUserName),
        headers: settingsHeaders(),
        success: function(data) {
            currentSettingsUser = typeof data === "string" ? JSON.parse(data) : data;
            $("#deviceNickName").val(currentSettingsUser.deviceNickName || "");
            verifiedPhoneStored = currentSettingsUser.phone || "";
            verifiedRegionStored = (currentSettingsUser.region || "US").trim() || "US";
            applyVerifiedDisplay();
            $("#changePhoneToggle").prop("checked", false);
            $("#phoneChangePanel").hide();
            $("#phone").val("");
            $("#countryCode").val(verifiedRegionStored);
            $("#oneTimePassword").val("");
        },
        error: function(){
            alert("Unable to load settings.");
            window.location = "/";
        },
        contentType: "application/text",
        dataType: "text"
    });
};

const sendPhoneVerification = () => {
    if (!$("#changePhoneToggle").is(":checked")) {
        alert('Turn on "Change cell number or country code" before requesting a code.');
        return;
    }

    const phone = $("#phone").val();
    const region = $("#countryCode").val() || "US";

    if (!phone || !phone.trim()) {
        alert("Enter the new cell number before requesting a verification code.");
        return;
    }

    $.ajax({
        type: "POST",
        url: "/user/" + encodeURIComponent(settingsUserName) + "/phone-verification" + (region !== "US" ? "?whatsApp=true" : ""),
        headers: settingsHeaders(),
        data: JSON.stringify({
            phone: phone.trim(),
            region: region.trim()
        }),
        success: function(){
            alert("Verification code sent.");
        },
        error: function(xhr){
            if (xhr.status === 409) {
                alert("That cell number is already registered.");
            } else {
                alert("Unable to send verification code. Please confirm the number and country code.");
            }
        },
        contentType: "application/json",
        dataType: "text"
    });
};

const saveSettings = () => {
    const needsPhoneVerify = isPhoneOrRegionChangeRequested();
    const oneTimePassword = $("#oneTimePassword").val();

    if (needsPhoneVerify) {
        if (!oneTimePassword || !String(oneTimePassword).trim()) {
            alert("Request a verification code for the new number and enter it here before saving.");
            return;
        }
    }

    const userUpdate = {
        deviceNickName: $("#deviceNickName").val()
    };

    if (needsPhoneVerify) {
        const phone = $("#phone").val();
        const region = ($("#countryCode").val() || "US").trim();
        userUpdate.phone = phone.trim();
        userUpdate.whatsAppPhone = phone.trim();
        userUpdate.region = region;
        userUpdate.oneTimePassword = Number(String(oneTimePassword).trim());
        if (!userUpdate.phone) {
            alert("Enter the new cell number.");
            return;
        }
        if (Number.isNaN(userUpdate.oneTimePassword)) {
            alert("Enter the numeric verification code.");
            return;
        }
    }

    $.ajax({
        type: "PATCH",
        url: "/user/" + encodeURIComponent(settingsUserName),
        headers: settingsHeaders(),
        data: JSON.stringify(userUpdate),
        success: function(){
            alert("Settings saved.");
            loadSettingsUser();
        },
        error: function(xhr){
            if (xhr.status === 400) {
                alert("Unable to save. If changing the cell number, confirm the verification code is correct.");
            } else if (xhr.status === 409) {
                alert("That cell number is already registered.");
            } else {
                alert("Unable to save settings.");
            }
        },
        contentType: "application/json",
        dataType: "text"
    });
};
