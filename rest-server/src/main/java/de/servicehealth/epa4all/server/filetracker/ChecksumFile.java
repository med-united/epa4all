package de.servicehealth.epa4all.server.filetracker;

import jakarta.xml.bind.DatatypeConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.io.Files.readLines;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class ChecksumFile {

    private static final Logger log = Logger.getLogger(ChecksumFile.class.getName());

    public static final String CHECKSUM_FILE_NAME = "sha256checksums";

    private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final String insurantId;
    private final File file;

    public ChecksumFile(File insurantFolder, String insurantId) throws IOException {
        this.insurantId = insurantId;
        file = new File(insurantFolder, CHECKSUM_FILE_NAME);
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    public Set<String> getChecksums() throws Exception {
        lock.readLock().lock();
        try {
            return new HashSet<>(readLines(file, ISO_8859_1).stream().filter(s -> !s.trim().isEmpty()).toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean appendChecksumFor(byte[] bytes) {
        lock.writeLock().lock();
        try {
            String checksum = calculateChecksum(bytes);
            if (new HashSet<>(readLines(file, ISO_8859_1)).contains(checksum)) {
                return false;
            }
            try (FileOutputStream os = new FileOutputStream(file, true)) {
                String newLine = checksum + "\n";
                os.write(newLine.getBytes());
            }
            return true;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unable to append 'sha256checksums' file for " + insurantId, e);
        } finally {
            lock.writeLock().unlock();
        }
        return false;
    }

    String calculateChecksum(byte[] bytes) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(bytes);
        return DatatypeConverter.printHexBinary(digest.digest());
    }
}
