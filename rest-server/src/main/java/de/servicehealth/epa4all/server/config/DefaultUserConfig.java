package de.servicehealth.epa4all.server.config;

import de.health.service.cetp.config.KonnektorAuth;
import de.health.service.cetp.config.KonnektorDefaultConfig;
import de.health.service.config.api.IRuntimeConfig;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.idp.IdpConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;

import static de.health.service.cetp.config.KonnektorAuth.CERTIFICATE;

@ApplicationScoped
@Default
public class DefaultUserConfig implements UserRuntimeConfig {

    private static final Logger log = LoggerFactory.getLogger(DefaultUserConfig.class.getName());

    private final KonnektorDefaultConfig konnektorDefaultConfig;
    private final IUserConfigurations userConfigurations;
    private final IRuntimeConfig runtimeConfig;

    public DefaultUserConfig(KonnektorDefaultConfig konnektorDefaultConfig, IdpConfig idpConfig) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;

        runtimeConfig = new InternalRuntimeConfig(idpConfig.getClientId(), idpConfig.getAuthRequestRedirectUrl());

        userConfigurations = new IUserConfigurations() {

            private String clientCertificate;

            @Override
            public KonnektorAuth getKonnektorAuth() {
                return konnektorDefaultConfig.getAuth().orElse(CERTIFICATE);
            }

            @Override
            public String getBasicAuthUsername() {
                return konnektorDefaultConfig.getBasicAuthUsername().orElse(null);
            }

            @Override
            public String getBasicAuthPassword() {
            	return konnektorDefaultConfig.getBasicAuthPassword().orElse(null);
            }

            @Override
            public String getClientCertificate() {
                if (clientCertificate == null) {
                    Optional<String> certAuthStoreFileOpt = konnektorDefaultConfig.getCertAuthStoreFile();
                    if (certAuthStoreFileOpt.isPresent()) {
                        String certFilePath = certAuthStoreFileOpt.get();
                        try {
                            byte[] certBytes = Files.readAllBytes(Paths.get(certFilePath));
                            String prefix = "data:application/x-pkcs12;base64,";
                            clientCertificate = prefix + Base64.getEncoder().encodeToString(certBytes).replace("\n", "");
                        } catch (Exception e) {
                            log.error("Unable to read certificate from " + certFilePath, e);
                        }
                    }
                }
                return clientCertificate;
            }

            @Override
            public String getClientCertificatePassword() {
                return konnektorDefaultConfig.getCertAuthStoreFilePassword().orElse(null);
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
