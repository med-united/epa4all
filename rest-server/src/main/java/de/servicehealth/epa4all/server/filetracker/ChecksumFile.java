package de.servicehealth.epa4all.server.filetracker;

import com.google.common.io.Files;
import jakarta.xml.bind.DatatypeConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChecksumFile {

    private static final Logger log = Logger.getLogger(ChecksumFile.class.getName());

    private static final String CHECKSUM_FILE_NAME = "sha256checksums";

    private final ReentrantReadWriteLock lock;
    private final String insurantId;
    private final File file;

    public ChecksumFile(File insurantFolder, String insurantId) throws IOException {
        this.insurantId = insurantId;
        lock = new ReentrantReadWriteLock();
        file = new File(insurantFolder, CHECKSUM_FILE_NAME);
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    private List<String> getChecksumLines() throws IOException {
        return Files.readLines(file, StandardCharsets.ISO_8859_1);
    }

    public Set<String> getChecksums() throws Exception {
        lock.readLock().lock();
        try {
            return new HashSet<>(getChecksumLines().stream().filter(s -> !s.trim().isEmpty()).toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean appendChecksumFor(byte[] bytes) {
        lock.writeLock().lock();
        try {
            String checksum = calculateChecksum(bytes);
            if (getChecksumLines().contains(checksum)) {
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
