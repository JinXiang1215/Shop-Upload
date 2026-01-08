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

        
        String password = (values != null && values.length > 0) ? values[0] : "";
        

        if (password.trim().isEmpty()) {
            formData.addFormError(FormUtil.getElementParameterName(element), "Password is required.");
            return false;
        }

       
        String emailFieldId = "email"; // <-- change this to your actual email field ID
        Form form = FormUtil.findRootForm(element);
        Element emailElement = FormUtil.findElement(emailFieldId, form, formData);

        String email = null;
        if (emailElement != null) {
            
            String[] emailValues = FormUtil.getElementPropertyValues(emailElement, formData);
            email = (emailValues != null && emailValues.length > 0) ? emailValues[0] : null;
            
        } else {
            
        }

 

       
        if (email == null || email.trim().isEmpty()) {
            formData.addFormError(FormUtil.getElementParameterName(element), "Email is missing or empty.");
            return false;
        }

   
        try{
            String bearerToken = "YOUR_API_KEY_HERE";
            String rawAuth = email+"|"+password+"|"+bearerToken;

            

            String encodedAuth = java.util.Base64.getEncoder().encodeToString(rawAuth.getBytes("UTF-8"));
            


            String apiUrl = " https://app.lineclearexpressonline.com/DownloadWaybill";
            java.net.URL url = new java.net.URL(apiUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", encodedAuth);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonBody = "{ \"WayBillType\": \"Parent Waybills\", \"WayBills\": \"\", \"PrintOption\": \"LC WB\" }";

           
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }

            int responseCode = conn.getResponseCode();
            

            InputStream responseStream = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
            Scanner scanner = new Scanner(responseStream).useDelimiter("\\A");
            String responseBody = scanner.hasNext() ? scanner.next() : "";

            

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
        

        
        return result;
    }


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


