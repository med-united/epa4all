package de.servicehealth.epa4all.server.vsd;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.entitlement.AuditEvidenceException;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils;
import jakarta.xml.bind.DatatypeConverter;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static de.servicehealth.epa4all.server.entitlement.EntitlementService.AUDIT_EVIDENCE_NO_DEFINED;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCEntity;
import static de.servicehealth.utils.ServerUtils.unzipAndSaveDataToFile;

public class VsdResponseFile {

    private static final Logger log = Logger.getLogger(VsdResponseFile.class.getName());

    public static final String UNDEFINED_PZ = "undefined";

    public static final String ALLGEMEINE_VERSICHERUNGSDATEN_XML = "AllgemeineVersicherungsdaten.xml";
    public static final String PERSOENLICHE_VERSICHERTENDATEN_XML = "PersoenlicheVersichertendaten.xml";
    public static final String GESCHUETZTE_VERSICHERTENDATEN_XML = "GeschuetzteVersichertendaten.xml";
    public static final String PRUEFUNGSNACHWEIS_XML = "Pruefungsnachweis.xml";
    public static final String READ_VSD_RESPONSE_XML = "ReadVSDResponse.xml";

    static JAXBContext readVSDJaxbContext;

    static {
        try {
            readVSDJaxbContext = JAXBContext.newInstance(ReadVSDResponse.class);
        } catch (JAXBException e) {
            log.log(Level.SEVERE, "Could not create JAXBContext", e);
        }
    }

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final File readVSDResponseFile;
    private final File pruefungsnachweisFile;
    private final File allgemeineVersicherungsdatenFile;
    private final File persoenlicheVersichertendatenFile;
    private final File geschuetzteVersichertendatenFile;

    public VsdResponseFile(File localMedFolder) {
        if (localMedFolder == null || !localMedFolder.isDirectory()) {
            throw new IllegalArgumentException("Local med folder is corrupted");
        }

        readVSDResponseFile = new File(localMedFolder, READ_VSD_RESPONSE_XML);
        pruefungsnachweisFile = new File(localMedFolder, PRUEFUNGSNACHWEIS_XML);
        allgemeineVersicherungsdatenFile = new File(localMedFolder, ALLGEMEINE_VERSICHERUNGSDATEN_XML);
        persoenlicheVersichertendatenFile = new File(localMedFolder, PERSOENLICHE_VERSICHERTENDATEN_XML);
        geschuetzteVersichertendatenFile = new File(localMedFolder, GESCHUETZTE_VERSICHERTENDATEN_XML);

        initFiles(
            List.of(
                readVSDResponseFile,
                pruefungsnachweisFile,
                allgemeineVersicherungsdatenFile,
                persoenlicheVersichertendatenFile,
                geschuetzteVersichertendatenFile
            )
        );
    }

    private void initFiles(List<File> files) {
        files.forEach(f -> {
            if (!f.exists()) {
                try {
                    f.createNewFile();
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Can't create a file: " + f.getAbsolutePath());
                }
            }
        });
    }

    public static String extractPz(byte[] pruefungsnachweis) throws Exception {
        PruefungsnachweisNodes nodes = getPruefungsnachweisNodes(pruefungsnachweis);
        return nodes.eNode.getTextContent().equals("3") && nodes.pzNode == null
            ? UNDEFINED_PZ
            : nodes.pzNode.getTextContent();
    }

    public InsuranceData load(String telematikId, String kvnr) {
        if (readVSDResponseFile.exists()) {
            lock.readLock().lock();
            try {
                byte[] allgemeineVersicherungsdaten = Files.readAllBytes(allgemeineVersicherungsdatenFile.toPath());
                byte[] persoenlicheVersichertendaten = Files.readAllBytes(persoenlicheVersichertendatenFile.toPath());
                byte[] geschuetzteVersichertendaten = Files.readAllBytes(geschuetzteVersichertendatenFile.toPath());
                byte[] pruefungsnachweis = Files.readAllBytes(pruefungsnachweisFile.toPath());

                if (persoenlicheVersichertendaten.length == 0 && pruefungsnachweis.length == 0) {
                    return null;
                }

                return new InsuranceData(
                    extractPz(pruefungsnachweis),
                    kvnr,
                    telematikId,
                    createUCEntity(persoenlicheVersichertendaten),
                    createUCEntity(geschuetzteVersichertendaten),
                    createUCEntity(allgemeineVersicherungsdaten)
                );
            } catch (Exception e) {
                String msg = String.format("Error while reading insurance data for KVNR=%s", kvnr);
                log.log(Level.SEVERE, msg, e);
                return null;
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return null;
        }
    }

    public static String extractInsurantId(ReadVSDResponse readVSDResponse, boolean forcePz) throws Exception {
        byte[] persoenlicheVersichertendaten = readVSDResponse.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML patient = createUCEntity(persoenlicheVersichertendaten);
        return extractInsurantId(patient, readVSDResponse.getPruefungsnachweis(), forcePz);
    }

    private static class PruefungsnachweisNodes {
        Node eNode;
        Node pzNode;

        public PruefungsnachweisNodes(Node eNode, Node pzNode) {
            this.eNode = eNode;
            this.pzNode = pzNode;
        }
    }

    private static PruefungsnachweisNodes getPruefungsnachweisNodes(byte[] pruefungsnachweis) throws Exception {
        Document doc = InsuranceXmlUtils.createDocument(pruefungsnachweis);
        Node eNode = doc.getElementsByTagName("E").item(0);
        Node pzNode = doc.getElementsByTagName("PZ").item(0);
        return new PruefungsnachweisNodes(eNode, pzNode);
    }

    private static String extractInsurantId(
        UCPersoenlicheVersichertendatenXML patient,
        byte[] pruefungsnachweis,
        boolean forcePz
    ) throws Exception {
        PruefungsnachweisNodes nodes = getPruefungsnachweisNodes(pruefungsnachweis);
        if (forcePz && nodes.pzNode == null) {
            throw new AuditEvidenceException("[Pruefungsnachweis] " + AUDIT_EVIDENCE_NO_DEFINED);
        }
        if (nodes.eNode.getTextContent().equals("3") && nodes.pzNode == null) {
            return patient.getVersicherter().getVersichertenID();
        } else {
            byte[] base64Binary = DatatypeConverter.parseBase64Binary(nodes.pzNode.getTextContent());
            String base64PN = new String(base64Binary);
            return base64PN.substring(0, 10);
        }
    }

    public void store(ReadVSDResponse readVSDResponse) throws Exception {
        lock.writeLock().lock();
        try {
            readVSDJaxbContext.createMarshaller().marshal(readVSDResponse, new FileOutputStream(readVSDResponseFile));

            unzipAndSaveDataToFile(readVSDResponse.getAllgemeineVersicherungsdaten(), allgemeineVersicherungsdatenFile);
            unzipAndSaveDataToFile(readVSDResponse.getPersoenlicheVersichertendaten(), persoenlicheVersichertendatenFile);
            unzipAndSaveDataToFile(readVSDResponse.getGeschuetzteVersichertendaten(), geschuetzteVersichertendatenFile);
            unzipAndSaveDataToFile(readVSDResponse.getPruefungsnachweis(), pruefungsnachweisFile);
        } catch (Exception e) {
            log.log(Level.WARNING, "Could not save ReadVSDResponse", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cleanUp() {
        List<File> files = List.of(
            readVSDResponseFile,
            pruefungsnachweisFile,
            allgemeineVersicherungsdatenFile,
            persoenlicheVersichertendatenFile,
            geschuetzteVersichertendatenFile
        );
        lock.writeLock().lock();
        try {
            files.forEach(f -> {
                if (f.exists()) {
                    f.delete();
                }
            });
        } finally {
            lock.writeLock().unlock();
        }
    }
}
