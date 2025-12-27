package com.lineclear;

import java.util.ArrayList;
import java.util.Collection;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration> registrationList;

    public void start(BundleContext context) {
        registrationList = new ArrayList<ServiceRegistration>();

        //Register plugin here
        registrationList.add(context.registerService(getUpdateAPI.class.getName(), new getUpdateAPI(), null));

        registrationList.add(context.registerService(validationFile.class.getName(), new validationFile(), null));

        registrationList.add(context.registerService(fileUploadValidation.class.getName(), new fileUploadValidation(), null));

        registrationList.add(context.registerService(GetShipmentType.class.getName(), new GetShipmentType(), null));

        registrationList.add(context.registerService(createShipment.class.getName(), new createShipment(), null));

        registrationList.add(context.registerService(fileFieldValidator.class.getName(), new fileFieldValidator(), null));

        registrationList.add(context.registerService(downloadWaybill.class.getName(), new downloadWaybill(), null));

        registrationList.add(context.registerService(downloadWaybillPlugin.class.getName(), new downloadWaybillPlugin(), null));

        registrationList.add(context.registerService(editCheckRef.class.getName(), new editCheckRef(), null));

        registrationList.add(context.registerService(customTemplateUpload.class.getName(), new customTemplateUpload(), null));

        registrationList.add(context.registerService(downloadAllwaybill.class.getName(), new downloadAllwaybill(), null));

        registrationList.add(context.registerService(credentialValidator.class.getName(), new credentialValidator(), null));
    }

    public void stop(BundleContext context) {
        for (ServiceRegistration registration : registrationList) {
            registration.unregister();
        }
    }
}