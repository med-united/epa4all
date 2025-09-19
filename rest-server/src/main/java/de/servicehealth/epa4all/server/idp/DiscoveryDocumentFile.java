package de.servicehealth.epa4all.server.idp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DiscoveryDocumentFile<T extends Serializable> {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryDocumentFile.class.getName());

    private final ReentrantReadWriteLock lock;
    private final String fileName;
    private final File file;

    public DiscoveryDocumentFile(File configFolder, String fileName) throws IOException {
        this.fileName = fileName;
        lock = new ReentrantReadWriteLock();
        if (configFolder == null) {
            log.warn("Config folder is null, using current directory.");
            configFolder = new File(".");
        }
        file = new File(configFolder, fileName);
        if (!file.exists()) {
            log.info(String.format("Creating '%s' in the folder '%s'", fileName, configFolder.getAbsolutePath()));
            file.createNewFile();
        }
    }

    @SuppressWarnings("unchecked")
    public T load() {
        lock.readLock().lock();
        try (ObjectInputStream is = new ObjectInputStream(new FileInputStream(file))) {
            return (T) is.readObject();
        } catch (Exception e) {
            log.error(String.format("Unable to read '%s' file: %s", fileName, e.getMessage()));
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
            log.error(String.format("Unable to store '%s' file: %s", fileName, e.getMessage()));
        } finally {
            lock.writeLock().unlock();
        }
    }
}
