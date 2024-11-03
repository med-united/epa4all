package de.servicehealth.epa4all.server.smcb;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DLSequence;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.bouncycastle.asn1.isismtt.x509.Admissions;
import org.bouncycastle.asn1.util.ASN1Dump;

import de.servicehealth.epa4all.server.config.WebdavConfig;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@Startup
public class SmcbManager {

    private static final Logger log = Logger.getLogger(SmcbManager.class.getName());

    private final Map<String, Set<FolderInfo>> smcbDocsMap = new ConcurrentHashMap<>();

    private final WebdavConfig webdavConfig;
    private final File rootFolder;

    @Inject
    public SmcbManager(WebdavConfig webdavConfig) {
        this.webdavConfig = webdavConfig;
        rootFolder = new File(webdavConfig.getRootFolder());
    }

    public void checkOrCreateSmcbFolders(String telematikId) {
        if (!rootFolder.exists()) {
            throw new IllegalStateException("Root SMC-B folder is absent");
        }
        String path = rootFolder.getAbsolutePath() + File.separator + telematikId;
        try {
            Path smcb = Files.createDirectories(Paths.get(path));
            String smcbFolder = smcb.toFile().getAbsolutePath();
            Set<FolderInfo> folders = smcbDocsMap.computeIfAbsent(smcbFolder, f -> new HashSet<>());
            webdavConfig.getSmcbFolders().forEach(f -> {
                try {
                    String[] parts = f.split("_");
                    String folderPath = smcbFolder + File.separator + parts[0];
                    Path dir = Paths.get(folderPath);
                    if (!dir.toFile().exists()) {
                        try {
                            Path folder = Files.createDirectories(dir);
                            folders.add(new FolderInfo(folder.toFile().getAbsolutePath(), parts[1]));
                        } catch (Exception e) {
                            log.severe(String.format("Unable to create SMC-V folder [%s]", folderPath));
                        }
                    }
                } catch (Exception e) {
                    throw new IllegalStateException(e.getMessage());
                }
            });
        } catch (Exception e) {
            log.severe("Error while initing SMC-B folders -> " + e.getMessage());
        }
    }
    
    @SuppressWarnings("resource")
	public static String extractTelematikIdFromCertificate(X509Certificate cert) {
    	// https://oidref.com/1.3.36.8.3.3
    	byte[] admission = cert.getExtensionValue(ISISMTTObjectIdentifiers.id_isismtt_at_admission.toString());
    	
    	ASN1InputStream input = new ASN1InputStream(admission);

        ASN1Primitive p;
        try {
        	// Based on https://stackoverflow.com/a/20439748
			while ((p = input.readObject()) != null) { 
		        DEROctetString derOctetString = (DEROctetString) p;
		        ASN1InputStream asnInputStream = new ASN1InputStream(new ByteArrayInputStream(derOctetString.getOctets()));
		        ASN1Primitive asn1 = asnInputStream.readObject();
		        AdmissionSyntax admissionSyntax = AdmissionSyntax.getInstance(asn1);
		        return admissionSyntax.getContentsOfAdmissions()[0].getProfessionInfos()[0].getRegistrationNumber();

			}
		} catch (IOException e) {
			log.log(Level.SEVERE, "Could not get registration number", e);
		}
    	return null;
    }
}
