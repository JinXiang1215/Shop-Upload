package com.lineclear;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.service.FileUtil;
import org.joget.commons.util.LogUtil;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

public class insertRecords {

    private String recordId;
    private String tableName;
    private String fileName;


    // Constructor to initialize with file details
    public insertRecords(String recordId, String tableName, String fileName) {
        this.recordId = recordId;
        this.tableName = tableName;
        this.fileName = fileName;
    }

    // Method to process and read the Excel file
    public void readExcelFile() throws IOException {
        // Assuming FileUtil.getFile method retrieves the file from the specified path
        File excel = FileUtil.getFile(fileName, tableName, recordId);
        LogUtil.info("Stored processed file is:"+fileName+"table name"+tableName+"record id:",recordId);


        if (!excel.exists()) {
            LogUtil.info("insertRecords", "File not found: " + fileName);
            return;
        } else {
            LogUtil.info("File found: ", "Filename- " + fileName);
        }

        PreparedStatement ps = null;
        Connection con = null;

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

        // Open the file
//            try (FileInputStream file = new FileInputStream(excel)) {
//                // Create a workbook instance for the file
//                XSSFWorkbook workbook = new XSSFWorkbook(file);
//                // Get the first sheet
//                XSSFSheet sheet = workbook.getSheetAt(0);
//
//                // Iterate through rows
//                Iterator<Row> rowIterator = sheet.iterator();
//
//                if (rowIterator.hasNext()) {
//                    Row row = rowIterator.next(); // Read the first row
//
//                    // Iterate through all the columns in the row
//                    Iterator<Cell> cellIterator = row.cellIterator();
//
//                    while (cellIterator.hasNext()) {
//                        Cell cell = cellIterator.next();
//                        // Assuming the cell contains string data
//                        LogUtil.info("Records", "Cell Value: " + cell.getStringCellValue());
//                    }
//                }
//
//                // Close the workbook
//                workbook.close();
//            }

            try(FileInputStream file = new FileInputStream(excel)){
                XSSFWorkbook workbook = new XSSFWorkbook(file);
                XSSFSheet sheet = workbook.getSheetAt(0);

                Row headerRow = sheet.getRow(0);
                if(headerRow == null){
                    LogUtil.error("insertRecords", null, "Header row is missing");
                    return;
                }

                List<String> columnNames = new ArrayList<>();

                // Format the column names in excel to become like in the table. This method are use so that it know which field need to insert where
                for (Cell cell : headerRow){
                    if(cell != null && cell.getCellType() == CellType.STRING){
                        String columnName = cell.getStringCellValue().trim();
                        if (!columnName.isEmpty()) { // Only add non-empty column names
                            String dbColumnName = "c_" + columnName.replace(" ", "_"); // Replace spaces with underscores
                            columnNames.add(dbColumnName);  // Add column name
                        }
                    }
                }
                //LogUtil.info("insertRecords", "Number of columns: " + columnNames.size());

                String dbIdColumn = "id";
                columnNames.add(0, dbIdColumn);

                String dbDateColumn = "dateCreated";
                columnNames.add(1, dbDateColumn);

                String dbParentId = "c_parentId";
                columnNames.add(2,dbParentId);

                StringBuilder sql = new StringBuilder("INSERT INTO app_fd_shopUpload_records (");
                for (int i = 0; i < columnNames.size(); i++) {
                    sql.append(columnNames.get(i));
                    if (i < columnNames.size() - 1) {
                        sql.append(", ");
                    }
                }
                sql.append(") VALUES (");
                for (int i = 0; i < columnNames.size(); i++) {
                    sql.append("?");
                    if (i < columnNames.size() - 1) {
                        sql.append(", ");
                    }
                }
                sql.append(")");

                ps = con.prepareStatement(sql.toString());

                for(int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++){
                    Row dataRow = sheet.getRow(rowIndex);
                    if(dataRow == null){
                        continue;
                    }

                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setTimestamp(2, Timestamp.from(Instant.now()));
                    ps.setString(3,recordId);

                    for(int colIndex =0; colIndex < columnNames.size()-3; colIndex++){
                        Cell cell = dataRow.getCell(colIndex);
                        String columnName = columnNames.get(colIndex+3);

                        if (cell != null) {
                            switch (cell.getCellType()) {
                                case STRING:
                                    ps.setString(colIndex + 4, cell.getStringCellValue());
                                    break;
                                case NUMERIC:
                                    if ("c_Quantity".equals(columnName) || "c_Delivery_Postal_Code".equals(columnName)) {
                                        ps.setInt(colIndex + 4, (int) cell.getNumericCellValue());
                                    } else {
                                        ps.setDouble(colIndex + 4, cell.getNumericCellValue());
                                    }
                                    break;
                                case BOOLEAN:
                                    ps.setBoolean(colIndex + 4, cell.getBooleanCellValue());
                                    break;
                                default:
                                    ps.setNull(colIndex + 4, java.sql.Types.NULL);
                            }
                        } else {
                            ps.setNull(colIndex + 4, java.sql.Types.NULL);  // Handle null cells
                        }

                    }
                    ps.addBatch();
                }
                ps.executeBatch();

                workbook.close();

                //LogUtil.info("insertRecords", "Records inserted successfully.");
                //response.getWriter().write("Records inserted successfully.");

            }

         } catch (SQLException e) {
            LogUtil.error("insertRecords", e, "Database connection error");
        } finally {
            // Close resources
            if (ps != null) {
                try { ps.close(); } catch (SQLException e) { /* ignored */ }
            }
            if (con != null) {
                try { con.close(); } catch (SQLException e) { /* ignored */ }
            }
        }
    }
}

