package de.servicehealth.epa4all.server.smcb;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.servicehealth.epa4all.server.cdi.TelematikId;
import de.servicehealth.epa4all.server.config.WebdavConfig;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils;
import de.servicehealth.epa4all.server.insurance.ReadVSDResponseEx;
import io.quarkus.runtime.Startup;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.isismtt.ISISMTTObjectIdentifiers;
import org.bouncycastle.asn1.isismtt.x509.AdmissionSyntax;
import org.w3c.dom.Document;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCDocument;

@ApplicationScoped
@Startup
public class WebdavSmcbManager {

    private static final Logger log = Logger.getLogger(WebdavSmcbManager.class.getName());
    public static final String ALLGEMEINE_VERSICHERUNGSDATEN_XML = "AllgemeineVersicherungsdaten.xml";
    public static final String PERSOENLICHE_VERSICHERTENDATEN_XML = "PersoenlicheVersichertendaten.xml";
    public static final String GESCHUETZTE_VERSICHERTENDATEN_XML = "GeschuetzteVersichertendaten.xml";
    public static final String PRUEFUNGSNACHWEIS_XML = "Pruefungsnachweis.xml";

    private final Map<String, Set<FolderInfo>> smcbDocsMap = new ConcurrentHashMap<>();
    private final Map<String, InsuranceData> kvnrToInsuranceMap = new ConcurrentHashMap<>();

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

    private final WebdavConfig webdavConfig;
    private final File rootFolder;

    @Inject
    public WebdavSmcbManager(WebdavConfig webdavConfig) {
        this.webdavConfig = webdavConfig;

        rootFolder = new File(webdavConfig.getRootFolder());
        if (!rootFolder.exists()) {
            throw new IllegalStateException("Root SMC-B folder is absent");
        }
    }

    void onStart(@Observes @Priority(5200) StartupEvent ev) {
        File[] telematikFolders = rootFolder.listFiles(File::isDirectory);
        if (telematikFolders != null) {
            Arrays.stream(telematikFolders).flatMap(dir -> {
                    File[] insurantFolders = dir.listFiles(File::isDirectory);
                    return insurantFolders != null ? Stream.of(insurantFolders) : Stream.empty();
                }).map(kvnrFolder -> {
                    String insurantId = kvnrFolder.getName();
                    File[] localFolders = kvnrFolder.listFiles(f -> f.isDirectory() && f.getName().equals("local"));
                    if (localFolders != null) {
                        Optional<File> localFolderOpt = Arrays.stream(localFolders).findFirst();
                        return localFolderOpt.map(file -> getLocalInsuranceData(file, insurantId)).orElse(null);
                    } else {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .forEach(data -> kvnrToInsuranceMap.put(data.getXInsurantId(), data));
        }
    }

    public Path applyTelematikPath(String telematikId) throws Exception {
        String path = rootFolder.getAbsolutePath() + File.separator + telematikId;
        return Files.createDirectories(Paths.get(path));
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

    public void onRead(@Observes ReadVSDResponseEx readVSDResponseEx) {
        try {
            String telematikId = readVSDResponseEx.getTelematikId();
            String xInsurantid = readVSDResponseEx.getXInsurantId();
            prepareLocalXMLs(telematikId, xInsurantid, readVSDResponseEx.getReadVSDResponse());

            Path telematikPath = applyTelematikPath(telematikId);
            String telematikFolder = telematikPath.toFile().getAbsolutePath();
            Set<FolderInfo> folders = smcbDocsMap.computeIfAbsent(telematikFolder, f -> new HashSet<>());
            webdavConfig.getSmcbFolders().forEach(folderSetting ->
                initFolder(folders, xInsurantid, telematikFolder, folderSetting)
            );
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
        }
    }

    public InsuranceData getLocalInsuranceData(String telematikId, String kvnr) {
        if (kvnr == null) {
            return null;
        }
        return kvnrToInsuranceMap.computeIfAbsent(kvnr, insurantId -> {
            try {
                File localInsurantFolder = getLocalInsurantFolder(telematikId, kvnr);
                return getLocalInsuranceData(localInsurantFolder, kvnr);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private InsuranceData getLocalInsuranceData(File localInsurantFolder, String kvnr) {
        try {
            File readVSDResponseFile = new File(localInsurantFolder, "ReadVSDResponse.xml");
            if (readVSDResponseFile.exists()) {
                byte[] allgemeineVersicherungsdaten = Files.readAllBytes(localInsurantFolder.toPath().resolve(ALLGEMEINE_VERSICHERUNGSDATEN_XML));
                byte[] persoenlicheVersichertendaten = Files.readAllBytes(localInsurantFolder.toPath().resolve(PERSOENLICHE_VERSICHERTENDATEN_XML));
                byte[] geschuetzteVersichertendaten = Files.readAllBytes(localInsurantFolder.toPath().resolve(GESCHUETZTE_VERSICHERTENDATEN_XML));
                byte[] pruefungsnachweis = Files.readAllBytes(localInsurantFolder.toPath().resolve(PRUEFUNGSNACHWEIS_XML));

                Document doc = InsuranceXmlUtils.createDocument(pruefungsnachweis);
                String pz = doc.getElementsByTagName("PZ").item(0).getTextContent();

                return new InsuranceData(
                    pz,
                    kvnr,
                    createUCDocument(persoenlicheVersichertendaten),
                    createUCDocument(geschuetzteVersichertendaten),
                    createUCDocument(allgemeineVersicherungsdaten)
                );
            } else {
                return null;
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Error while reading insurance data for KVNR=%s", kvnr));
            return null;
        }
    }

    private void prepareLocalXMLs(String telematikId, String xInsurantid, ReadVSDResponse readVSDResponse) throws Exception {
        File localInsurantFolder = getLocalInsurantFolder(telematikId, xInsurantid);
        File readVSDResponseFile = new File(localInsurantFolder, "ReadVSDResponse.xml");
        if (!readVSDResponseFile.exists()) {
            readVSDResponseFile.createNewFile();
        }
        readVSDJaxbContext.createMarshaller().marshal(readVSDResponse, new FileOutputStream(readVSDResponseFile));
        File allgemeineVersicherungsdatenFile = new File(localInsurantFolder, ALLGEMEINE_VERSICHERUNGSDATEN_XML);
        unzipAndSaveDataToFile(readVSDResponse.getAllgemeineVersicherungsdaten(), allgemeineVersicherungsdatenFile);
        File persoenlicheVersichertendatenFile = new File(localInsurantFolder, PERSOENLICHE_VERSICHERTENDATEN_XML);
        unzipAndSaveDataToFile(readVSDResponse.getPersoenlicheVersichertendaten(), persoenlicheVersichertendatenFile);
        File geschuetzteVersichertendatenFile = new File(localInsurantFolder, GESCHUETZTE_VERSICHERTENDATEN_XML);
        unzipAndSaveDataToFile(readVSDResponse.getGeschuetzteVersichertendaten(), geschuetzteVersichertendatenFile);
        File pruefungsnachweisFile = new File(localInsurantFolder, PRUEFUNGSNACHWEIS_XML);
        unzipAndSaveDataToFile(readVSDResponse.getPruefungsnachweis(), pruefungsnachweisFile);
    }

    private File getLocalInsurantFolder(String telematikId, String xInsurantid) throws Exception {
        Path telematikPath = applyTelematikPath(telematikId);
        Path localPath = Files.createDirectories(Paths.get(
            telematikPath.toFile().getAbsolutePath() + File.separator + xInsurantid + File.separator + "local"
        ));
        return localPath.toFile();
    }

    private void initFolder(
        Set<FolderInfo> folders,
        String xInsurantid,
        String telematikFolder,
        String folderSetting
    ) {
        String[] parts = folderSetting.split("_");
        String name = parts[0];
        String uuid = parts[1];
        String folderPath = telematikFolder + File.separator + xInsurantid + File.separator + name;
        File dir = new File(folderPath);
        Path folder = null;
        if (dir.exists()) {
            folder = Paths.get(dir.toURI());
        } else {
            try {
                folder = Files.createDirectories(Paths.get(folderPath));
            } catch (Exception e) {
                log.log(Level.SEVERE, String.format("Unable to create SMC-B folder [%s]", folderPath), e);
            }
        }
        if (folder != null) {
            folders.add(new FolderInfo(folder.toFile().getAbsolutePath(), uuid));
        }
    }

    private void unzipAndSaveDataToFile(byte[] dataForWriting, File outputFile) {
        if (!outputFile.exists()) {
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
