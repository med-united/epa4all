package de.servicehealth.epa4all.server.kim;

import com.unboundid.ldap.sdk.Filter;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private static final String BASE_DN = "dc=data,dc=vzd";
    private static final String[] KIM_ATTRS = {"mail", "kimData", "rfc822mailbox", "displayName"};

    /** Back-compat entry point: resolve a KIM address by practitioner name only. */
    public String searchKimAddress(UserRuntimeConfig runtimeConfig, String name) throws PrescriptionSendException {
        return searchKimAddress(runtimeConfig, null, null, name);
    }

    /**
     * Resolves a KIM address, preferring the *institution* (Betriebsstätte/SMC-B) —
     * in VZD the KIM mailbox lives on the institution entry, not the individual
     * doctor's HBA entry (which usually has none). Tries, in order:
     *   1. the organization's Telematik-ID (exact match),
     *   2. the organization name (order-independent token match),
     *   3. the practitioner name (fallback).
     */
    public String searchKimAddress(
        UserRuntimeConfig runtimeConfig,
        String orgTelematikId,
        String orgName,
        String practitionerName
    ) throws PrescriptionSendException {
        String label = firstNonBlank(orgTelematikId, orgName, practitionerName);
        try {
            IUserConfigurations userConfigurations = runtimeConfig.getUserConfigurations();
            String certificate = userConfigurations.getClientCertificate();
            String password = userConfigurations.getClientCertificatePassword();
            SSLContext sslContext = SSLUtils.createSSLContext(certificate, password, trustManager, null);

            List<Filter> strategies = new ArrayList<>();
            if (isPresent(orgTelematikId)) {
                strategies.add(Filter.createEqualityFilter("telematikID", orgTelematikId.trim()));
            }
            if (isPresent(orgName)) {
                strategies.add(buildNameFilter(orgName));
            }
            if (isPresent(practitionerName)) {
                strategies.add(buildNameFilter(practitionerName));
            }
            if (strategies.isEmpty()) {
                throw new IllegalArgumentException("no organization/practitioner identifier to search by");
            }

            try (LDAPConnection connection = new LDAPConnection(
                sslContext.getSocketFactory(), runtimeConfig.getKonnektorHost(), ldapConfig.getLdapPort()
            )) {
                for (Filter filter : strategies) {
                    SearchResult searchResult = connection.search(BASE_DN, SUB, filter, KIM_ATTRS);
                    for (SearchResultEntry entry : searchResult.getSearchEntries()) {
                        String kimAddress = extractKimAddress(entry);
                        if (kimAddress != null) {
                            return kimAddress;
                        }
                    }
                }
            }
            throw new IllegalArgumentException("not found");
        } catch (Exception e) {
            log.warn("Could not search LDAP", e);
            String message = "Error while searching KIM address for: '" + label + "' - " + e.getMessage();
            throw new PrescriptionSendException(message, NOT_FOUND);
        }
    }

    private static boolean isPresent(String s) {
        return s != null && s.trim().length() >= 3;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /**
     * Reads the KIM address from a VZD entry. The gematik VZD stores KIM mailboxes
     * in `mail` (and `kimData` = "address,version" or "version,address"); the old
     * `rfc822mailbox` attribute is empty in current directories, so it's only a
     * last-resort fallback.
     */
    static String extractKimAddress(SearchResultEntry entry) {
        String mail = entry.getAttributeValue("mail");
        if (mail != null && !mail.isBlank()) {
            return mail;
        }
        String kimData = entry.getAttributeValue("kimData");
        if (kimData != null && !kimData.isBlank()) {
            for (String part : kimData.split(",")) {
                if (part.contains("@")) {
                    return part.trim();
                }
            }
        }
        return entry.getAttributeValue("rfc822mailbox");
    }

    // Academic-title / honorific tokens that the practitioner name may carry but the
    // VZD displayName usually omits — dropped so they don't cause false negatives.
    private static final Set<String> IGNORED_NAME_TOKENS =
        Set.of("dr", "prof", "med", "dr.", "prof.", "med.", "h.c.", "hc");

    /**
     * Builds an order-independent name filter: one (displayName=*token*) per name
     * word, AND-combined. The practitioner name arrives as "Given [Nobility] Family"
     * but the VZD stores displayName as "Family, Given Nobility" — a single
     * exact-order substring never matches, whereas an AND of per-token substrings
     * matches regardless of order (and tolerates nobility particles like "Freifrau"
     * appearing between the parts). Academic titles are ignored to avoid false
     * negatives; UnboundID's Filter API escapes each token.
     */
    static Filter buildNameFilter(String name) {
        List<Filter> parts = new ArrayList<>();
        for (String token : name.trim().split("\\s+")) {
            if (token.isBlank() || IGNORED_NAME_TOKENS.contains(token.toLowerCase())) {
                continue;
            }
            parts.add(Filter.createSubstringFilter("displayName", null, new String[]{token}, null));
        }
        // Fall back to the whole string if tokenization produced nothing usable.
        if (parts.isEmpty()) {
            parts.add(Filter.createSubstringFilter("displayName", null, new String[]{name.trim()}, null));
        }
        return parts.size() == 1 ? parts.get(0) : Filter.createANDFilter(parts);
    }
}
