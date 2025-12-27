package com.lineclear;

import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.*;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;

import java.io.InputStream;
import java.util.Scanner;

public class credentialValidator extends FormValidator {
    @Override
    public boolean validate(Element element, FormData formData, String[] values) {
        boolean result = true;

        // Extract password from this element (field validator)
        String password = (values != null && values.length > 0) ? values[0] : "";
        //LogUtil.info(getClassName(), "Extracted password from field: " + password);

        // Optional: Check for mandatory
        if (password.trim().isEmpty()) {
            //LogUtil.info(getClassName(), "Password is empty, adding form error.");
            formData.addFormError(FormUtil.getElementParameterName(element), "Password is required.");
            return false;
        }

        // Find the email field element
        String emailFieldId = "email"; // <-- change this to your actual email field ID
        Form form = FormUtil.findRootForm(element);
        Element emailElement = FormUtil.findElement(emailFieldId, form, formData);

        String email = null;
        if (emailElement != null) {
            //LogUtil.info(getClassName(), "Found email field with ID: " + emailFieldId);
            String[] emailValues = FormUtil.getElementPropertyValues(emailElement, formData);
            email = (emailValues != null && emailValues.length > 0) ? emailValues[0] : null;
            //LogUtil.info(getClassName(), "Extracted email value: " + email);
        } else {
            //LogUtil.info(getClassName(), "Email field not found for ID: " + emailFieldId);
        }

        //LogUtil.info(getClassName(), "Validating Line Clear credentials - Email: " + email + ", Password: " + password);

        // Basic check
        if (email == null || email.trim().isEmpty()) {
            formData.addFormError(FormUtil.getElementParameterName(element), "Email is missing or empty.");
            return false;
        }

        // TODO: Perform Line Clear API call here
        // - Encode "username|password|BearerToken"
        try{
            String bearerToken = "SjOJmWI0cx9mIn2fT1Mqoi5LS5edDOdKka2N6LtdJqNmHIvn0uoTticZ$VibQBJh";
            String rawAuth = email+"|"+password+"|"+bearerToken;

            //LogUtil.info(getClassName(),"Raw auth String:"+rawAuth);

            String encodedAuth = java.util.Base64.getEncoder().encodeToString(rawAuth.getBytes("UTF-8"));
            //LogUtil.info(getClassName(),"Encoded auth:"+encodedAuth);

//            String apiUrl = "https://uat-app.lineclearexpressonline.com/DownloadWaybill";
            String apiUrl = " https://app.lineclearexpressonline.com/DownloadWaybill";
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", encodedAuth);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonBody = "{ \"WayBillType\": \"Parent Waybills\", \"WayBills\": \"\", \"PrintOption\": \"LC WB\" }";

            // Log the request body for debugging
            //LogUtil.info(getClassName(), "Sending JSON request body: " + jsonBody);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            //LogUtil.info(getClassName(), "API Response Code: " + responseCode);

            InputStream responseStream = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
            Scanner scanner = new Scanner(responseStream).useDelimiter("\\A");
            String responseBody = scanner.hasNext() ? scanner.next() : "";

            //LogUtil.info(getClassName(), "API Response Body: " + responseBody);

            if (responseCode == 403) {
                formData.addFormError(FormUtil.getElementParameterName(element), "Password must match with Line Clear Account.");
                return false;
            } else if (responseCode != 400) {
                formData.addFormError(FormUtil.getElementParameterName(element), "Something unexpected happened, please try again: " + responseCode);
                return false;
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Error validating Line Clear credentials");
            formData.addFormError(FormUtil.getElementParameterName(element), "System error occurred.");
            return false;
        }
        // - Make POST request
        // - If 403 Forbidden, return false with form error
        // - Else return true

        //return true; // For now until API call is added
        return result;
    }
//return validate(element, formData, values);
// Final call required by Joget to run this block

//    public boolean validate(Element element, FormData formData, String[] strings) {
//
//
//        // Get the password value (this field being validated)
//        String password = FormUtil.getElementPropertyValue(element, formData);
//
//        // Mandatory field check for password
//        if (password == null || password.trim().isEmpty()) {
//            formData.addFormError(element.getPropertyString("id"), "Password is required.");
//            return false;
//        }
//
//        String primaryKey = formData.getPrimaryKeyValue(); // This gets the record's primary key (submission ID)
//        String formId = "form_oms_user2"; // Joget form ID (used in load)
//        String tableName = "app_fd_shopUpload_omsUser"; // Database table name
//
//        String email = null;
//
//        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
//
//        if (primaryKey!= null && !primaryKey.isEmpty()) {
//            FormRow row = formDataDao.load(formId, tableName, primaryKey);
//            if (row != null) {
//                email = row.getProperty("c_email"); // Replace with your actual email field ID
//            }
//        }
//
//        // Get email from the same form (different field)
////        FormRow formRow = formData.getFormResult(formId);
////        String email = null;
////        if (formRow != null) {
////
////            email = formRow.getProperty("email"); // Replace "email" with your actual field ID
////        }
//
//        LogUtil.info(getClassName(), "Email: " + primaryKey);
//        LogUtil.info(getClassName(), "Email1: " + email);
//        LogUtil.info(getClassName(), "Password: " + password);
//
//        // Bearer token - static for now
//        String bearerToken = "SjOJmWI0cx9mIn2fT1Mqoi5LS5edDOdKka2N6LtdJqNmHIvn0uoTticZ$VibQBJh";
//
//        // You can continue your API request logic from here...
//
//        return true; // Change to false if validation fails (e.g., from API result)
//    }

    @Override
    public String getName() {
        return "LineClear OMS Account Valiation";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getLabel() {
        return  getClass().getName();
    }

    @Override
    public String getClassName() {
        return "";
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

}
