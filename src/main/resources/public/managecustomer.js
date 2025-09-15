//Copyright 2021 Sean Murdock

let customerName = "";
let phone = "";
let whatsAppPhone="";
let bday = "";
let form = "";
let elements = "";
let deviceNickName = "";


const checkBoxListener = ()=>{
    const signUpButton = document.getElementById("createbtn");
    const textMessagesCheckBox = document.getElementById("textMessageCheckBox");
    const privacyCheckBox = document.getElementById("privacyCheckBox");
    const termsOfUseCheckBox = document.getElementById("termsOfUseCheckBox");
    const cookiePolicyCheckBox = document.getElementById("cookiePolicyCheckBox");

    if(textMessagesCheckBox.checked && privacyCheckBox.checked && termsOfUseCheckBox.checked && cookiePolicyCheckBox.checked){
        signUpButton.disabled=false;
    } else{
        signUpButton.disabled=true;
    }
}

$(document).ready(function(){

   const privacyCheckBox = document.getElementById("privacyCheckBox");
   privacyCheckBox.addEventListener('change',checkBoxListener);

   const termsOfUseCheckBox = document.getElementById("termsOfUseCheckBox");
   termsOfUseCheckBox.addEventListener('change',checkBoxListener);

   const cookiePolicyCheckBox = document.getElementById("cookiePolicyCheckBox");
   cookiePolicyCheckBox.addEventListener('change',checkBoxListener);

});

function setcustomername(){
    customerName = $("#cn").val();
}

function setemail(){
    email = $("#email").val();
}

function setphone(){
    phone = $("#phone").val().replace(/\D+/g, "");
    whatsAppPhone = phone;
}

function setDeviceNickName(){
    deviceNickName = $("#deviceNickName").val();
}


function setbday(){
    bday = $("#bday").val();
}

function setregion() {
    region = $("#countryCode").val();
}

function readonlyforms(formid){
    form = document.getElementById(formid);
    elements = form.elements;
    for (i = 0, len = elements.length; i < len; ++i) {
    elements[i].readOnly = true;
    }
    createbutton();
}
 function pwsDisableInput( element, condition ) {
        if ( condition == true ) {
            element.disabled = true;

        } else {
            element.removeAttribute("disabled");
        }

 }

function createbutton(){
    var button = document.createElement("input");
    button.type = "button";
    button.value = "OK";
    button.onclick = window.location.href = "/index.html";
    context.appendChild(button);
}

function findcustomer(email){
    var headers = { "suresteps.session.token": localStorage.getItem("token")};
    $.ajax({
        type: 'GET',
        url: `/customer/${email}`,
        contentType: 'application/text',
        dataType: 'text',
        headers: headers,
        success: function(data) {
            localStorage.setItem("customer",data);
            window.location.href="/timer.html";
        }
    });
}

function createcustomer() {
    // In case they hit the back/forward buttons and our in-memory variables got reset
    setusername();
    setuserpassword();
    setverifypassword();
    setcustomername();
    setemail();
    setphone();
    setDeviceNickName();
    setbday();
    setregion();

    // This is the more picky of the two operations, so let's try it first, and if it succeeds, create the customer, not vice versa
    $.ajax({
        type: 'POST',
        url: '/user',
        data: JSON.stringify({
            'userName': email,
            email,
            password,
            phone,
            deviceNickName,
            birthDate: bday,
            verifyPassword,
            agreedToTermsOfUseDate: new Date().getTime(),
            agreedToCookiePolicyDate: new Date().getTime(),
            agreedToPrivacyPolicyDate: new Date().getTime(),
            agreedToTextMessageDate: new Date().getTime(),
            whatsAppPhone,
            region
        }), // We are using the email as the user name
        success: function (loginToken) {
            createCustomer(loginToken);
        },
        error: function (xhr) {
            console.log(JSON.stringify(xhr))
            if (xhr.status == 409) {
                alert("Email or cell # has already been previously registered");
            } else {
                alert("Error creating account. Please confirm password is at least 6 characters, has an upper case letter, a lower case letter, a number, and a symbol.")
            }
        },
        contentType: "application/text",
        dataType: 'text'
    });
}

const createCustomer = (loginToken) => {
    const customer = {
        customerName: customerName,
        email: email,
        phone: phone,
        birthDay: bday,
        whatsAppPhone,
        region
    }

    $.ajax({
        type: 'POST',
        url: '/customer',
        data: JSON.stringify(customer),
        contentType: 'application/text',
        dataType: 'text',
        headers: {
            "suresteps.session.token": loginToken
        },
        success: function (data) {
            localStorage.setItem("customer", JSON.stringify(customer));
            alert("Successfully created user, please login to get started.");
            window.location.href = "/index.html"
        },
        error: function (xhr) {
            console.log(JSON.stringify(xhr))
            if (xhr.status == 409) {
                alert("Email or cell # has already been previously registered");
            } else {
                alert("Error creating account. Please confirm information is correct.")
            }
        }
    });
}