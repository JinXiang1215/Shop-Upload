package com.lineclear;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.base.ExtDefaultPlugin;
import org.joget.plugin.base.PluginWebSupport;
import org.json.JSONArray;
import org.json.JSONObject;

public class GetShipmentType extends ExtDefaultPlugin implements PluginWebSupport {
    public String getName() {
        return "Get Shipment Type";
    }

    public String getVersion() {
        return "7.0.0";
    }

    public String getDescription() {
        return "Retrieve shipment type and platform value from form data";
    }

    public void webService(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String formId = request.getParameter("id");
        PrintWriter writer = response.getWriter();
        Connection con = null;

        try {
            DataSource ds = (DataSource)AppUtil.getApplicationContext().getBean("setupDataSource");
            con = ds.getConnection();
            if (!con.isClosed()) {
                String getValue = "SELECT c_shipment_type, c_select_platform, c_oms_accountNom FROM `app_fd_shop_upload` WHERE id = ?";
                PreparedStatement getValueStmt = con.prepareStatement(getValue);
                getValueStmt.setObject(1, formId);
                ResultSet rsValue = getValueStmt.executeQuery();
                JSONArray jsonArray = new JSONArray();

                while(rsValue.next()) {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("c_shipment_type", rsValue.getString("c_shipment_type"));
                    jsonObject.put("c_select_platform", rsValue.getString("c_select_platform"));
                    jsonObject.put("c_oms_accountNom", rsValue.getString("c_oms_accountNom"));
                    jsonArray.put(jsonObject);
                }

                String jsonString = jsonArray.toString();
                //LogUtil.info("com.lineclear.GetShipmentType", "Shipment type and platform retrieved successfully: " + jsonString);
                writer.write(jsonString);
            }
        } catch (Exception var20) {
            LogUtil.error("Error getting shipment type for form " + formId, var20, var20.getMessage());
        } finally {
            if (con != null) {
                try {
                    con.close();
                } catch (SQLException var19) {
                    Logger.getLogger(GetShipmentType.class.getName()).log(Level.SEVERE, (String)null, var19);
                }
            }

        }

    }
}

