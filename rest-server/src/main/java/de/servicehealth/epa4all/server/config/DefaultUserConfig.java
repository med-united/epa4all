package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IRuntimeConfig;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;

@ApplicationScoped
@Default
public class DefaultUserConfig implements UserRuntimeConfig {

    private final KonnektorDefaultConfig konnektorDefaultConfig;
    private final IUserConfigurations userConfigurations;
    private final IRuntimeConfig runtimeConfig;

    public DefaultUserConfig(KonnektorDefaultConfig konnektorDefaultConfig, IdpConfig idpConfig) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;

        runtimeConfig = new InternalRuntimeConfig(idpConfig.getClientId(), idpConfig.getAuthRequestRedirectUrl());

        userConfigurations = new IUserConfigurations() {
            @Override
            public String getBasicAuthUsername() {
                return null;
            }

            @Override
            public String getBasicAuthPassword() {
            	return null;
            }

            @Override
            public String getClientCertificate() {
                return null; // defaultSSLContext will be used from konnektor.default.cert.auth.store.file
            }

            @Override
            public String getClientCertificatePassword() {
                return null;
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
        return new RuntimeConfig(konnektorDefaultConfig, userConfigurations);
    }

    @Override
    public void updateProperties(IUserConfigurations userConfigurations) {
        throw new UnsupportedOperationException("Not implemented");
    }
}
