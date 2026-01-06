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
                DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
                con = ds.getConnection();

                fileName = getFileName(con, recordId);
                if (fileName != null) {

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

