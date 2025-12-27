package com.lineclear;

import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.DefaultApplicationPlugin;

import javax.sql.DataSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class editCheckRef extends DefaultApplicationPlugin {
    @Override
    public String getName() {
        return "Edit Reference Number Validation";
    }

    @Override
    public String getVersion() {
        return "6.6.6";
    }

    @Override
    public String getDescription() {
        return "To Enable to user to check validate the Reference Number";
    }

    @Override
    public Object execute(Map properties) {
        String parentId = (String) properties.get("recordId");

        if (parentId == null || parentId.isEmpty()) {
            LogUtil.warn(getClassName(), "❌ Parent ID is missing.");
            return null;
        }

//        LogUtil.info(getClassName(), "✅ Starting execution for parentId: " + parentId);

        Map<String, String> refStatusMap = new HashMap<>();
        List<String> existedRefList = new ArrayList<>();
        List<String> freshRefList = new ArrayList<>();

        // 1. Retrieve all reference numbers from DB for this parent ID
        List<String> refNumbers = getReferenceNumbersFromDb(parentId);

        if (refNumbers.isEmpty()) {
            LogUtil.warn(getClassName(), "⚠️ No Reference_Number values found in DB for parentId: " + parentId);
            return null;
        }

//        LogUtil.info(getClassName(), "📦 Found " + refNumbers.size() + " reference numbers for parentId: " + parentId);

        for (String ref : refNumbers) {
//            LogUtil.info(getClassName(), "🔍 Checking status of reference: " + ref);


            String result = checkReferenceExists(ref); // Call your API


            if (result.contains("\"StatusCode\"") && result.contains("\"WayBillNumber\"")) {
//                LogUtil.info(getClassName(), "✅ Reference EXISTS in system: " + ref);
                refStatusMap.put(ref.toLowerCase(), "EXISTS");
                existedRefList.add(ref);
            } else if (result.toLowerCase().contains("does not exist")) {
//                LogUtil.info(getClassName(), "✅ Reference is NEW: " + ref);
                refStatusMap.put(ref.toLowerCase(), "NEW");
                freshRefList.add(ref);
            } else {
                refStatusMap.put(ref.toLowerCase(), "UNKNOWN");
            }
        }

        updateChildStatuses(parentId, refStatusMap);

//        LogUtil.info(getClassName(), "✅ Execution completed for parentId: " + parentId);
//        LogUtil.info(getClassName(), "🟢 Total EXISTS: " + existedRefList.size() + ", Total NEW: " + freshRefList.size());

        return null;
    }

    @Override
    public String getLabel() {
        return "Check reference number for Edit Section ";
    }

    @Override
    public String getClassName() {
        return this.getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    private void updateChildStatuses(String parentId, Map<String, String> refStatusMap) {
        Connection con = null;
        PreparedStatement stmt = null;

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            String sql = "UPDATE app_fd_shopUpload_records SET c_status = ?, c_excludeFromShipment = ? " +
                    "WHERE c_parentId = ? AND LOWER(c_Reference_Number) = ?";
            stmt = con.prepareStatement(sql);

            for (Map.Entry<String, String> entry : refStatusMap.entrySet()) {
                String ref = entry.getKey();
                String status = entry.getValue();
                boolean exclude = "EXISTS".equals(status);

                stmt.setString(1, status);
                stmt.setString(2, "EXISTS".equalsIgnoreCase(status) ? "true" : "false");
                stmt.setString(3, parentId);
                stmt.setString(4, ref);

                int rows = stmt.executeUpdate();
                if (rows == 0) {
                    LogUtil.warn(getClassName(), "⚠️ No match found for reference: " + ref);
                }
            }

        } catch (SQLException e) {
            LogUtil.error(getClassName(), e, "❌ Error updating child statuses");
        } finally {
            try {
                if (stmt != null) stmt.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                LogUtil.error(getClassName(), e, "❌ Failed to close DB resources");
            }
        }
    }


    private List<String> getReferenceNumbersFromDb(String parentId) {
        List<String> refList = new ArrayList<>();
        Connection con = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();
            String query = "SELECT c_Reference_Number FROM app_fd_shopUpload_records WHERE c_parentId = ?";
            stmt = con.prepareStatement(query);
            stmt.setString(1, parentId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                String ref = rs.getString("c_Reference_Number");
                if (ref != null && !ref.trim().isEmpty()) {
                    refList.add(ref.trim());
                }
            }
        } catch (SQLException e) {
            LogUtil.error(getClassName(), e, "❌ Error fetching reference numbers");
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (con != null) con.close();
            } catch (SQLException e) {
                LogUtil.error(getClassName(), e, "❌ Failed to close DB resources");
            }
        }

        return refList;
    }
    private String checkReferenceExists(String referenceNumber) {
        //String apiUrl = "https://8ym3webome.execute-api.ap-south-1.amazonaws.com/production/1.0/viewandtrack";
        String apiUrl = "https://lineclearexpressonline.com/ce/1.0/viewandtrack";
        String bearerToken = "eyJhbGciOiJIUzI1NiJ9.QkVTVF9MQ1VOSV9FU1NQTA.1FcVvOUwquYYuoyA5yBrPcOLNUDf8iJaAZCqNZgjVys";
        HttpURLConnection conn = null;

        try {
            URL url = new URL(apiUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken);
            conn.setDoOutput(true);

            String jsonInput = String.format("{\"SearchType\": \"ShipmentRef\", \"ShipmentRef\": [\"%s\"]}", referenceNumber);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes("utf-8");
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            //LogUtil.info(getClassName(),"API response code for "+referenceNumber+ ": " + responseCode);

            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            String response = new BufferedReader(new InputStreamReader(is))
                    .lines()
                    .collect(Collectors.joining("\n"));

            //LogUtil.info(getClassName(),"API response code :"+responseCode);

            //LogUtil.info(getClassName(), "🌐 API response for ref " + referenceNumber + " : " + response);
            return response;

        } catch (IOException e) {
            LogUtil.error(getClassName(), e, "Exception during API call for reference:" + referenceNumber);
            return "Exception OCcured";
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
