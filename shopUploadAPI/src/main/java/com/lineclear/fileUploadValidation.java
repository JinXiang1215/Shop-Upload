package com.lineclear;

import jdk.jpackage.internal.Log;
import net.sf.json.JSON;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.sql.DataSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.Buffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class fileUploadValidation extends DefaultApplicationPlugin {

    @Override
    public String getName() {
        return "Validation Template File Plugin";
    }

    @Override
    public String getVersion() {
        return "7.0.0";
    }

    @Override
    public String getDescription() {
        return "A plugin to validate uploaded Template excel file.";
    }

    @Override
    public Object execute(Map properties) {
        Connection con = null;
        PreparedStatement ps = null;

        String column1 = "Excel Column";
        String column2 = "Customer Column";

        try {
            WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
            WorkflowManager wManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
            PluginManager pManager = (PluginManager) properties.get("pluginManager");
            String recordId = (String) properties.get("recordId");
            String fileName = null;

            if (recordId == null && wfAssignment != null) {
                String processId = wfAssignment.getProcessId();
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                recordId = appService.getOriginProcessId(processId);
            }

            if (recordId != null) {
//                LogUtil.info("ValidationFilePlugin", "Found Record ID: " + recordId);

                DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
                con = ds.getConnection();

                fileName = getFileName(con, recordId);
                if (fileName != null) {
//                    LogUtil.info("ValidationFilePlugin", "File name found: " + fileName);

                    File excelFile = FileUtil.getFile(fileName, "template_upload", recordId);
                    if (excelFile != null && excelFile.exists()) {
                        try (FileInputStream file = new FileInputStream(excelFile)) {
                            XSSFWorkbook workbook = new XSSFWorkbook(file);
                            XSSFSheet sheet = workbook.getSheetAt(0);
                            Iterator<Row> rowIterator = sheet.iterator();

                            if (!rowIterator.hasNext()) {
                                LogUtil.warn("ValidationFilePlugin", "Excel file is empty or does not contain data.");
                                return null;
                            }

                            Row row = rowIterator.next();
                            List<String> excelColumns = new ArrayList<>();
                            Iterator<Cell> cellIterator = row.cellIterator();
                            while (cellIterator.hasNext()) {
                                Cell cell = cellIterator.next();
                                excelColumns.add(cell.getStringCellValue());
                                //LogUtil.info("","Columns: " + cell.getStringCellValue());
                            }


                            String status;
                            List<String> requiredColumns = Arrays.asList(column1, column2);
                            if (excelColumns.equals(requiredColumns) && excelColumns.size() == requiredColumns.size()) {
                                status = "Success";
                                //LogUtil.info("Status", "Valid");

                            } else {
                                status = "Failed";
                                //LogUtil.info("Status", "Invalid");
                            }
                            updateStatus(con, recordId, status);
//                            LogUtil.info("ValidationFilePlugin", "Updated c_status to: " + status);

                            if (wManager != null) {
                                wManager.activityVariable(wfAssignment.getActivityId(), "status", status);
                            } else {
                                LogUtil.warn("ValidationFilePlugin", "WorkflowManager is null. Cannot update activity variables.");
                            }

                        } catch (Exception e) {
                            LogUtil.error(validationFile.class.getName(), e, "Error processing the Excel file.");
                        }
                    } else {
                        LogUtil.warn("ValidationFilePlugin", "File not found or does not exist: " + fileName);
                    }
                } else {
                    LogUtil.warn("ValidationFilePlugin", "File name not found for Record ID: " + recordId);
                }
            } else {
                LogUtil.warn("ValidationFilePlugin", "No Record ID found in workflow assignment or map.");
            }
        } catch (Exception e) {
            LogUtil.error(validationFile.class.getName(), e, "Error retrieving the file: " + e.getMessage());
        } finally {
            try {
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                LogUtil.error("ValidationFilePlugin", e, "Error closing database resources");
            }
        }

        return null;
    }
//    public Object execute(Map properties) {
//        Connection con = null;
//        PreparedStatement ps = null;
//
//        String column1 = "Excel Column";
//        String column2 = "Customer Column";
//
//        try {
//            WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
//            WorkflowManager wManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
//            PluginManager pManager = (PluginManager) properties.get("pluginManager");
//            String recordId = (String) properties.get("recordId");
//            String fileName = null;
//
//            if (recordId == null && wfAssignment != null) {
//                String processId = wfAssignment.getProcessId();
//                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
//                recordId = appService.getOriginProcessId(processId);
//            }
//
//            if (recordId != null) {
//                //LogUtil.info("ValidationFilePlugin", "Found Record ID: " + recordId);
//
//                // Step 1: Get platform value
//                LogUtil.info(getClassName(),"Record ID = "+recordId);
//                String platformValue = getPlatformValue(recordId);
//                if (platformValue == null) {
//                    LogUtil.warn("ValidationFilePlugin", "Platform value not found for Record ID: " + recordId);
//                    return null;  // Exit if platform not found
//                }
//
//                // Step 2: Get required columns from API
//                List<String> requiredColumns = fetchExpectedColumns(platformValue);
//                if (requiredColumns == null || requiredColumns.isEmpty()) {
//                    LogUtil.warn("ValidationFilePlugin", "No expected columns found for platform: " + platformValue);
//                    return null;  // Exit if no expected columns
//                }
//
//                DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
//                con = ds.getConnection();
//
//                String formDefId = "shop_upload";
//                String fileUploadFieldId = "upload_file";
//
//                LogUtil.info("ValidationFilePlugin", "Checking file with formDefId=" + formDefId + ", fileUploadFieldId=" + fileUploadFieldId + ", recordId=" + recordId);
//
//                FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");
//                FormRowSet rowSet = formDataDao.find(formDefId, "", "WHERE e.id = ?", new String[]{recordId}, null, null, null, null);
//
//                if (rowSet == null || rowSet.isEmpty()) {
//                    LogUtil.warn("ValidationFilePlugin", "Form data not found for record ID: " + recordId);
//                    return null;
//                }
//
//                FormRow formRow = rowSet.get(0);
//                String uploadFile = formRow.getProperty(fileUploadFieldId);
//
//                if (uploadFile == null || uploadFile.isEmpty()) {
//                    LogUtil.warn("ValidationFilePlugin", "Uploaded file field is empty for record ID: " + recordId);
//                    return null;
//                }
//
//                File excelFile = FileUtil.getFile(uploadFile,formDefId,recordId);
////                if(!excelFile.exists()){
////                    LogUtil.warn("ValidationFilePLugin","Uploaded file not found for record ID"+recordId);
////                    return null;
////                }
//
//                LogUtil.info("ValidationFilePlugin","Excel File successfully found:"+excelFile.getName());
//                LogUtil.info("ValidationFilePlugin","Absolute path:"+excelFile.getAbsolutePath());
//
//                try(FileInputStream file = new FileInputStream(excelFile)){
//                    XSSFWorkbook workbook = new XSSFWorkbook(file);
//                    XSSFSheet sheet = workbook.getSheetAt(0);
//                    Iterator<Row> rowIterator = sheet.iterator();
//
//                    if(!rowIterator.hasNext()){
//                        LogUtil.warn("ValidationFilePlugin","EXcel File Empty or does not contain data");
//                        return null;
//                    }
//                    Row row = rowIterator.next();
//                    List<String> excelColumns = new ArrayList<>();
//                    Iterator<Cell> cellIterator = row.cellIterator();
//
//                    LogUtil.info("ValidationFilePlugin","Reading Header row the extract excel column");
//
//                    while(cellIterator.hasNext()){
//                        Cell cell = cellIterator.next();
//                        excelColumns.add(cell.getStringCellValue());
//                        LogUtil.info("ValidationFilePlugin","Found column:"+cell.getStringCellValue());
//                    }
//                    // Step 6: Compare columns
//                    String status;
//                    if(excelColumns.containsAll(requiredColumns)){
//                        status="Success";
//                        LogUtil.info("ValidationFilePlugin","Excel column validation: Valid- all required columns found");
//
//                    }else{
//                        status="Failed";
//                        LogUtil.info("ValidationFilePlugin","Excel column validation: Invalid - Missing Required columns:");
//
//                        List<String> missing = new ArrayList<>(requiredColumns);
//                        missing.removeAll(excelColumns);
//                        LogUtil.warn("ValidationFilePlugin","Missing Required Columns:"+missing);
//
//                    }
//
//                    updateStatus(con,recordId,status);
//                    LogUtil.info("ValidationFilePlugin","Updated c_status to:"+status);
//
//                    if(wManager!=null){
//                        wManager.activityVariable(wfAssignment.getActivityId(),"status",status);
//                    }else{
//                        LogUtil.warn("ValidationFIlePLugin","WorkflowManager is null.cannot update activity");
//                    }
//                }
//
//                // Step 3: Get uploaded file name
//                fileName = getFileName(con, recordId);
//                if (fileName == null) {
//                    LogUtil.warn("ValidationFilePlugin", "File name not found for Record ID: " + recordId);
//                    return null;  // Exit if no file name
//                }
//
//                LogUtil.info("ValidationFilePlugin", "File name found: " + fileName);
//
////                // Step 4: Get actual uploaded Excel file
////                File excelFile = FileUtil.getFile(fileName, "template_upload", recordId);
////                if (excelFile == null || !excelFile.exists()) {
////                    LogUtil.warn("ValidationFilePlugin", "File not found or does not exist: " + fileName);
////                    return null;  // Exit if file doesn't exist
////                }
////
////                // Step 5: Read Excel file & extract columns
////                try (FileInputStream file = new FileInputStream(excelFile)) {
////                    XSSFWorkbook workbook = new XSSFWorkbook(file);
////                    XSSFSheet sheet = workbook.getSheetAt(0);
////                    Iterator<Row> rowIterator = sheet.iterator();
////
////                    if (!rowIterator.hasNext()) {
////                        LogUtil.warn("ValidationFilePlugin", "Excel file is empty or does not contain data.");
////                        return null;
////                    }
////
////                    Row row = rowIterator.next();
////                    List<String> excelColumns = new ArrayList<>();
////                    Iterator<Cell> cellIterator = row.cellIterator();
////
////                    LogUtil.info("ValidationFilePlugin", "Reading header row to extract Excel columns...");
////
////                    while (cellIterator.hasNext()) {
////                        Cell cell = cellIterator.next();
////                        excelColumns.add(cell.getStringCellValue());
////                        LogUtil.info("", "Columns: " + cell.getStringCellValue());
////                    }
////
////                    // Step 6: Compare columns
////                    String status;
////                    if (excelColumns.equals(requiredColumns) && excelColumns.size() == requiredColumns.size()) {
////                        status = "Success";
////                        LogUtil.info("Status", "Valid");
////                    } else {
////                        status = "Failed";
////                        LogUtil.info("Status", "Invalid");
////                    }
////
////                    updateStatus(con, recordId, status);
////                    LogUtil.info("ValidationFilePlugin", "Updated c_status to: " + status);
////
////                    if (wManager != null) {
////                        wManager.activityVariable(wfAssignment.getActivityId(), "status", status);
////                    } else {
////                        LogUtil.warn("ValidationFilePlugin", "WorkflowManager is null. Cannot update activity variables.");
////                    }
////
////                } catch (Exception e) {
////                    LogUtil.error(validationFile.class.getName(), e, "Error processing the Excel file.");
////                }
//
//            } else {
//                LogUtil.warn("ValidationFilePlugin", "No Record ID found in workflow assignment or map.");
//            }
//        } catch (Exception e) {
//            LogUtil.error(validationFile.class.getName(), e, "Error retrieving the file: " + e.getMessage());
//        } finally {
//            try {
//                if (ps != null) ps.close();
//                if (con != null) con.close();
//            } catch (SQLException e) {
//                LogUtil.error("ValidationFilePlugin", e, "Error closing database resources");
//            }
//        }
//
//        return null;
//    }

//    public Object execute(Map properties) {
//        Connection con = null;
//        PreparedStatement ps = null;
//
//        String column1 = "Excel Column";
//        String column2 = "Customer Column";
//
//        try {
//            WorkflowAssignment wfAssignment = (WorkflowAssignment) properties.get("workflowAssignment");
//            WorkflowManager wManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
//            PluginManager pManager = (PluginManager) properties.get("pluginManager");
//            String recordId = (String) properties.get("recordId");
//            String fileName = null;
//
//            if (recordId == null && wfAssignment != null) {
//                String processId = wfAssignment.getProcessId();
//                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
//                recordId = appService.getOriginProcessId(processId);
//            }
//
//            if (recordId != null) {
//                LogUtil.info("ValidationFilePlugin", "Found Record ID: " + recordId);
//
//                String platformValue = getPlatformValue(recordId);
//                if (platformValue == null) {
//                    LogUtil.warn("ValidationFilePlugin", "Platform value not found for Record ID: " + recordId);
//                    return null;  // Stop processing if platform value is missing
//                }
//
//                DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
//                con = ds.getConnection();
//
//                fileName = getFileName(con, recordId);
//                if (fileName != null) {
//                    LogUtil.info("ValidationFilePlugin", "File name found: " + fileName);
//
//                    File excelFile = FileUtil.getFile(fileName, "template_upload", recordId);
//                    if (excelFile != null && excelFile.exists()) {
//                        try (FileInputStream file = new FileInputStream(excelFile)) {
//                            XSSFWorkbook workbook = new XSSFWorkbook(file);
//                            XSSFSheet sheet = workbook.getSheetAt(0);
//                            Iterator<Row> rowIterator = sheet.iterator();
//
//                            if (!rowIterator.hasNext()) {
//                                LogUtil.warn("ValidationFilePlugin", "Excel file is empty or does not contain data.");
//                                return null;
//                            }
//
//                            Row row = rowIterator.next();
//                            List<String> excelColumns = new ArrayList<>();
//                            Iterator<Cell> cellIterator = row.cellIterator();
//                            while (cellIterator.hasNext()) {
//                                Cell cell = cellIterator.next();
//                                excelColumns.add(cell.getStringCellValue());
//                                LogUtil.info("","Columns: " + cell.getStringCellValue());
//                            }
//
//
//                            String status;
//                            //List<String> requiredColumns = Arrays.asList(column1, column2);
//                            List<String> requiredColumns = fetchExpectedColumns(platformValue);
//                            if (requiredColumns.isEmpty()) {
//                                LogUtil.warn("ValidationFilePlugin", "No expected columns found for platform: " + platformValue);
//                                return null;
//                            }
//                            if (excelColumns.equals(requiredColumns) && excelColumns.size() == requiredColumns.size()) {
//                                status = "Success";
//                                LogUtil.info("Status", "Valid");
//
//                            } else {
//                                status = "Failed";
//                                LogUtil.info("Status", "Invalid");
//                            }
//                            updateStatus(con, recordId, status);
//                            LogUtil.info("ValidationFilePlugin", "Updated c_status to: " + status);
//
//                            if (wManager != null) {
//                                wManager.activityVariable(wfAssignment.getActivityId(), "status", status);
//                            } else {
//                                LogUtil.warn("ValidationFilePlugin", "WorkflowManager is null. Cannot update activity variables.");
//                            }
//
//                        } catch (Exception e) {
//                            LogUtil.error(validationFile.class.getName(), e, "Error processing the Excel file.");
//                        }
//                    } else {
//                        LogUtil.warn("ValidationFilePlugin", "File not found or does not exist: " + fileName);
//                    }
//                } else {
//                    LogUtil.warn("ValidationFilePlugin", "File name not found for Record ID: " + recordId);
//                }
//            } else {
//                LogUtil.warn("ValidationFilePlugin", "No Record ID found in workflow assignment or map.");
//            }
//        } catch (Exception e) {
//            LogUtil.error(validationFile.class.getName(), e, "Error retrieving the file: " + e.getMessage());
//        } finally {
//            try {
//                if (ps != null) ps.close();
//                if (con != null) con.close();
//            } catch (SQLException e) {
//                LogUtil.error("ValidationFilePlugin", e, "Error closing database resources");
//            }
//        }
//
//       return null;
//    }

    private String getFileName(Connection con, String recordId){
        String fileName = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try{
            String query = "SELECT c_upload_template FROM app_fd_template_upload WHERE id =?";
            ps = con.prepareStatement(query);
            ps.setString(1,recordId);
            rs = ps.executeQuery();

            if(rs.next()){
                fileName = rs.getString("c_upload_template");
            } else{
                LogUtil.warn("ValidationFilePlugin", "No file found for record ID: " + recordId);
            }
        } catch (SQLException e){
            LogUtil.error("ValidationFilePlugin", e, "Error retrieving file name from the database");
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
            } catch (SQLException e) {
                LogUtil.error("ValidationFilePlugin", e, "Error closing database resources");
            }
        }

        return fileName;
    }

    private void updateStatus(Connection con, String recordId, String status) {
        PreparedStatement ps = null;
        try {
            String query = "UPDATE app_fd_shop_upload SET c_testStatus = ? WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, status);
            ps.setString(2, recordId);

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                LogUtil.info("ValidationFilePlugin", "✅ Status updated to '" + status + "' for record ID: " + recordId);
            } else {
                LogUtil.warn("ValidationFilePlugin", "⚠ No record found with ID: " + recordId + " in app_fd_shop_upload.");
            }
        } catch (SQLException e) {
            LogUtil.error("ValidationFilePlugin", e, "❌ Error updating status in app_fd_shop_upload.");
        } finally {
            try {
                if (ps != null) ps.close();
            } catch (SQLException e) {
                LogUtil.error("ValidationFilePlugin", e, "❌ Error closing prepared statement.");
            }
        }
    }


//    private void updateStatus(Connection con, String recordId, String status){
//        PreparedStatement ps = null;
//        try{
//            String query = "UPDATE app_fd_template_upload SET c_status = ? WHERE id = ?";
//            ps = con.prepareStatement(query);
//            ps.setString(1, status);
//            ps.setString(2, recordId);
//
//            int rowsUpdated = ps.executeUpdate();
//            if (rowsUpdated > 0) {
//                LogUtil.info("ValidationFilePlugin", "Record updated successfully with status: " + status);
//            } else {
//                LogUtil.warn("ValidationFilePlugin", "No record found with ID: " + recordId);
//            }
//        } catch(SQLException e){
//            LogUtil.error("ValidationFilePlugin", e, "Error updating the status in the database");
//        } finally {
//            try {
//                if (ps != null) ps.close();
//            } catch (SQLException e) {
//                LogUtil.error("ValidationFilePlugin", e, "Error closing database resources");
//            }
//        }
//    }
//recover1
//    private String getPlatformValue(String recordId){
//        String apiUrl = "http://justtapao.com:8080/jw/web/json/plugin/com.lineclear.GetShipmentType/service?id=" + recordId;
//        String platformValue = null;
//
//        try{
//            URL url = new URL(apiUrl);
//            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//            conn.setRequestMethod("GET");
//
//            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//            StringBuilder response = new StringBuilder();
//            String inputLine;
//
//            while((inputLine=in.readLine())!=null){
//                response.append(inputLine);
//            }
//            in.close();
//
//            LogUtil.info("ValidationFilePlugin","Raw API response:"+response.toString());
//
//            JSONArray jsonArray = new JSONArray(response.toString());
//            if(jsonArray.length()>0){
//                JSONObject obj = jsonArray.getJSONObject(0);
//                platformValue = obj.optString("c_select_platform",null);
//                LogUtil.info("ValidationFilePlugin","Extracted platform value:"+platformValue);
//            }else{
//                LogUtil.warn("ValidationFilePlugin","Empty response array from API for recordId:"+recordId);
//            }
//
////            JSONObject jsonResponse = new JSONObject(response.toString());
////            if(jsonResponse.has("c_select_platform")){
////                platformValue = jsonResponse.getString("c_select_platform");
////            }
//
//        }catch (IOException|JSONException e){
//            LogUtil.error("ValidationFilePlugin",e,"Error Fetching platform value from API.");
//        }
//        return platformValue;
//    }

    //recover1

//    private List<String> fetchExpectedColumns(String platformValue) throws IOException {
//        String apiUrl = "http://justtapao.com:8080/jw/web/json/plugin/com.lineclear.TemplateAPI/service?refId=" + platformValue;
//        LogUtil.info("ValidationFilePlugin", "full column api:" + apiUrl);
//
//        URL url = new URL(apiUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//
//        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//        String inputLine;
//        StringBuilder response = new StringBuilder();
//
//        while ((inputLine = in.readLine()) != null) {
//            response.append(inputLine);
//        }
//        in.close();
//
//        LogUtil.info("ValidationFilePlugin", "API Response for expected columns: " + response.toString());
//
//        List<String> expectedColumns = new ArrayList<>();
//
//        try {
//            JSONObject jsonResponse = new JSONObject(response.toString());
//
//            Iterator<String> keys = jsonResponse.keys();
//            while (keys.hasNext()) {
//                String key = keys.next();
//                Object value = jsonResponse.get(key);
//
//                if (value instanceof JSONArray) {
//                    JSONArray array = (JSONArray) value;
//                    for (int i = 0; i < array.length(); i++) {
//                        expectedColumns.add(array.getString(i));
//                        LogUtil.info("ValidationFilePlugin", "Expected column (array): " + array.getString(i));
//                    }
//                } else if (value instanceof String) {
//                    expectedColumns.add((String) value);
//                    LogUtil.info("ValidationFilePlugin", "Expected column (string): " + value);
//                }
//            }
//        } catch (JSONException e) {
//            LogUtil.error("ValidationFIlePlugin", e, "Invalid JSON Structure from API.");
//        }
//
//        return expectedColumns;
//    }
//recover1

//    private List<String> fetchExpectedColumns(String platformValue) throws IOException{
//        String apiUrl = "http://justtapao.com:8080/jw/web/json/plugin/com.lineclear.TemplateAPI/service?refId=" + platformValue;
//        LogUtil.info("ValidationFilePlugin","full column api:"+apiUrl);
//
//        URL url = new URL(apiUrl);
//        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
//        conn.setRequestMethod("GET");
//
//        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
//        String inputLine;
//        StringBuilder response = new StringBuilder();
//
//        while((inputLine=in.readLine())!=null){
//            response.append(inputLine);
//
//        }
//        in.close();
//        LogUtil.info("ValidationFilePlugin", "API Response for expected columns: " + response.toString());
//
//        List<String> expectedColumns = new ArrayList<>();
//        try{
//            JSONObject jsonResponse = new JSONObject(response.toString());
//
//            if (!jsonResponse.has("columns")) {
//                LogUtil.warn("ValidationFilePlugin", "JSON response does not contain 'columns' key.");
//            }
//
//            JSONArray columns = jsonResponse.getJSONArray("columns");
//            for(int i=0;i< columns.length();i++){
//                expectedColumns.add(columns.getString(i));
//                LogUtil.info("ValidationFilePlugin", "Expected column: " + columns.getString(i));
//            }
//        }catch(JSONException e){
//            LogUtil.error("ValidationFIlePlugin",e,"Invalid JSON Structure from API.");
//        }
//        return expectedColumns;
//    }

    @Override
    public String getLabel() {
        return "Validation Template File Plugin";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

}
