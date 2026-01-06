package com.lineclear;

import jdk.jpackage.internal.Log;
import net.sf.json.JSON;
import oracle.jdbc.proxy.annotation.Pre;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.workflow.util.WorkflowUtil;
import org.joget.directory.model.User;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.json.HTTP;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.sql.DataSource;
import javax.xml.transform.Result;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class createShipment extends DefaultApplicationPlugin {

    @Override
    public String getName() {
        return "Create Shipment Shop Upload";
    }

    @Override
    public String getVersion() {
        return "7.0.0";
    }

    @Override
    public String getDescription() {
        return "To create shipment to oms system";
    }

    @Override
    public String getLabel() {
        return "Create Shipment Shop Upload";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    @Override
    public Object execute(Map map) {

        String recordId = (String)map.get("recordId");
        String formDefId = "shop_upload";
        AppDefinition appDef = AppUtil.getCurrentAppDefinition();
        FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
        FormRowSet rowSet = formDataDao.find(formDefId, "", "WHERE e.id = ?", new String[]{recordId}, null, null, null, null);

        if (rowSet == null || rowSet.isEmpty()) {
            LogUtil.warn(getClassName(), "⚠ Form data not found for record: " + recordId);
            return "Error: Form data not found for record: " + recordId;
        }

        FormRow formRow = rowSet.get(0);
        String user_Id = formRow.getProperty("c_user_id");
        String omsCode= formRow.getProperty("oms_accountNom");

  LogUtil.info(getClassName(),"Extracted oms account id: "+omsCode);

        Connection con = null;
        //String userId  =(String)map.get("")
        try {

            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();
           

            String userId=getUserIdFromShopUpload(con,recordId);
      

            //JSONObject senderInfo = getSenderInfo(con, userId);
            JSONObject senderInfo = getSenderInfo(con, omsCode);


            
            JSONArray shipments = getShipmentRecords(con, recordId);
           

            List<String> freshReferences = getFreshShipmentReferences(con, recordId);

           
            JSONArray shipmentArray = new JSONArray();
            for (int i = 0; i < shipments.length(); i++) {
                JSONObject shipment = shipments.getJSONObject(i);

                // Insert Sender info into each shipment
                shipment.put("SenderName", senderInfo.getString("SenderName"));
                shipment.put("ShipmentAddressFrom", senderInfo.getJSONObject("Address"));

                shipmentArray.put(shipment);
               

            }

            JSONObject finalPayload = new JSONObject();
            finalPayload.put("Shipment", shipmentArray);
            


            String apiUrl = "https://app.lineclearexpressonline.com/Accounts/CreateShipment";

            String responseString = "";

            try{
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type","application/json");
                conn.setRequestProperty("Accept","application/json");



                String user_id = userId;
               
                String dynamicAuthHeader = getAuthHeaderByOmsCode(con, omsCode); // omsCode is from form

              

                conn.setRequestProperty("Authorization", dynamicAuthHeader);
                LogUtil.info(getClassName(),"Create Shipment user_id:"+user_id);

                conn.setDoOutput(true);

                try(OutputStream os = conn.getOutputStream()){
                    byte[] input=finalPayload.toString().getBytes("utf-8");
                    os.write(input,0, input.length);

                }

                int statusCode = conn.getResponseCode();
                LogUtil.info(getClassName(), "HTTP Status Code from UAT: " + statusCode);
                InputStream is = (statusCode<400)?conn.getInputStream(): conn.getErrorStream();

                try(BufferedReader br = new BufferedReader(new InputStreamReader(is,"utf-8"))){
                    StringBuilder responseBuilder = new StringBuilder();
                    String responseLine;
                    while((responseLine=br.readLine())!=null){
                        responseBuilder.append(responseLine.trim());
                    }
                    responseString = responseBuilder.toString();
                    LogUtil.info(getClassName(),"Server Response:\n"+responseString);
                }
                try {
                    JSONObject jsonResponse = new JSONObject(responseString);

                    String status = jsonResponse.optBoolean("Status") ? "Success" : "Failed";
                    String message = jsonResponse.optString("Message");
                    String reason = jsonResponse.has("Reason") && !jsonResponse.isNull("Reason") ? jsonResponse.opt("Reason").toString() : "";
                    String responseData = jsonResponse.has("ResponseData") && !jsonResponse.isNull("ResponseData") ? jsonResponse.opt("ResponseData").toString() : "";

                   
                    LogUtil.info(getClassName(), "Parsed API Status: " + status);
                    LogUtil.info(getClassName(), "Parsed Message: " + message);
                    LogUtil.info(getClassName(), "Parsed Reason: " + reason);
                    LogUtil.info(getClassName(), "Parsed ResponseData: " + responseData);

                   
                    PreparedStatement pstmt = con.prepareStatement(
                            "UPDATE app_fd_shop_upload SET c_api_status = ?, c_api_message = ?, c_api_reason = ?, c_api_response_data = ? WHERE id = ?"
                    );
                    pstmt.setString(1, status);
                    pstmt.setString(2, message);
                    pstmt.setString(3, reason);
                    pstmt.setString(4, responseData);
                    pstmt.setString(5, recordId);
                    pstmt.executeUpdate();
                    pstmt.close();

                    
                    String userMessage;
                    if (reason != null && reason.contains("Invalid email/password for login with Business customer")) {
                        userMessage = " Shipment creation failed: Your OMS email or password is invalid. Please change your password at the OMS edit profile page.";
                        LogUtil.warn(getClassName(), userMessage);
                    } else if ("Shipment Creation Successful".equalsIgnoreCase(message)) {
                        userMessage = "Shipment creation successful.";
                        //LogUtil.info(getClassName(), userMessage);

                    } else {
                        userMessage = "Shipment creation failed. Please check your shipment data and try again.";
                        LogUtil.warn(getClassName(), userMessage);
                    }

                    PreparedStatement pstmt2 = con.prepareStatement(
                            "UPDATE app_fd_shop_upload SET c_error_message = ? WHERE id = ?"
                    );
                    pstmt2.setString(1, userMessage);
                    pstmt2.setString(2, recordId);
                    pstmt2.executeUpdate();
                    pstmt2.close();


                    if (!responseData.isEmpty()) {

                        JSONArray responseArray = new JSONArray(responseData);

                        if (responseArray.length() != freshReferences.size()) {

                            return "Error: Response size mismatch with FRESH references. No updates made.";
                        }

                        for (int i = 0; i < responseArray.length(); i++) {
                            JSONObject responseItem = responseArray.getJSONObject(i);



                            String waybillNo = "";
                            if (responseItem.has("WayBill")) {
                                JSONArray waybillArray = responseItem.getJSONArray("WayBill");

                                if (waybillArray.length() > 0) {
                                    waybillNo = waybillArray.getString(0);  // Just take the first WayBill

                                }
                            }

                            String referenceNo = (i < freshReferences.size()) ? freshReferences.get(i) : "";

                            // Make sure both values exist before updating
                            if (!referenceNo.isEmpty() && !waybillNo.isEmpty()) {


                                PreparedStatement updateStmt = con.prepareStatement(
                                        "UPDATE app_fd_shopUpload_records SET c_waybill = ? WHERE c_reference_number = ? AND c_parentId = ?"
                                );
                                updateStmt.setString(1, waybillNo);
                                updateStmt.setString(2, referenceNo);
                                updateStmt.setString(3, recordId); // parent ID from shop_upload
                                int rowsUpdated = updateStmt.executeUpdate();
                                //updateStmt.executeUpdate();
                                updateStmt.close();


                            } else {
                                LogUtil.warn(getClassName(), "Missing referenceNo or waybillNo for index " + i);
                            }
                        }
                    }
                    updateShipmentIdPrefix(con, recordId);

                } catch (Exception e) {
                    LogUtil.error(getClassName(), e, "Failed to parse API response");
                }
                return responseString;

            }catch (Exception e){
                LogUtil.error(getClassName(),e,"Error sending shipment payload to UAT server.");
                return "Error Posting Payload:"+e.getMessage();
            }


        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Exception in Create Shipment plugin");
            return "Error: " + e.getMessage();
        } finally {
            try {
                if (con != null) con.close();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    private String getUserIdFromShopUpload(Connection con, String recordId) throws SQLException {
        String userId = null;
        String sql = "SELECT c_user_id FROM app_fd_shop_upload WHERE id = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, recordId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getString("c_user_id");
                }
            }
        }
        return userId;
    }

    private JSONObject getSenderInfo(Connection con, String omsCode) throws Exception {
       //senderInfo = array for seperate payloads, individual
        JSONObject senderInfo = new JSONObject();
        //address is for shipmentaddressFrom
        JSONObject address = new JSONObject();

        String sql = "SELECT * FROM app_fd_shopUpload_omsUser WHERE c_oms_accountNo_hidden = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, omsCode);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    senderInfo.put("SenderName", rs.getString("c_sender_name"));
                    address.put("CompanyName", rs.getString("c_sender_company_name"));
                    address.put("UnitNumber", rs.getString("c_unit_number"));
                    address.put("Address", rs.getString("c_sender_address"));
                    address.put("Address2", rs.getString("c_sender_address_2"));
                    address.put("PostalCode", rs.getString("c_sender_postal_code"));
                    address.put("City", rs.getString("c_sender_city"));
                    address.put("State", rs.getString("c_sender_state"));
                    address.put("Country", ""); // Optional
                    address.put("Email", rs.getString("c_sender_email_address"));
                    address.put("PhoneNumber", rs.getString("c_sender_phone_number"));
                    address.put("ICNumber", ""); // Optional
                }
            }
        }

        senderInfo.put("Address", address);
        return senderInfo;
    }

    private void updateShipmentIdPrefix(Connection con, String recordId) throws SQLException{
        String getMaxSql = "SELECT MAX(CAST(SUBSTRING_INDEX(c_shipment_id,' ',-1)AS UNSIGNED)) AS max_num FROM app_fd_shop_upload WHERE c_shipment_id is NOT NULL";
        int nextNumber = 1;

        try (PreparedStatement ps = con.prepareStatement(getMaxSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                nextNumber = rs.getInt("max_num") + 1;
            }
        }

        String shipmentPrefix = String.format("Shipment %03d", nextNumber);

        String updateSql = "UPDATE app_fd_shop_upload SET c_shipment_Id=? WHERE id=?";
        try (PreparedStatement ps = con.prepareStatement(updateSql)) {
            ps.setString(1, shipmentPrefix);
            ps.setString(2, recordId);
            ps.executeUpdate();
        }

    }

    private List<String> getFreshShipmentReferences(Connection con, String parentId) throws SQLException {
        List<String> freshReferences = new ArrayList<>();

        String sql = "SELECT c_reference_number FROM app_fd_shopUpload_records " +
                "WHERE c_status = 'NEW' AND c_parentId = ? ORDER BY dateCreated ASC";
        PreparedStatement pstmt = con.prepareStatement(sql);
        pstmt.setString(1, parentId);
        ResultSet rs = pstmt.executeQuery();

        while (rs.next()) {
            String ref = rs.getString("c_reference_number");
            freshReferences.add(ref);
        }

        rs.close();
        pstmt.close();
        return freshReferences;
    }

    private String getAuthHeaderByOmsCode(Connection con, String omsCode) throws Exception {

        String sql = "SELECT c_email_hidden, c_password_hidden FROM app_fd_shopUpload_omsUser WHERE c_oms_accountNo_hidden = ?";

        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, omsCode);
            //pstmt.setString(2, omsCode);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("c_email_hidden");
                    String password = rs.getString("c_password_hidden");

                    String auth = email + ":" + password;
                    return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                } else {
                    throw new Exception("OMS Account not found in both customer tables for code: " + omsCode);
                }
            }
        }
    }


    private String getAuthHeaderFromDb(Connection con, String userId) throws Exception {
        String sql = "SELECT c_email, c_password FROM app_fd_shopUpload_omsUser WHERE c_user_id = ?";
        try (PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setString(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String email = rs.getString("c_email");
                    String password = rs.getString("c_password");

                    String auth = email + ":" + password;
                    return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                } else {
                    throw new Exception("User not found in OMS user table for user_id: " + userId);
                }
            }
        }
    }
    private JSONArray getShipmentRecords(Connection con,String parentId) throws Exception{
        JSONArray records = new JSONArray();

        //String sql = "SELECT * FROM app_fd_shopUpload_records WHERE c_parentId = ? AND c_reference_number IS NOT NULL AND c_reference_number!=''";

        String sql = "SELECT * FROM app_fd_shopUpload_records " +
                "WHERE c_parentId = ? " +
                "AND (c_reference_number IS NOT NULL AND c_reference_number != '') " +
                "AND c_status = 'NEW' " +
                "AND (c_excludeFromShipment IS NULL OR c_excludeFromShipment = 'false')";

        try(PreparedStatement pstmt = con.prepareStatement(sql)){
            pstmt.setString(1,parentId);

            try(ResultSet rs = pstmt.executeQuery()){
                while(rs.next()){
                    JSONObject record = new JSONObject();
                    record.put("ShipmentRef", rs.getString("c_Reference_Number"));
                    record.put("RecipientName", rs.getString("c_Delivery_Name"));
                    record.put("RecipientPhone", rs.getString("c_Delivery_Phone_Number"));
                    record.put("ParcelType", rs.getString("c_Shipment_Type"));
                    record.put("ShipmentType", rs.getString("c_Mode"));
                    record.put("ShipmentServiceType", rs.getString("c_types_of_services"));

                    JSONObject shipmentAddressTo = new JSONObject();
                    shipmentAddressTo.put("UnitNumber",rs.getString("c_Unit_Number"));
                    shipmentAddressTo.put("Address", rs.getString("c_delivery_address"));
                    shipmentAddressTo.put("Address2", rs.getString("c_delivery_address_2")); // optional
                    shipmentAddressTo.put("PostalCode", rs.getString("c_delivery_postal_code"));
                    shipmentAddressTo.put("PhoneNumber", rs.getString("c_delivery_phone_number"));
                    record.put("ShipmentAddressTo", shipmentAddressTo);

                    JSONArray wayBillArray = new JSONArray();
                    JSONObject wayBillObj = new JSONObject();

                    wayBillObj.put("WayBillNo", ""); // optional, empty
                    wayBillObj.put("ProductID", ""); // optional, empty
                    wayBillObj.put("Weight", rs.getString("c_weight"));
                    wayBillObj.put("VolumeWidth", rs.getString("c_width"));
                    wayBillObj.put("VolumeHeight", rs.getString("c_height"));
                    wayBillObj.put("VolumeLength", rs.getString("c_length"));

                    wayBillArray.put(wayBillObj);
                    record.put("WayBill", wayBillArray);

                    records.put(record);

                }
            }
        }

        return records;
    }

//    private JSONArray getShipmentRecords
}

