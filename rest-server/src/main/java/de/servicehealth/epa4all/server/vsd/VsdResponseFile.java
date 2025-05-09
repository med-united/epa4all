package de.servicehealth.epa4all.server.vsd;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
import de.servicehealth.epa4all.server.insurance.InsuranceData;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static de.servicehealth.epa4all.server.insurance.InsuranceUtils.createUCEntity;
import static de.servicehealth.utils.ServerUtils.writeBytesToFile;
import static de.servicehealth.utils.XmlUtils.createDocument;

public class VsdResponseFile {

    private static final Logger log = LoggerFactory.getLogger(VsdResponseFile.class.getName());

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
            log.error("Could not create JAXBContext", e);
        }
    }

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final File readVSDResponseFile;
    private final File pruefungsnachweisFile;
    private final File allgemeineVersicherungsdatenFile;
    private final File persoenlicheVersichertendatenFile;
    private final File geschuetzteVersichertendatenFile;

    private final List<File> internalFiles;

    public VsdResponseFile(File localMedFolder) {
        if (localMedFolder == null || !localMedFolder.isDirectory()) {
            throw new IllegalArgumentException("Local med folder is corrupted");
        }

        readVSDResponseFile = new File(localMedFolder, READ_VSD_RESPONSE_XML);
        pruefungsnachweisFile = new File(localMedFolder, PRUEFUNGSNACHWEIS_XML);
        allgemeineVersicherungsdatenFile = new File(localMedFolder, ALLGEMEINE_VERSICHERUNGSDATEN_XML);
        persoenlicheVersichertendatenFile = new File(localMedFolder, PERSOENLICHE_VERSICHERTENDATEN_XML);
        geschuetzteVersichertendatenFile = new File(localMedFolder, GESCHUETZTE_VERSICHERTENDATEN_XML);

        internalFiles = List.of(
            readVSDResponseFile,
            pruefungsnachweisFile,
            allgemeineVersicherungsdatenFile,
            persoenlicheVersichertendatenFile,
            geschuetzteVersichertendatenFile
        );
    }

    public List<File> getFiles() {
        return readVSDResponseFile.exists() ? internalFiles : List.of();
    }

    public static String extractPz(byte[] pruefungsnachweis) throws Exception {
        PruefungsnachweisNodes nodes = getPruefungsnachweisNodes(pruefungsnachweis);
        return nodes.eNode.getTextContent().equals("3") && nodes.pzNode == null
            ? null
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

                if (pruefungsnachweis.length == 0) {
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
                log.error(msg, e);
                return null;
            } finally {
                lock.readLock().unlock();
            }
        } else {
            return null;
        }
    }

    public static String extractInsurantId(ReadVSDResponse readVSDResponse, String fallbackKvnr) {
        try {
            byte[] persoenlicheVersichertendaten = readVSDResponse.getPersoenlicheVersichertendaten();
            UCPersoenlicheVersichertendatenXML patient = createUCEntity(persoenlicheVersichertendaten);
            if (patient == null) {
                return fallbackKvnr;
            }
            String versichertenID = patient.getVersicherter().getVersichertenID();
            if (versichertenID == null || versichertenID.trim().isEmpty()) {
                return fallbackKvnr;
            } else {
                return versichertenID;
            }
        } catch (Exception e) {
            log.error("Error while VsdResponseFile.extractInsurantId", e);
            return fallbackKvnr;
        }
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
        Document doc = createDocument(pruefungsnachweis);
        Node eNode = doc.getElementsByTagName("E").item(0);
        Node pzNode = doc.getElementsByTagName("PZ").item(0);
        return new PruefungsnachweisNodes(eNode, pzNode);
    }

    public void store(ReadVSDResponse readVSDResponse) {
        lock.writeLock().lock();
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            readVSDJaxbContext.createMarshaller().marshal(readVSDResponse, os);

            writeBytesToFile(os.toByteArray(), readVSDResponseFile);
            writeBytesToFile(readVSDResponse.getAllgemeineVersicherungsdaten(), allgemeineVersicherungsdatenFile);
            writeBytesToFile(readVSDResponse.getPersoenlicheVersichertendaten(), persoenlicheVersichertendatenFile);
            writeBytesToFile(readVSDResponse.getGeschuetzteVersichertendaten(), geschuetzteVersichertendatenFile);
            writeBytesToFile(readVSDResponse.getPruefungsnachweis(), pruefungsnachweisFile);
        } catch (Exception e) {
            log.warn("Could not save ReadVSDResponse", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void cleanUp() {
        lock.writeLock().lock();
        try {
            internalFiles.forEach(f -> {
                if (f.exists()) {
                    f.delete();
                }
            });
        } finally {
            lock.writeLock().unlock();
        }
    }
}