package com.lineclear;

import org.joget.apps.app.service.AppUtil;
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
import java.util.Base64;

import static org.apache.felix.framework.util.Util.getClassName;


public class downloadWaybillPlugin extends ExtDefaultPlugin implements PluginWebSupport {

    @Override
    public String getName() {
        return "Waybill PDF Viewer";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getDescription() {
        return "Serves Waybill PDF as web view";
    }


    public String getClassName() {return getClass().getName();}

    @Override
    public void webService(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) throws ServletException, IOException {
        String recordId = httpServletRequest.getParameter("id");

        String waybill = httpServletRequest.getParameter("waybill");

        if (waybill == null || waybill.trim().isEmpty()) {
            httpServletResponse.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing waybill parameter");
            return;
        }

        try {

            String authHeader = generateAuthHeader(recordId);
            if (authHeader == null) {
                LogUtil.warn(getClassName(), "❌ Authorization header generation failed.");
                httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing or invalid credentials");
                return;
            }
            String url = "https://app.lineclearexpressonline.com/DownloadWaybill";
            JSONObject payload = new JSONObject();
            payload.put("WayBillType", "Parent Waybills");
            payload.put("WayBills", waybill);
            payload.put("PrintOption", "LC WB");

            HttpClient client = HttpClientBuilder.create().build();
            HttpPost post = new HttpPost(url);
            post.setHeader("Content-Type", "application/json");
            post.setHeader("Authorization", authHeader);
            post.setEntity(new StringEntity(payload.toString(), ContentType.APPLICATION_JSON));

            HttpResponse apiResponse = client.execute(post);
            int statusCode = apiResponse.getStatusLine().getStatusCode();

            if (statusCode == 200) {

                httpServletResponse.setContentType("application/pdf");
                httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"" + waybill + ".pdf\"");

                InputStream apiInputStream = apiResponse.getEntity().getContent();
                OutputStream outStream = httpServletResponse.getOutputStream();

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = apiInputStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, bytesRead);
                }
                outStream.flush();
                outStream.close();
                apiInputStream.close();

            } else {
                LogUtil.warn(getClassName(), "❌ Failed to download waybill. HTTP Status: " + statusCode);
                httpServletResponse.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Failed to fetch waybill from external API");
            }

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "Exception while processing waybill PDF");
            httpServletResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal error while fetching waybill");
        }
    }

    private String generateAuthHeader(String recordId) {
        String bearer = "YOUR_API_KEY_HERE"; // Your static bearer
        try (Connection con = AppUtil.getApplicationContext().getBean(DataSource.class).getConnection()) {

            // ✅ Get OMS Account No
            String omsAccountNo = null;
            String parentId = null;
            PreparedStatement ps1 = con.prepareStatement(
                    "SELECT c_parentId FROM app_fd_shopUpload_records WHERE id = ?"
            );
            ps1.setString(1, recordId);
            ResultSet rs1 = ps1.executeQuery();


            if (rs1.next()) {
                //omsAccountNo = rs1.getString("c_oms_accountNom");
                parentId = rs1.getString("c_parentId");
                //LogUtil.info(getClassName(), "🔍 OMS Account No for recordId " + recordId + ": " + parentId);
            }
            rs1.close();
            ps1.close();



            if (parentId == null || parentId.isEmpty()) {
                LogUtil.warn(getClassName(), "⚠️ parentId not found for recordId: " + recordId);
                return null;
            }
            String omsAccount = null;
            PreparedStatement ps3 = con.prepareStatement(
                    "SELECT c_oms_accountNom FROM app_fd_shop_upload WHERE id = ?"
            );
            ps3.setString(1, parentId);
            ResultSet rs3 = ps3.executeQuery();


            if (rs3.next()) {
                omsAccount = rs3.getString("c_oms_accountNom");
                //LogUtil.info(getClassName(), "🏷️ OMS Account No: " + omsAccount);
            }
            rs3.close();
            ps3.close();
            if (omsAccount == null || omsAccount.isEmpty()) {
                LogUtil.warn(getClassName(), "⚠ OMS account number not found for recordId: " + parentId);
                return null;
            }
            // ✅ Get Email and Password
            String email = null;
            String password = null;
            PreparedStatement ps2 = con.prepareStatement("SELECT c_email_hidden, c_password_hidden FROM app_fd_shopUpload_omsUser WHERE c_oms_accountNo_hidden = ?");
            ps2.setString(1, omsAccount);
            ResultSet rs2 = ps2.executeQuery();
            if (rs2.next()) {
                email = rs2.getString("c_email_hidden");
                password = rs2.getString("c_password_hidden");
                //LogUtil.info(getClassName(), "📧 Email: " + email);
            }
            rs2.close();
            ps2.close();

            if (email == null || password == null) {
                LogUtil.warn(getClassName(), "⚠ Email or password not found for OMS Account: " + omsAccountNo);
                return null;
            }

            // ✅ Encode to Base64
            String rawAuth = email + "|" + password + "|" + bearer;
            String encodedAuth = Base64.getEncoder().encodeToString(rawAuth.getBytes(StandardCharsets.UTF_8));
            //LogUtil.info(getClassName(), "🔐 Generated Base64 auth string.");
            return encodedAuth;

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "❌ Failed to generate Authorization header");
            return null;
        }
    }

    }





