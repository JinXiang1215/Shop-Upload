package com.lineclear;

import jdk.jpackage.internal.Log;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.plugin.base.DefaultApplicationPlugin;
import org.joget.plugin.base.PluginProperty;
import org.joget.workflow.model.WorkflowAssignment;
import org.joget.apps.app.service.AppService;
import org.joget.apps.form.model.FormData;
import org.joget.apps.form.service.FormUtil;
import org.joget.commons.util.LogUtil;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class customTemplateUpload extends DefaultApplicationPlugin {

    @Override
    public Object execute(Map properties) {
        try {
            String primaryKey = (String) properties.get("recordId");
            if (primaryKey == null || primaryKey.trim().isEmpty()) {
                WorkflowAssignment assignment = (WorkflowAssignment) properties.get("workflowAssignment");
                if (assignment != null) {
                    primaryKey = assignment.getProcessId(); // Or use a custom variable
                }
            }

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean("formDataDao");



            String formDefId = "template_upload_form";  // Make sure this matches your form ID
            String tableName = "template_upload";

            FormRow row = formDataDao.load(formDefId, tableName, primaryKey);
            if (row == null) {
                LogUtil.warn(getClassName(), "⚠️ No record found for primaryKey: " + primaryKey);
                return null;
            }

            String platformId = row.getProperty("platform");
            String platformName = getPlatformNameById(platformId);
            String customName = row.getProperty("name");

            String combined = (platformName != null ? platformName : "")
                    + (customName != null && !customName.trim().isEmpty() ? " - " + customName.trim() : "");

            row.setProperty("platformName", combined);



            FormRowSet updatedRowSet = new FormRowSet();
            updatedRowSet.add(row);
            updatedRowSet.setMultiRow(false); // Only updating one row

            formDataDao.saveOrUpdate(formDefId, tableName, updatedRowSet);

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "❌ Error in CustomTemplateUpload plugin");
        }

        return null;
    }

    private String getPlatformNameById(String platformId) {
        String name = "";
        try {
            String sql = "SELECT c_platform FROM app_fd_shopUpload_platform WHERE id = ?";
            Object[] args = { platformId };

            DataSource ds = (DataSource) AppUtil.getApplicationContext().getBean("setupDataSource");
            JdbcTemplate jdbcTemplate = new JdbcTemplate(ds);

            name = jdbcTemplate.queryForObject(sql, args, String.class);

        } catch (Exception e) {
            LogUtil.error(getClassName(), e, "❌ Error fetching platform name for ID: " + platformId);
        }
        return name != null ? name : "";
    }

    @Override
    public String getName() {
        return "Custom Template Upload - Platform Name Resolver";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public String getDescription() {
        return "Post-processing plugin to create platformName as Platform - CustomName";
    }

    @Override
    public String getLabel() {
        return getName();
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public String getPropertyOptions() {
        return "";
    }

    @Override
    public PluginProperty[] getPluginProperties() {
        return new PluginProperty[0]; // no config needed
    }
}

