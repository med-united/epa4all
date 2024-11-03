package de.servicehealth.epa4all.server.smcb;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.zip.GZIPInputStream;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.vsds.VSDService;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;

@ApplicationScoped
@Startup
public class WebdavSmcbManager {

    private static final Logger log = Logger.getLogger(WebdavSmcbManager.class.getName());

    private final Map<String, Set<FolderInfo>> smcbDocsMap = new ConcurrentHashMap<>();

    private final WebdavConfig webdavConfig;
    private final File rootFolder;
    
    static JAXBContext readVSDJaxbContext;
    static {
    	try {
			readVSDJaxbContext = JAXBContext.newInstance(ReadVSDResponse.class);
		} catch (JAXBException e) {
			log.log(Level.SEVERE, "Could not create JAXBContext", e);
		}
    }
    
	@Inject
	@TelematikId
	String telematikId;

    @Inject
    public WebdavSmcbManager(WebdavConfig webdavConfig) {
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
        } catch (Exception e) {
            log.log(Level.SEVERE, "Error while initing SMC-B folders", e);
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
    
    public void onRead(@Observes ReadVSDResponse readVSDResponse) {
    	checkOrCreateSmcbFolders(telematikId);
    	
    	Document doc;
		try {
			doc = VSDService.createDocument(readVSDResponse);
			String xInsurantid = VSDService.getKVNRFromResponseOrDoc(readVSDResponse, doc);
	    	
	    	String path = rootFolder.getAbsolutePath() + File.separator + telematikId+File.separator+xInsurantid+File.separator+"local";
	    	File localFolder = new File(path);
	    	if(!localFolder.exists()) {
	    		localFolder.mkdirs();
	    		path = rootFolder.getAbsolutePath() + File.separator + telematikId;
	    		Path smcb = Files.createDirectories(Paths.get(path));
	            String smcbFolder = smcb.toFile().getAbsolutePath();
	            Set<FolderInfo> folders = smcbDocsMap.computeIfAbsent(smcbFolder, f -> new HashSet<>());
	            webdavConfig.getSmcbFolders().forEach(f -> {
	                try {
	                    String[] parts = f.split("_");
	                    String folderPath = smcbFolder+File.separator+xInsurantid + File.separator + parts[0];
	                    Path dir = Paths.get(folderPath);
	                    if (!dir.toFile().exists()) {
	                        try {
	                            Path folder = Files.createDirectories(dir);
	                            folders.add(new FolderInfo(folder.toFile().getAbsolutePath(), parts[1]));
	                        } catch (Exception e) {
	                        	log.log(Level.SEVERE, String.format("Unable to create SMC-B folder [%s]", folderPath), e);
	                        }
	                    }
	                } catch (Exception e) {
	        			log.log(Level.SEVERE, "Could not get registration number", e);
	                }
	            });
	    	}
	    	File readVSDResponseFile = new File(localFolder, "ReadVSDResponse.xml");
	    	if(!readVSDResponseFile.exists()) {
	    		readVSDResponseFile.createNewFile();
	    	}
	    	readVSDJaxbContext.createMarshaller().marshal(readVSDResponse, new FileOutputStream(readVSDResponseFile));
	    	File allgemeineVersicherungsdatenFile = new File(localFolder, "AllgemeineVersicherungsdaten.xml");
	    	unzipAndSaveDataToFile(readVSDResponse.getAllgemeineVersicherungsdaten(), allgemeineVersicherungsdatenFile);
	    	File persoenlicheVersichertendatenFile = new File(localFolder, "PersoenlicheVersichertendaten.xml");
	    	unzipAndSaveDataToFile(readVSDResponse.getPersoenlicheVersichertendaten(), persoenlicheVersichertendatenFile);
	    	File geschuetzteVersichertendatenFile = new File(localFolder, "GeschuetzteVersichertendaten.xml");
	    	unzipAndSaveDataToFile(readVSDResponse.getGeschuetzteVersichertendaten(), geschuetzteVersichertendatenFile);
		} catch (Exception e) {
			log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
		}
    	
    	
    }

	private void unzipAndSaveDataToFile(byte[] dataForWriting, File outputFile) {
		if(!outputFile.exists()) {
			try {
				outputFile.createNewFile();
			} catch (IOException e) {
				log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
			}
		}
		try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
		    outputStream.write(decompress(dataForWriting));
		} catch (IOException e) {
			log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
		}
		
	}
	
	public static byte[] decompress(final byte[] bytes) throws IOException {
		if (bytes == null || bytes.length == 0) {
			return new byte[0];
		}
		try (final GZIPInputStream ungzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] data = new byte[8192];
			int nRead;
			while ((nRead = ungzip.read(data)) != -1) {
				out.write(data, 0, nRead);
			}
			return out.toByteArray();
		}
	}
}
