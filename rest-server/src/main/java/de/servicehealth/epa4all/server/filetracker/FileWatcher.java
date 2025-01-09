package de.servicehealth.epa4all.server.filetracker;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Taken from <a href="https://gist.github.com/danielflower/f54c2fe42d32356301c68860a4ab21ed">...</a>
 */
public class FileWatcher {

    private final static Logger log = Logger.getLogger(FileWatcher.class.getName());

    private Thread thread;
    private WatchService watchService;
    private final Map<WatchKey, Path> watchKeyToDirectory = new HashMap<>();

    public interface Callback {
        void run() throws Exception;
    }

    /**
     * Starts watching a file and the given path and calls the callback when it is changed.
     * A shutdown hook is registered to stop watching. To control this yourself, create an
     * instance and use the start/stop methods.
     */
    public static void onFileChange(List<Path> files, Callback callback) throws IOException {
        FileWatcher fileWatcher = new FileWatcher();
        fileWatcher.start(files, callback);
        Runtime.getRuntime().addShutdownHook(new Thread(fileWatcher::stop));
    }

    public void start(List<Path> files, Callback callback) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        List<Path> fileParentDirs = files.stream().map(Path::getParent).distinct().toList();
        for (Path parent : fileParentDirs) {
            WatchKey wk = parent.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            watchKeyToDirectory.put(wk, parent);
        }
        for (Path file : files) {
            log.info("Going to watch " + file);
        }

        thread = new Thread(() -> {
            while (true) {
                WatchKey wk = null;
                try {
                    wk = watchService.take();
                    Thread.sleep(500); // give a chance for duplicate events to pile up

                    Path keyParent = watchKeyToDirectory.get(wk);
                    if (keyParent == null) {
                        log.log(Level.SEVERE, "WatchKey not recognized");
                        continue;
                    }

                    for (WatchEvent<?> event : wk.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            log.log(Level.SEVERE, "FileSystem watcher watched an OVERFLOW event");
                            continue;
                        }

                        @SuppressWarnings("cast") // We only observe Path...
                        WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                        Path changed = keyParent.resolve(pathEvent.context());

                        if (!Files.exists(changed)) {
                            continue;
                        }

                        for (Path watchFile : files) {
                            if (Files.isSameFile(watchFile, changed)) {
                                log.info("File change event: " + changed);
                                callback.run();
                                break;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    log.info("Ending my watch");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Error while reloading file", e);
                } finally {
                    if (wk != null) {
                        wk.reset();
                    }
                }
            }
        });
        thread.start();
    }

    public void stop() {
        thread.interrupt();
        try {
            watchService.close();
        } catch (IOException e) {
            log.log(Level.INFO, "Error closing watch service", e);
        }
    }
}