package de.servicehealth.epa4all.server.smcb;

import de.gematik.ws.conn.vsds.vsdservice.v5.ReadVSDResponse;
import de.gematik.ws.fa.vsdm.vsd.v5.UCPersoenlicheVersichertendatenXML;
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

import static de.health.service.cetp.utils.Utils.unzipAndSaveDataToFile;
import static de.servicehealth.epa4all.server.insurance.InsuranceXmlUtils.createUCEntity;

public class VsdResponseFile {

    private static final Logger log = Logger.getLogger(WebdavSmcbManager.class.getName());

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

    private final ReentrantReadWriteLock lock;

    private final File readVSDResponseFile;
    private final File pruefungsnachweisFile;
    private final File allgemeineVersicherungsdatenFile;
    private final File persoenlicheVersichertendatenFile;
    private final File geschuetzteVersichertendatenFile;

    public VsdResponseFile(File localMedFolder) {
        if (localMedFolder == null || !localMedFolder.isDirectory()) {
            throw new IllegalArgumentException("Local med folder is corrupted");
        }

        lock = new ReentrantReadWriteLock();

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

    public InsuranceData load(String kvnr) {
        if (readVSDResponseFile.exists()) {
            lock.readLock().lock();
            try {
                byte[] allgemeineVersicherungsdaten = Files.readAllBytes(allgemeineVersicherungsdatenFile.toPath());
                byte[] persoenlicheVersichertendaten = Files.readAllBytes(persoenlicheVersichertendatenFile.toPath());
                byte[] geschuetzteVersichertendaten = Files.readAllBytes(geschuetzteVersichertendatenFile.toPath());
                byte[] pruefungsnachweis = Files.readAllBytes(pruefungsnachweisFile.toPath());

                return new InsuranceData(
                    extractPz(pruefungsnachweis, false),
                    kvnr,
                    createUCEntity(persoenlicheVersichertendaten, false),
                    createUCEntity(geschuetzteVersichertendaten, false),
                    createUCEntity(allgemeineVersicherungsdaten, false)
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

    public static String extractInsurantId(ReadVSDResponse readVSDResponse) throws Exception {
        boolean gzipSource = true;
        byte[] persoenlicheVersichertendaten = readVSDResponse.getPersoenlicheVersichertendaten();
        UCPersoenlicheVersichertendatenXML patient = createUCEntity(persoenlicheVersichertendaten, gzipSource);
        return extractInsurantId(patient, readVSDResponse.getPruefungsnachweis(), gzipSource);
    }

    private static class PruefungsnachweisNodes {
        Node eNode;
        Node pzNode;

        public PruefungsnachweisNodes(Node eNode, Node pzNode) {
            this.eNode = eNode;
            this.pzNode = pzNode;
        }
    }

    private static PruefungsnachweisNodes getPruefungsnachweisNodes(
        byte[] pruefungsnachweis,
        boolean gzipSource
    ) throws Exception {
        Document doc = InsuranceXmlUtils.createDocument(pruefungsnachweis, gzipSource);
        Node eNode = doc.getElementsByTagName("E").item(0);
        Node pzNode = doc.getElementsByTagName("PZ").item(0);
        return new PruefungsnachweisNodes(eNode, pzNode);
    }

    public static String extractPz(
        byte[] pruefungsnachweis,
        boolean gzipSource
    ) throws Exception {
        PruefungsnachweisNodes nodes = getPruefungsnachweisNodes(pruefungsnachweis, gzipSource);
        return nodes.eNode.getTextContent().equals("3") && nodes.pzNode == null
            ? "undefined"
            : nodes.pzNode.getTextContent();
    }

    private static String extractInsurantId(
        UCPersoenlicheVersichertendatenXML patient,
        byte[] pruefungsnachweis,
        boolean gzipSource
    ) throws Exception {
        PruefungsnachweisNodes nodes = getPruefungsnachweisNodes(pruefungsnachweis, gzipSource);
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
