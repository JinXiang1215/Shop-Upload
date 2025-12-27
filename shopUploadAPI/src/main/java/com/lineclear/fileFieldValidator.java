package com.lineclear;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.joget.apps.form.model.Element;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormValidator;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class fileFieldValidator extends FormValidator {
    @Override
    public String getName() {return "File Field Validator";}

    @Override
    public String getVersion() {return "5.0.0";}

    @Override
    public String getDescription() {return "To validate the file uploaded by the user";}

    @Override
    public String getLabel() {return "File Field Validator";}

    @Override
    public String getClassName() {return getClass().getName();}

    @Override
    public String getPropertyOptions() {return "";}

    @Override
    public boolean validate(Element element, FormData formData, String[] strings) {
        try {
            // Get uploaded file path from form field
            String filePath = FormUtil.getElementPropertyValue(element, formData);

            if (filePath == null || filePath.isEmpty()) {
                LogUtil.warn("TemplateValidation", "No uploaded file path found in form field.");
                formData.addFormError(FormUtil.getElementParameterName(element), "No uploaded file found.");
                return false;
            }

            //LogUtil.info("TemplateValidation", "Uploaded file UUID path: " + filePath);


            //File excelFile = new File(fileStoragePath);
            File excelFile = org.joget.commons.util.FileManager.getFileByPath(filePath);

            if (excelFile == null) {
                LogUtil.warn("TemplateValidation", "Uploaded file could not be retrieved via FileManager: " + filePath);
                formData.addFormError(FormUtil.getElementParameterName(element), "Uploaded file could not be retrieved.");
                return false;
            }

            //LogUtil.info("TemplateValidation", "Actual File Absolute Path: " + excelFile.getAbsolutePath());

            if (!excelFile.exists()) {
                LogUtil.warn("TemplateValidation", "Uploaded file not found at path: " +  excelFile.getAbsolutePath());
                formData.addFormError(FormUtil.getElementParameterName(element), "Uploaded file not found.");
                return false;
            }

            //LogUtil.info("TemplateValidation", "Excel File successfully found: " + excelFile.getName());

            // ✅ Get the root form
            Form form = FormUtil.findRootForm(element);

            // ✅ Get Select Platform Element
            Element selectPlatformElement = FormUtil.findElement("select_platform", form, formData);
            String platformValue = "";
            if (selectPlatformElement != null) {
                platformValue = FormUtil.getElementPropertyValue(selectPlatformElement, formData);
            }
            if (platformValue == null || platformValue.isEmpty()) {
                formData.addFormError(FormUtil.getElementParameterName(element), "Platform value is required.");
                return false;
            }

            // ✅ Get Shipment Type Element
            Element shipmentTypeElement = FormUtil.findElement("shipment_type", form, formData);
            String shipmentType = "";
            if (shipmentTypeElement != null) {
                shipmentType = FormUtil.getElementPropertyValue(shipmentTypeElement, formData);
            }

            // ✅ Get OMS Account Element
            Element omsAccountElement = FormUtil.findElement("oms_accountNom", form, formData);
            String omsAccount = "";
            if (omsAccountElement != null) {
                omsAccount = FormUtil.getElementPropertyValue(omsAccountElement, formData);
            }

            //LogUtil.info("TemplateValidation", "Platform Value: " + platformValue);
            //LogUtil.info("TemplateValidation", "Shipment Type: " + shipmentType) if(expectedColumns==null||expectedColumns.isEmpty());
            //LogUtil.info("TemplateValidation", "OMS Account: " + omsAccount);

            List<String> expectedColumns = fetchExpectedColumns(platformValue);
            LogUtil.info("TemplateValidation", "PlatformValue: " + platformValue +
                    ", ExpectedColumns: " + (expectedColumns != null ? expectedColumns.toString() : "null"));
            if(expectedColumns.isEmpty()){
                formData.addFormError(FormUtil.getElementParameterName(element),"No expected columns found for platform: " + platformValue);
                return false;
            }

            try(FileInputStream fileInputStream = new FileInputStream(excelFile);
            XSSFWorkbook workbook = new XSSFWorkbook(fileInputStream)){

                XSSFSheet sheet = workbook.getSheetAt(0);

                Iterator<Row> rowIterator = sheet.iterator();
                if(!rowIterator.hasNext()){
                    formData.addFormError(FormUtil.getElementParameterName(element),"Excel file is empty or does not contain any headers.");
                    return false;
                }

                Row headerRow = rowIterator.next();
                List<String> excelColumns = new ArrayList<>();
                Iterator<Cell> cellIterator = headerRow.cellIterator();

                while(cellIterator.hasNext()){
                    Cell cell = cellIterator.next();
                    String columnHeader = cell.getStringCellValue().trim();
                    if(columnHeader!=null&&!columnHeader.isEmpty()){
                        excelColumns.add(columnHeader);
                    }
                }

                //LogUtil.info("TemplateValidation", "Excel Columns Found: " + String.join(", ", excelColumns));

                List<String> missingColumns = new ArrayList<>(expectedColumns);
                missingColumns.removeAll(excelColumns);

                if(!missingColumns.isEmpty()){
                    formData.addFormError(FormUtil.getElementParameterName(element),"Missing required columns: " + String.join(", ", missingColumns));
                    return false;
                }
            }
            return true;

        } catch (Exception e){
            LogUtil.error("TemplateValidation",e,"Error during file path validation");
            formData.addFormError(FormUtil.getElementParameterName(element), "Validation error: " + e.getMessage());
            return false;
        }

    }

    private String getPlatformValue(String recordId){
        String apiUrl = "http://justtapao.com:8080/jw/web/json/plugin/com.lineclear.GetShipmentType/service?id=" + recordId;
        String platformValue = null;

        try{
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String inputLine;

            while((inputLine=in.readLine())!=null){
                response.append(inputLine);
            }
            in.close();

//            LogUtil.info("ValidationFilePlugin","Raw API response:"+response.toString());

            JSONArray jsonArray = new JSONArray(response.toString());
            if(jsonArray.length()>0){
                JSONObject obj = jsonArray.getJSONObject(0);
                platformValue = obj.optString("c_select_platform",null);
                LogUtil.info("ValidationFilePlugin","Extracted platform value:"+platformValue);
            }else{
                LogUtil.warn("ValidationFilePlugin","Empty response array from API for recordId:"+recordId);
            }

//            JSONObject jsonResponse = new JSONObject(response.toString());
//            if(jsonResponse.has("c_select_platform")){
//                platformValue = jsonResponse.getString("c_select_platform");
//            }

        }catch (IOException | JSONException e){
            LogUtil.error("ValidationFilePlugin",e,"Error Fetching platform value from API.");
        }
        return platformValue;
    }

    private List<String> fetchExpectedColumns(String platformValue) throws IOException {
        String apiUrl = "https://btc.lineclearexpress.com/jw/web/json/plugin/com.lineclear.TemplateAPI/service?refId=" + platformValue;
        //LogUtil.info("ValidationFilePlugin", "full column api:" + apiUrl);

        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        //LogUtil.info("ValidationFilePlugin", "API Response for expected columns: " + response.toString());

        List<String> expectedColumns = new ArrayList<>();

        try {
            JSONObject jsonResponse = new JSONObject(response.toString());

            Iterator<String> keys = jsonResponse.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = jsonResponse.get(key);

                if (value instanceof JSONArray) {
                    JSONArray array = (JSONArray) value;
                    for (int i = 0; i < array.length(); i++) {
                        expectedColumns.add(array.getString(i));
                        //LogUtil.info("ValidationFilePlugin", "Expected column (array): " + array.getString(i));
                    }
                } else if (value instanceof String) {
                    expectedColumns.add((String) value);
                    //LogUtil.info("ValidationFilePlugin", "Expected column (string): " + value);
                }
            }
        } catch (JSONException e) {
            LogUtil.error("ValidationFIlePlugin", e, "Invalid JSON Structure from API.");
        }

        return expectedColumns;
    }
}
