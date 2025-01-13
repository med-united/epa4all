package de.servicehealth.epa4all.server.idp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscoveryDocumentFile<T extends Serializable> {

    private static final Logger log = Logger.getLogger(DiscoveryDocumentFile.class.getName());

    private static final String DISCOVERY_DOC_FILE_NAME = "discovery-doc";

    private final ReentrantReadWriteLock lock;
    private final File file;

    public DiscoveryDocumentFile(File configFolder) throws IOException {
        lock = new ReentrantReadWriteLock();
        file = new File(configFolder, DISCOVERY_DOC_FILE_NAME);
        log.info(String.format("Creating '%s' in the folder '%s'", DISCOVERY_DOC_FILE_NAME, configFolder.getAbsolutePath()));
        if (!file.exists()) {
            file.createNewFile();
        }
    }

    @SuppressWarnings("unchecked")
    public T load() {
        lock.readLock().lock();
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            return (T) is.readObject();
        } catch (Exception e) {
            log.log(Level.SEVERE, String.format("Unable to read '%s' file: %s", DISCOVERY_DOC_FILE_NAME, e.getMessage()));
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    public void store(T serializable) {
        lock.writeLock().lock();
        try (ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file))) {
            os.writeObject(serializable);
        } catch (IOException e) {
            log.log(Level.SEVERE, String.format("Unable to store '%s' file: %s", DISCOVERY_DOC_FILE_NAME, e.getMessage()));
        } finally {
            lock.writeLock().unlock();
        }
    }
}
