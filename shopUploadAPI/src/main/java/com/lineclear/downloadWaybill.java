package com.lineclear;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.taglibs.standard.tag.common.sql.DataSourceUtil;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FileUtil;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.json.JSONObject;

import javax.sql.DataSource;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class downloadWaybill extends DefaultApplicationPlugin {
    @Override
    public String getName() {return "Download Waybill plugin";}

    @Override
    public String getVersion() {return "6.9.9";}

    @Override
    public String getDescription() {return "To enable user to download Waybill";}

    @Override
    public Object execute(Map map) {
        try {
            String recordId = (String) map.get("recordId");

            if (recordId == null) {

                return null;
            }


            String sql = "SELECT c_waybill FROM app_fd_shopUpload_records WHERE c_parentId=?";
            String waybill = null;
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");

            int maxRetries = 10;
            int retryDelayMs = 1000; // 1 second
            int retryCount = 0;

            while (retryCount < maxRetries) {
                try (Connection con = ds.getConnection();
                     PreparedStatement stmt = con.prepareStatement(sql)) {

                    stmt.setString(1, recordId); // make sure this is the correct parent id

                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String candidate = rs.getString("c_waybill");
                            if (candidate != null && !candidate.trim().isEmpty()) {
                                waybill = candidate;
                                break; // ✅ Found a non-empty waybill
                            }
                        }
                    }

                    if (waybill != null) {
                        break; // Break retry loop if found
                    }

                } catch (SQLException e) {
                    throw new RuntimeException("Database error during polling", e);
                }


                retryCount++;
                try {
                    Thread.sleep(retryDelayMs);
                } catch (InterruptedException ignored) {}
            }


            
            if(waybill==null){

                return null;
            }


            String url = "https://uat-app.lineclearexpressonline.com/DownloadWaybill";
            JSONObject payload = new JSONObject();
            payload.put("WayBillType","Parent Waybills");
            payload.put("WayBills",waybill);
            payload.put("PrintOption","LC WB");

          
            String authString = "YOUR_API_KEY_HERE";
            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type","application/json");
            post.setHeader("Authorization",authString);
            post.setEntity(new StringEntity(payload.toString(), ContentType.APPLICATION_JSON));

            StringEntity entity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
            post.setEntity(entity);

            HttpResponse response = client.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();

            if(statusCode==200){
                byte[] pdfBytes = EntityUtils.toByteArray(response.getEntity());

                String filename = recordId + ".pdf";
                File pdfFile = new File(filename);

                try (FileOutputStream fos = new FileOutputStream(pdfFile)) {
                    fos.write(pdfBytes);
                    LogUtil.info(getClassName(), "Saved PDF to " + filename);
                }
                
                //File pdfFile = new File("/tmp/test_waybill.pdf");
                if (pdfFile.exists()) {
                    String tableName = "app_fd_shopUpload_records";
                    FileUtil.storeFile(pdfFile, tableName, recordId);
                    saveWaybillFilenameToDb(recordId, recordId+".pdf");

                    File retrievedFile = FileUtil.getFile(String.valueOf(pdfFile),tableName,recordId);
                    LogUtil.info(getClassName(),"retrieved PDF with size:"+retrievedFile.length() + " bytes");
                    LogUtil.info(getClassName(), "📂 Successfully retrieved file: " + retrievedFile.getAbsolutePath());
                }

            } else {
                String responseBody = EntityUtils.toString(response.getEntity());
                LogUtil.info(getClassName(), "Error Response Body: " + responseBody);
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Unexpected error occurred");
        }

        return null;
    }

    private void saveWaybillFilenameToDb(String recordId, String filename) {
        String updateSql = "UPDATE app_fd_shopUpload_records SET c_waybillPdf = ? WHERE id = ?";
        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            conn = ds.getConnection();
            pstmt = conn.prepareStatement(updateSql);
            pstmt.setString(1, filename);  // Example: "53ac492e-...pdf"
            pstmt.setString(2, recordId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            LogUtil.error(getClassName(), e, "❌ Error saving file name to DB");
        } finally {
            try { if (pstmt != null) pstmt.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.close(); } catch (Exception ignored) {}
        }
    }



    @Override
    public String getLabel() {return "Download Waybill AS PDF";}

    @Override
    public String getClassName() {return getClass().getName();}

    @Override
    public String getPropertyOptions() {return "";}
}


