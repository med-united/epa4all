package de.servicehealth.config.api;

import jakarta.servlet.http.HttpServletRequest;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

@SuppressWarnings("unused")
public interface IUserConfigurations {

    String getBasicAuthUsername();

    String getBasicAuthPassword();

    String getClientCertificate();

    String getClientCertificatePassword();

    String getErixaHotfolder();

    String getErixaDrugstoreEmail();

    String getErixaUserEmail();

    String getErixaUserPassword();

    String getErixaApiKey();

    String getMuster16TemplateProfile();

    String getConnectorBaseURL();

    String getMandantId();

    String getWorkplaceId();

    String getClientSystemId();

    String getUserId();

    String getVersion();

    void setVersion(String version);

    String getTvMode();

    String getPruefnummer();

    default IUserConfigurations updateWithRequest(HttpServletRequest httpServletRequest) {
        return updateWithRequest(httpServletRequest, List.of("X-eHBAHandle", "X-SMCBHandle", "X-sendPreview"));
    }

    default IUserConfigurations updateWithRequest(
        HttpServletRequest httpServletRequest,
        List<String> skippedHeaders
    ) {
        Enumeration<String> enumeration = httpServletRequest.getHeaderNames();
        List<String> list = Collections.list(enumeration);
        for (String headerName : list) {
            if (headerName.startsWith("X-") && skippedHeaders.stream().noneMatch(s -> s.equals(headerName))) {
                String propertyName = headerName.substring(2);
                Field field;
                try {
                    field = this.getClass().getDeclaredField(propertyName);
                    field.setAccessible(true);
                    field.set(this, httpServletRequest.getHeader(headerName));
                } catch (NoSuchFieldException | RuntimeException | IllegalAccessException ignored) {
                }
            }
        }
        return this;
    }
}
