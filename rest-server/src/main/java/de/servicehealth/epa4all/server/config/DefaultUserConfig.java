package de.servicehealth.epa4all.server.config;

import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.IRuntimeConfig;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.idp.IdpConfig;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DefaultUserConfig implements UserRuntimeConfig {

    private final KonnektorDefaultConfig konnektorDefaultConfig;
    private final IUserConfigurations userConfigurations;
    private final IRuntimeConfig runtimeConfig;

    public DefaultUserConfig(
        KonnektorDefaultConfig konnektorDefaultConfig,
        IdpConfig idpConfig
    ) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;

        runtimeConfig = new IRuntimeConfig() {
            @Override
            public String getEHBAHandle() {
                return null;
            }

            @Override
            public String getSMCBHandle() {
                return null;
            }

            @Override
            public void setEHBAHandle(String eHBAHandle) {

            }

            @Override
            public void setSMCBHandle(String smcbHandle) {

            }

            @Override
            public boolean isSendPreview() {
                return false;
            }

            @Override
            public String getIdpAuthRequestRedirectURL() {
                return idpConfig.getAuthRequestRedirectUrl();
            }

            @Override
            public String getIdpClientId() {
                return idpConfig.getClientId();
            }
        };

        userConfigurations = new IUserConfigurations() {
            @Override
            public String getBasicAuthUsername() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getBasicAuthPassword() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getClientCertificate() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getClientCertificatePassword() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getErixaHotfolder() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getErixaDrugstoreEmail() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getErixaUserEmail() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getErixaUserPassword() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getErixaApiKey() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getMuster16TemplateProfile() {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getConnectorBaseURL() {
                return konnektorDefaultConfig.getUrl();
            }

            @Override
            public String getMandantId() {
                return konnektorDefaultConfig.getMandantId();
            }

            @Override
            public String getWorkplaceId() {
                return konnektorDefaultConfig.getWorkplaceId();
            }

            @Override
            public String getClientSystemId() {
                return konnektorDefaultConfig.getClientSystemId();
            }

            @Override
            public String getUserId() {
                return konnektorDefaultConfig.getUserId().orElse(null);
            }

            @Override
            public String getVersion() {
                return konnektorDefaultConfig.getVersion();
            }

            @Override
            public void setVersion(String version) {
                throw new UnsupportedOperationException("Not implemented");
            }

            @Override
            public String getTvMode() {
                return konnektorDefaultConfig.getTvMode();
            }

            @Override
            public String getPruefnummer() {
                throw new UnsupportedOperationException("Not implemented");
            }
        };
    }

    @Override
    public String getConnectorBaseURL() {
        return konnektorDefaultConfig.getUrl();
    }

    @Override
    public String getConnectorVersion() {
        return konnektorDefaultConfig.getVersion();
    }

    @Override
    public String getMandantId() {
        return konnektorDefaultConfig.getMandantId();
    }

    @Override
    public String getWorkplaceId() {
        return konnektorDefaultConfig.getWorkplaceId();
    }

    @Override
    public String getClientSystemId() {
        return konnektorDefaultConfig.getClientSystemId();
    }

    @Override
    public String getUserId() {
        return konnektorDefaultConfig.getUserId().orElse(null);
    }

    @Override
    public IUserConfigurations getUserConfigurations() {
        return userConfigurations;
    }

    @Override
    public IRuntimeConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    @Override
    public UserRuntimeConfig copy() {
        return new AppConfig(konnektorDefaultConfig, userConfigurations);
    }

    @Override
    public void updateProperties(IUserConfigurations userConfigurations) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
