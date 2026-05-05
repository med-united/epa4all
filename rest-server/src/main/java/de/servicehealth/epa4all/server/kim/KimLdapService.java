package de.servicehealth.epa4all.server.kim;

import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.SearchResult;
import com.unboundid.ldap.sdk.SearchResultEntry;
import de.health.service.config.api.IUserConfigurations;
import de.health.service.config.api.UserRuntimeConfig;
import de.servicehealth.epa4all.server.presription.PrescriptionSendException;
import de.servicehealth.utils.SSLUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

import static com.unboundid.ldap.sdk.SearchScope.SUB;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;

@ApplicationScoped
public class KimLdapService {

    private static final Logger log = LoggerFactory.getLogger(KimLdapService.class.getName());

    @Inject
    KimLdapConfig ldapConfig;

    // The KIM LDAP target (port 16636 on the konnektor host) is the konnektor's own LDAP
    // proxy. The konnektor presents a TI-PKI certificate (Komponenten-CA), so we validate
    // against the Gematik TSL trust set rather than JDK cacerts. Hostname verification
    // is not relevant here — UnboundID's LDAPConnection uses the SSLSocketFactory only
    // for chain validation; it doesn't run JSSE endpoint identification on the IP-addressed
    // konnektor host.
    @Inject
    X509TrustManager trustManager;

    public String searchKimAddress(UserRuntimeConfig runtimeConfig, String name) throws PrescriptionSendException {
        try {
            if (name == null || name.length() < 3) {
                throw new IllegalArgumentException("name too short");
            }
            IUserConfigurations userConfigurations = runtimeConfig.getUserConfigurations();
            String certificate = userConfigurations.getClientCertificate();
            String password = userConfigurations.getClientCertificatePassword();
            SSLContext sslContext = SSLUtils.createSSLContext(certificate, password, trustManager, null);

            try (LDAPConnection connection = new LDAPConnection(
                sslContext.getSocketFactory(), runtimeConfig.getKonnektorHost(), ldapConfig.getLdapPort()
            )) {
                String filter = String.format("(displayName=*%s*)", name);
                String baseDN = "dc=data,dc=vzd";
                SearchResult searchResult = connection.search(baseDN, SUB, filter, "rfc822mailbox", "displayName");
                for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                    String kimAddress = entry.getAttributeValue("rfc822mailbox");
                    if (kimAddress != null) {
                        return kimAddress;
                    }
                }
            }
            throw new IllegalArgumentException("not found");
        } catch (Exception e) {
            log.warn("Could not search LDAP", e);
            String message = "Error while searching KIM address for: '" + name + "' - " + e.getMessage();
            throw new PrescriptionSendException(message, NOT_FOUND);
        }
    }
}
