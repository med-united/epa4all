package de.servicehealth.epa4all.server.idp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DiscoveryDocFile<T extends Serializable> {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryDocFile.class.getName());

    private static class Empty implements Serializable {
        @Serial
        private static final long serialVersionUID = 5487165324856467227L;
    }

    private static final Empty empty = new Empty();

    private final ReentrantReadWriteLock lock;
    private final String fileName;
    private final File file;

    public DiscoveryDocFile(File configFolder, String fileName) throws IOException {
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
            Object object = is.readObject();
            return object instanceof Empty ? null : (T) object;
        } catch (Exception e) {
            log.error(String.format("Unable to read '%s' file: %s", fileName, e.getMessage()));
        } finally {
            lock.readLock().unlock();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public DiscoveryDocFile<T> erase() {
        lock.writeLock().lock();
        try {
            store((T) empty);
        } finally {
            lock.writeLock().unlock();
        }
        return this;
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
