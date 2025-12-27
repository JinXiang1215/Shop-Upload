package com.lineclear;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.service.FileUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginManager;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.workflow.model.service.WorkflowManager;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class validationFile extends DefaultApplicationPlugin {

    @Override
    public String getName() {
        return "Validation SKU File Plugin";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "A plugin to validate uploaded SKU Excel files.";
    }

    @Override
    public Object execute(Map map) {
        Connection con = null;
        PreparedStatement ps = null;

        String column1 = "SKUReferenceNo";
        String column2 = "ProductName";
        String column3 = "PackageID";
        String column4 = "PackageType";

        try {
            WorkflowAssignment wfAssignment = (WorkflowAssignment) map.get("workflowAssignment");
            //WorkflowManager wManager = (WorkflowManager) map.get("workflowManager");
            WorkflowManager wManager = (WorkflowManager) AppUtil.getApplicationContext().getBean("workflowManager");
            PluginManager pManager = (PluginManager) map.get("pluginManager");
            String recordId = (String) map.get("recordId");
            String fileName = null;

            if (wManager != null) {
                //wManager.activityVariables(wfAssignment.getActivityId(), variables);
                LogUtil.warn("ValidationFilePlugin", "WorkflowManager " +wManager);
            } else {
                LogUtil.warn("ValidationFilePlugin", "WorkflowManager is null. Cannot update activity variables.");
            }

            if (recordId == null && wfAssignment != null) {
                String processId = wfAssignment.getProcessId();
                AppService appService = (AppService) AppUtil.getApplicationContext().getBean("appService");
                recordId = appService.getOriginProcessId(processId);
            }

            if (recordId != null) {
                //LogUtil.info("ValidationFilePlugin", "Found Record ID: " + recordId);

                DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
                con = ds.getConnection();

                fileName = getFileName(con, recordId);
                if (fileName != null) {
                    //LogUtil.info("ValidationFilePlugin", "File name found: " + fileName);

                    File excelFile = FileUtil.getFile(fileName, "packageSku_upload", recordId);
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
                            List<String> requiredColumns = Arrays.asList(column1, column2, column3, column4);
                            if (excelColumns.equals(requiredColumns)) {
                                status = "Success";
                                //LogUtil.info("Status", "Valid");

                            } else if (excelColumns.containsAll(requiredColumns)) {
                                status = "Failed";
                                //LogUtil.info("Status", "Invalid - Correct columns but wrong order");
                            } else {
                                status = "Failed";
                                //LogUtil.info("Status", "Invalid");
                            }
                            updateStatus(con, recordId, status);
                            LogUtil.info("ValidationFilePlugin", "Updated c_status to: " + status);

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

    @Override
    public String getLabel() {
        return "Validation SKU File Plugin";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    private String getFileName(Connection con, String recordId) {
        String fileName = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            String query = "SELECT c_upload_sku FROM app_fd_packageSku_upload WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, recordId);
            rs = ps.executeQuery();

            if (rs.next()) {
                fileName = rs.getString("c_upload_sku");
            } else {
                LogUtil.warn("ValidationFilePlugin", "No file found for record ID: " + recordId);
            }
        } catch (SQLException e) {
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

    private void updateStatus(Connection con, String recordId, String status){
        PreparedStatement ps = null;
        try{
            String query = "UPDATE app_fd_packageSku_upload SET c_status = ? WHERE id = ?";
            ps = con.prepareStatement(query);
            ps.setString(1, status);
            ps.setString(2, recordId);

            int rowsUpdated = ps.executeUpdate();
            if (rowsUpdated > 0) {
                //LogUtil.info("ValidationFilePlugin", "Record updated successfully with status: " + status);
            } else {
                LogUtil.warn("ValidationFilePlugin", "No record found with ID: " + recordId);
            }
        } catch(SQLException e){
            LogUtil.error("ValidationFilePlugin", e, "Error updating the status in the database");
        } finally {
            try {
                if (ps != null) ps.close();
            } catch (SQLException e) {
                LogUtil.error("ValidationFilePlugin", e, "Error closing database resources");
            }
        }
    }
}
