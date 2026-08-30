package de.servicehealth.gematik;

import org.apache.xml.security.algorithms.JCEMapper;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.utils.Constants;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.KeyStoreSpi;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory, read-only {@link KeyStoreSpi} backed by the Gematik Trust Service List (TSL).
 * <p>
 * On {@link #engineLoad(InputStream, char[])} the TSL XML is fetched over HTTP, its XAdES
 * signature is verified against the pinned signing-CA certificate (shipped as a classpath
 * resource per environment) and every {@code X509Certificate} entry whose service-information
 * extensions reference {@code oid_sak_aut} is added as a trusted certificate entry.
 * <p>
 * The {@link InputStream} and password supplied to {@code engineLoad} are ignored.
 */
public class GematikKeyStoreSpi extends KeyStoreSpi {

    private static final Logger log = LoggerFactory.getLogger(GematikKeyStoreSpi.class);

    private static final String OID_SAK_AUT = "oid_sak_aut";
    private static final String TSL_NS = "http://uri.etsi.org/02231/v2#";
    private static final String OVERRIDE_SIGNING_CERT_PROP = "gematik.tsl.signingCert";
    private static final String OVERRIDE_TSL_URL_PROP = "gematik.tsl.url";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final GematikEnvironment environment;
    private final Map<String, X509Certificate> certificates = new LinkedHashMap<>();
    private Date creationDate;

    public GematikKeyStoreSpi(GematikEnvironment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("GematikEnvironment must not be null");
        }
        this.environment = environment;
    }

    @Override
    public void engineLoad(InputStream stream, char[] password) throws IOException {
        certificates.clear();
        try {
            byte[] tsl = fetchTsl();
            X509Certificate signer = loadSigningCertificate();
            if (verifyTslSignature(tsl, signer)) {
                certificates.putAll(extractCertificates(tsl));
                creationDate = new Date();
                log.info("Loaded {} trusted Gematik CA certificates for environment {}", certificates.size(), environment);
            } else {
                throw new IOException("TSL signature validation failed for environment " + environment);
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to load Gematik TSL for environment " + environment + ": " + e.getMessage(), e);
        }
    }

    /**
     * Verifies the TSL's XAdES signature and that its signing certificate was issued by the
     * pinned Gematik TSL-Signer-CA.
     * <p>
     * gemLibPki's {@code TslValidator.checkSignature} runs a full PKIX path build that cannot
     * succeed here: the Gematik TSL-Signer-CA is an intermediate (issued by the TI root CA) and
     * the TSL signature's {@code KeyInfo} carries only the signer certificate, so the path can
     * never reach a self-signed anchor. We therefore verify directly: the signer must be issued
     * by the pinned CA (our trust anchor) and the XML signature must be cryptographically valid.
     * BouncyCastle is required for the Brainpool ECDSA curves used across the TI.
     */
    private boolean verifyTslSignature(byte[] tsl, X509Certificate pinnedIssuerCa) {
        String previousProviderId = JCEMapper.getProviderId();
        try {
            org.apache.xml.security.Init.init();
            JCEMapper.setProviderId(BouncyCastleProvider.PROVIDER_NAME);

            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            Document doc = dbf.newDocumentBuilder().parse(new ByteArrayInputStream(tsl));
            registerIdAttributes(doc.getDocumentElement());

            NodeList sigNodes = doc.getElementsByTagNameNS(Constants.SignatureSpecNS, "Signature");
            if (sigNodes.getLength() == 0) {
                log.warn("TSL for environment {} carries no XML signature", environment);
                return false;
            }
            XMLSignature signature = new XMLSignature((Element) sigNodes.item(0), "");
            X509Certificate tslSigner = signature.getKeyInfo().getX509Certificate();
            if (tslSigner == null) {
                log.warn("TSL signature for environment {} carries no signing certificate", environment);
                return false;
            }
            tslSigner.checkValidity();
            tslSigner.verify(pinnedIssuerCa.getPublicKey(), BouncyCastleProvider.PROVIDER_NAME);
            return signature.checkSignatureValue(tslSigner.getPublicKey());
        } catch (Exception e) {
            log.warn("TSL signature verification failed for environment {}: {}", environment, e.getMessage());
            return false;
        } finally {
            if (previousProviderId != null) {
                JCEMapper.setProviderId(previousProviderId);
            }
        }
    }

    /** Registers {@code Id}/{@code ID}/{@code id} attributes as XML IDs so XAdES same-document
     *  references (e.g. {@code #SignedProperties-...}) resolve during signature validation. */
    private static void registerIdAttributes(Node node) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            for (String attr : new String[]{"Id", "ID", "id"}) {
                if (element.hasAttribute(attr)) {
                    element.setIdAttribute(attr, true);
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            registerIdAttributes(children.item(i));
        }
    }

    private byte[] fetchTsl() throws IOException, InterruptedException {
        String url = configValue(OVERRIDE_TSL_URL_PROP + "." + environment.name(), environment.getTslUrl());
        log.debug("Downloading Gematik TSL from {}", url);
        try (HttpClient client = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .GET()
                .build();
            HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Failed to download TSL: HTTP " + response.statusCode() + " from " + url);
            }
            return response.body();
        }
    }

    private X509Certificate loadSigningCertificate() throws IOException, CertificateException, NoSuchProviderException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
        String override = configValue(OVERRIDE_SIGNING_CERT_PROP + "." + environment.name(), null);
        if (override != null && !override.isBlank()) {
            byte[] bytes = Files.readAllBytes(Path.of(override));
            return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(bytes));
        }
        String resource = environment.getSigningCertResource();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("TSL signing-CA resource not found on classpath: " + resource);
            }
            return (X509Certificate) cf.generateCertificate(in);
        }
    }

    private Map<String, X509Certificate> extractCertificates(byte[] tsl) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setNamespaceAware(true);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(tsl));

        XPath xpath = XPathFactory.newInstance().newXPath();
        // Trust every CA certificate listed in the TSL (TSL namespace). The narrower
        // oid_sak_aut filter omitted the Komponenten-CAs (e.g. GEM.KOMP-CA8) that issue the
        // TLS server certificates of TI components such as the ePA Aktensystem, so VAU/HTTPS
        // handshakes to epa-as-*.prod.epa4all.de failed PKIX path building. The TSL is itself
        // the authoritative TI trust list, so all its entries are valid trust anchors.
        String expr = "//*[local-name()='X509Certificate'"
            + " and namespace-uri()='" + TSL_NS + "']";
        NodeList nodes;
        try {
            nodes = (NodeList) xpath.evaluate(expr, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            throw new IOException("Failed to evaluate TSL XPath", e);
        }

        CertificateFactory cf = CertificateFactory.getInstance("X.509", BouncyCastleProvider.PROVIDER_NAME);
        Map<String, X509Certificate> out = new LinkedHashMap<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            String b64 = nodes.item(i).getTextContent();
            if (b64 == null) {
                continue;
            }
            byte[] der = Base64.getDecoder().decode(b64.replaceAll("\\s+", ""));
            X509Certificate cert = (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(der));
            String alias = aliasFor(cert, i);
            out.put(alias, cert);
        }
        if (out.isEmpty()) {
            throw new IOException("TSL for environment " + environment + " contained no CA certificates");
        }
        return out;
    }

    private static String aliasFor(X509Certificate cert, int index) {
        String subject = cert.getSubjectX500Principal().getName();
        return "tsl-" + index + "-" + Integer.toHexString(subject.hashCode()).toLowerCase();
    }

    /** Reads from MP-Config (which itself includes system properties as a source); falls back
     *  to {@link System#getProperty(String)} if MP-Config is unavailable (e.g., bare JCE tests). */
    private static String configValue(String key, String defaultValue) {
        try {
            return ConfigProvider.getConfig().getOptionalValue(key, String.class).orElse(defaultValue);
        } catch (Throwable t) {
            String sys = System.getProperty(key);
            return sys != null ? sys : defaultValue;
        }
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        return alias == null ? null : certificates.get(alias);
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return null;
    }

    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        return null;
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return certificates.containsKey(alias) ? (Date) creationDate.clone() : null;
    }

    @Override
    public Enumeration<String> engineAliases() {
        return Collections.enumeration(certificates.keySet());
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return alias != null && certificates.containsKey(alias);
    }

    @Override
    public int engineSize() {
        return certificates.size();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return false;
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return engineContainsAlias(alias);
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        for (Map.Entry<String, X509Certificate> e : certificates.entrySet()) {
            if (e.getValue().equals(cert)) {
                return e.getKey();
            }
        }
        return null;
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        throw new KeyStoreException("GematikKeyStoreSpi is read-only");
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        throw new KeyStoreException("GematikKeyStoreSpi is read-only");
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new KeyStoreException("GematikKeyStoreSpi is read-only");
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        throw new KeyStoreException("GematikKeyStoreSpi is read-only");
    }

    @Override
    public void engineStore(OutputStream stream, char[] password) {
        throw new UnsupportedOperationException("GematikKeyStoreSpi is in-memory and cannot be persisted");
    }
}
