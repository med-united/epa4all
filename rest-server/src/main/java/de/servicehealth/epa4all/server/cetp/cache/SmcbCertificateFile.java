package de.servicehealth.epa4all.server.cetp.cache;

import de.health.service.cetp.CertificateInfo;
import de.servicehealth.epa4all.server.file.MapDumpFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Map;

import static de.health.service.cetp.konnektorconfig.FSConfigService.CONFIG_DELIMETER;

public class SmcbCertificateFile extends MapDumpFile<String, CertificateInfo> {

    public static final String SMCB_CERTIFICATE_FILE_NAME = "smcb-certificate";

    public SmcbCertificateFile(File folder) throws IOException {
        super(folder);
    }

    @Override
    protected String getFileName() {
        return SMCB_CERTIFICATE_FILE_NAME;
    }

    @Override
    protected Pair<String, CertificateInfo> deserialize(String line) {
        String[] parts = line.split(CONFIG_DELIMETER);
        String smcb = parts[0].trim();
        String signatureType = parts[1].trim();
        String base64Certificate = parts[2].trim();
        byte[] decoded = Base64.getDecoder().decode(base64Certificate);
        try (ObjectInputStream is = new ObjectInputStream(new ByteArrayInputStream(decoded))) {
            X509Certificate certificate = (X509Certificate) is.readObject();
            return Pair.of(smcb, new CertificateInfo(certificate, signatureType));
        } catch (Exception e) {
            log.error(String.format("Unable to read '%s' file: %s", SMCB_CERTIFICATE_FILE_NAME, e.getMessage()));
        }
        return null;
    }

    @Override
    protected String serialize(Map.Entry<String, CertificateInfo> entry) {
        String smcb = entry.getKey();
        CertificateInfo certificateInfo = entry.getValue();
        String signatureType = certificateInfo.getSignatureType();
        X509Certificate certificate = certificateInfo.getCertificate();
        ByteArrayOutputStream byteArrayStream = new ByteArrayOutputStream();
        try (ObjectOutputStream os = new ObjectOutputStream(byteArrayStream)) {
            os.writeObject(certificate);
        } catch (Exception e) {
            log.error(String.format("Unable to store '%s' file: %s", SMCB_CERTIFICATE_FILE_NAME, e.getMessage()));
            return null;
        }
        String base64Certificate = Base64.getEncoder().encodeToString(byteArrayStream.toByteArray());
        return String.join(CONFIG_DELIMETER, smcb, signatureType, base64Certificate);
    }
}