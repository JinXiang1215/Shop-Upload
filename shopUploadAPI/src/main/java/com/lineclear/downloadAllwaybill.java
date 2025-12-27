package com.lineclear;

import org.joget.apps.app.service.AppUtil;
import org.json.JSONArray;
import org.json.JSONObject;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.apache.felix.framework.util.Util.getClassName;

public class downloadAllwaybill extends ExtDefaultPlugin implements PluginWebSupport {
    @Override
    public String getName() {
        return "All WayBill Download";
    }

    @Override
    public String getVersion() {
        return "1.1.1";
    }

    @Override
    public String getDescription() {
        return "To able user to print all waybills from the order";
    }

    @Override
    public void webService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        String waybillParam = httpServletRequest.getParameter("waybill");
        String parentId = httpServletRequest.getParameter("parentId");

        List<String> waybillList = new ArrayList<>();

        try {
            // ✅ Case 1: Fetch waybills from DB using parentId
            if ((waybillParam == null || waybillParam.trim().isEmpty()) && parentId != null && !parentId.trim().isEmpty()) {
                try (Connection con = AppUtil.getApplicationContext().getBean(DataSource.class).getConnection();
                     PreparedStatement stmt = con.prepareStatement(
                             "SELECT c_waybill FROM app_fd_shopUpload_records WHERE c_parentId = ? AND c_waybill IS NOT NULL AND c_status = 'NEW'"
                     )) {

                    stmt.setString(1, parentId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            waybillList.add(rs.getString("c_waybill"));
                        }
                    }

                    if (waybillList.isEmpty()) {
                        LogUtil.warn(getClass().getName(), "⚠ No waybills found for parentId: " + parentId);
                        httpServletResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "No waybills found for given parentId");
                        return;
                    }

//                    LogUtil.info(getClass().getName(), "✅ Retrieved waybills from DB for parentId " + parentId + ": " + String.join(",", waybillList));

                } catch (Exception dbEx) {
                    LogUtil.error(getClass().getName(), dbEx, "❌ Error while querying DB for parentId: " + parentId);
                    httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error fetching waybills from DB");
                    return;
                }

            }
            // ✅ Case 2: Waybill(s) provided directly via URL param
            else if (waybillParam != null && !waybillParam.trim().isEmpty()) {
                for (String wb : waybillParam.split(",")) {
                    if (!wb.trim().isEmpty()) {
                        waybillList.add(wb.trim());
                    }
                }
            } else {
                httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing waybill or parentId parameter");
                return;
            }

            // ✅ Build request payload
            String joinedWaybills = String.join(",", waybillList);
            JSONObject payload = new JSONObject();
            payload.put("WayBillType", "Parent Waybills");
            payload.put("WayBills", joinedWaybills);
            payload.put("PrintOption", "LC WB");

//            LogUtil.info(getClass().getName(), "📤 Sending payload to Line Clear: " + payload.toString());

            // ✅ Prepare API call
            //String url = "https://uat-app.lineclearexpressonline.com/DownloadWaybill";
            String url = "https://app.lineclearexpressonline.com/DownloadWaybill";
            String authString = generateAuthHeaderFromParentId(parentId);
            //String authString = "bGluZWNsZWFydGVzdDMyMUBnbWFpbC5jb218VGVzdEAxMjN8U2pPSm1XSTBjeDltSW4yZlQxTXFvaTVMUzVlZERPZEtrYTJONkx0ZEpxTm1ISXZuMHVvVHRpY1okVmliUUJKaA==";

            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", authString);
            post.setEntity(new StringEntity(payload.toString(), ContentType.APPLICATION_JSON));

            HttpResponse apiResponse = client.execute(post);
            int statusCode = apiResponse.getStatusLine().getStatusCode();

            if (statusCode == 200) {
                httpServletResponse.setContentType("application/pdf");
                httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"waybills.pdf\"");

                try (InputStream apiInputStream = apiResponse.getEntity().getContent();
                     OutputStream outStream = httpServletResponse.getOutputStream()) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = apiInputStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, bytesRead);
                    }

//                    LogUtil.info(getClass().getName(), "✅ Waybill PDF streamed successfully for: " + joinedWaybills);
                }

            } else {
                LogUtil.warn(getClass().getName(), "❌ Failed to fetch waybill. HTTP status: " + statusCode);
                httpServletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Failed to fetch waybill from external API");
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "❌ Unexpected exception occurred");
            httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Unexpected error occurred");
        }
    }

    private String generateAuthHeaderFromParentId(String parentId) {
        String email = "";
        String password = "";
        String bearerToken = "SjOJmWI0cx9mIn2fT1Mqoi5LS5edDOdKka2N6LtdJqNmHIvn0uoTticZ$VibQBJh"; // static as per your doc

        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            //LogUtil.info(getClass().getName(), "🔍 Starting auth header generation for parentId: " + parentId);

            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();

            // 👇 Step 1: Get user_id from parent record
            String userId = "";
            ps = con.prepareStatement("SELECT c_user_id FROM app_fd_shop_upload WHERE id = ?");
            ps.setString(1, parentId);
            rs = ps.executeQuery();
            if (rs.next()) {
                userId = rs.getString("c_user_id");
                //LogUtil.info(getClass().getName(), "✅ Retrieved userId: " + userId);
            }
            rs.close();
            ps.close();

            // 👇 Step 2: Get email and password for user_id
            ps = con.prepareStatement("SELECT c_email_hidden, c_password_hidden FROM app_fd_shopUpload_omsUser WHERE c_oms_accountNo_hidden = ?");
            ps.setString(1, userId);
            rs = ps.executeQuery();
            if (rs.next()) {
                email = rs.getString("c_email_hidden");
                password = rs.getString("c_password_hidden");
                //LogUtil.info(getClass().getName(), "📧 Email: " + email);
            }

            if (email.isEmpty() || password.isEmpty()) {
                LogUtil.warn(getClass().getName(), "❌ Email or password is missing for userId: " + userId);
                return "";
            }

        } catch (Exception e) {
            LogUtil.error(getClass().getName(), e, "❌ Failed to generate auth header");
            return "";
        } finally {
            try {
                if (rs != null) rs.close();
                if (ps != null) ps.close();
                if (con != null) con.close();
            } catch (SQLException ignored) {}
        }

        // 👇 Step 3: Combine and encode
        String raw = email + "|" + password + "|" + bearerToken;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        //LogUtil.info(getClass().getName(), "🔐 Generated Base64 Auth Header: " + encoded);

        return encoded;
    }


}
//    public void webService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
//        String recordId = httpServletRequest.getParameter("id");
//        String waybillParam = httpServletRequest.getParameter("waybill");
//        String parentId = httpServletRequest.getParameter("parentId");
//
//
//        LogUtil.info(getClass().getName(), "Received API call for ID: " + recordId + ", Waybills: " + waybillParam);
//
//        if (waybillParam == null || waybillParam.trim().isEmpty()) {
//            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing waybill parameter");
//            return;
//        }
//
//        try {
//            // ✅ Split multiple waybills into array
//            String[] waybillArray = waybillParam.split(",");
//            String joinedWaybills = String.join(",", waybillArray).trim();
//
//            // ✅ Build the payload
//            JSONObject payload = new JSONObject();
//            payload.put("WayBillType", "Parent Waybills");
//            payload.put("WayBills", joinedWaybills);
//            payload.put("PrintOption", "LC WB");
//
//            LogUtil.info(getClass().getName(), "Sending payload: " + payload.toString());
//
//            // ✅ Make API call
//            String url = "https://uat-app.lineclearexpressonline.com/DownloadWaybill";
//            String authString = "bGluZWNsZWFydGVzdDMyMUBnbWFpbC5jb218VGVzdEAxMjN8U2pPSm1XSTBjeDltSW4yZlQxTXFvaTVMUzVlZERPZEtrYTJONkx0ZEpxTm1ISXZuMHVvVHRpY1okVmliUUJKaA==";
//
//            HttpClient client = HttpClientBuilder.create().build();
//            HttpPost post = new HttpPost(url);
//            post.setHeader("Content-Type", "application/json");
//            post.setHeader("Authorization", authString);
//            post.setEntity(new StringEntity(payload.toString(), ContentType.APPLICATION_JSON));
//
//            HttpResponse apiResponse = client.execute(post);
//            int statusCode = apiResponse.getStatusLine().getStatusCode();
//
//            if (statusCode == 200) {
//                // ✅ Stream the combined PDF to browser
//                httpServletResponse.setContentType("application/pdf");
//                httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"bulk-waybills.pdf\"");
//
//                InputStream apiInputStream = apiResponse.getEntity().getContent();
//                OutputStream outStream = httpServletResponse.getOutputStream();
//
//                byte[] buffer = new byte[8192];
//                int bytesRead;
//                while ((bytesRead = apiInputStream.read(buffer)) != -1) {
//                    outStream.write(buffer, 0, bytesRead);
//                }
//                outStream.flush();
//                outStream.close();
//                apiInputStream.close();
//
//                LogUtil.info(getClass().getName(), "✅ Bulk waybill PDF streamed successfully for waybills: " + waybillParam);
//            } else {
//                LogUtil.warn(getClass().getName(), "❌ Failed to download waybills. HTTP Status: " + statusCode);
//                httpServletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Failed to fetch waybill from external API");
//            }
//
//        } catch (Exception e) {
//            LogUtil.error(getClass().getName(), e, "Exception while processing bulk waybill PDF");
//            httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error while fetching waybill");
//        }
//    }
//}
