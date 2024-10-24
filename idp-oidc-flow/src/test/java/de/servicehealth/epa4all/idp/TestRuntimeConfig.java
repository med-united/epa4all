package de.servicehealth.epa4all.idp;

import de.servicehealth.config.KonnektorDefaultConfig;
import de.servicehealth.config.api.IRuntimeConfig;
import de.servicehealth.config.api.IUserConfigurations;
import de.servicehealth.config.api.UserRuntimeConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

import org.junit.jupiter.api.Test;

public class TestRuntimeConfig implements UserRuntimeConfig {

    private final KonnektorDefaultConfig konnektorDefaultConfig;

    public TestRuntimeConfig(KonnektorDefaultConfig konnektorDefaultConfig) {
        this.konnektorDefaultConfig = konnektorDefaultConfig;
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
        return new IUserConfigurations() {

			@Override
			public String getBasicAuthUsername() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getBasicAuthPassword() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getClientCertificate() {
				try {
					return ","+Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(konnektorDefaultConfig.getCertAuthStoreFile())));
				} catch (IOException e) {
					e.printStackTrace();
					return null;
				}
			}

			@Override
			public String getClientCertificatePassword() {
				return konnektorDefaultConfig.getCertAuthStoreFilePassword();
			}

			@Override
			public String getErixaHotfolder() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getErixaDrugstoreEmail() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getErixaUserEmail() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getErixaUserPassword() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getErixaApiKey() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getMuster16TemplateProfile() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getConnectorBaseURL() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getMandantId() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getWorkplaceId() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getClientSystemId() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getUserId() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getVersion() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public void setVersion(String version) {
				// TODO Auto-generated method stub
				
			}

			@Override
			public String getTvMode() {
				// TODO Auto-generated method stub
				return null;
			}

			@Override
			public String getPruefnummer() {
				// TODO Auto-generated method stub
				return null;
			}
        	
        };
    }

    @Override
    public IRuntimeConfig getRuntimeConfig() {
        return null;
    }

    @Override
    public UserRuntimeConfig copy() {
        return null;
    }

    @Override
    public void updateProperties(IUserConfigurations userConfigurations) {

    }
}
