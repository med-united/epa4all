package de.servicehealth.epa4all.server.filetracker;

import jakarta.xml.bind.DatatypeConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static com.google.common.io.Files.readLines;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class ChecksumFile {

    private static final Logger log = LoggerFactory.getLogger(ChecksumFile.class.getName());

    public static final String CHECKSUM_FILE_NAME = "sha256checksums";

    private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final File file;

    public ChecksumFile(File insurantFolder) throws IOException {
        file = new File(insurantFolder, CHECKSUM_FILE_NAME);
        if (!file.exists()) {
            log.info(String.format("Creating 'sha256checksums' in the folder '%s'", insurantFolder.getAbsolutePath()));
            file.createNewFile();
        }
    }

    public Set<String> getChecksums() throws Exception {
        lock.readLock().lock();
        try {
            return new HashSet<>(readLines(file, ISO_8859_1));
        } finally {
            lock.readLock().unlock();
        }
    }

    private boolean checksumAction(byte[] bytes, Function<String, Boolean> func) {
        lock.writeLock().lock();
        try {
            if (bytes == null || bytes.length == 0) {
                log.warn("[ChecksumFile] attempt to add checksum for empty bytes array");
                return false;
            }
            String checksum = calculateChecksum(bytes);
            return func.apply(checksum);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeChecksum(byte[] bytes, String insurantId) {
        checksumAction(bytes, (checksum) -> {
            try {
                HashSet<String> checksums = new HashSet<>(readLines(file, ISO_8859_1));
                if (checksums.contains(checksum)) {
                    checksums.remove(checksum);
                    try (FileOutputStream os = new FileOutputStream(file)) {
                        String newLine = checksums.isEmpty() ? "" : String.join("\n", checksums);
                        os.write(newLine.getBytes());
                    }
                }
                return true;
            } catch (Exception e) {
                log.error("[%s] Unable to append 'sha256checksums' file".formatted(insurantId), e);
            }
            return false;
        });
    }

    public boolean appendChecksum(byte[] bytes, String insurantId) {
        return checksumAction(bytes, (checksum) -> {
            try {
                HashSet<String> checksums = new HashSet<>(readLines(file, ISO_8859_1));
                if (checksums.contains(checksum)) {
                    return false;
                }
                try (FileOutputStream os = new FileOutputStream(file, true)) {
                    String newLine = checksums.isEmpty() ? checksum : "\n" + checksum;
                    os.write(newLine.getBytes());
                }
                return true;
            } catch (Exception e) {
                log.error("[%s] Unable to append 'sha256checksums' file".formatted(insurantId), e);
            }
            return false;
        });
    }

    public String calculateChecksum(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            return DatatypeConverter.printHexBinary(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
