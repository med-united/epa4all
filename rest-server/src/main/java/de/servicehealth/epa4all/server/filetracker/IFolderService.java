package de.servicehealth.epa4all.server.filetracker;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.io.File.separator;

public interface IFolderService {

    String LOCAL_FOLDER = "local";

    File getRootFolder();

    Supplier<File> getTelematikFolderSupplier(String telematikId);

    default Supplier<File> getInsurantFolderSupplier(String telematikId, String insurantId) {
        String path = String.join(separator, getTelematikFolderSupplier(telematikId).get().getAbsolutePath(), insurantId);
        return () -> getOrCreateFolder(path);
    }

    default Supplier<File> getFolderSupplier(String telematikId, String insurantId, String medFolder) {
        String path = String.join(separator, getInsurantFolderSupplier(telematikId, insurantId).get().getAbsolutePath(), medFolder);
        return () -> getOrCreateFolder(path);
    }

    default File getOrCreateFolder(String path) {
        File folder = new File(path);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            if (!created) {
                String msg = String.format("Directory [%s] was not created", path);
                throw new IllegalStateException(msg);
            }
        }
        return folder;
    }

    default File[] getTelematikFolders() {
        return getNestedFolders(getRootFolder());
    }

    default File[] getNestedFiles(File parent) {
        File[] files = parent.listFiles(File::isFile);
        return files == null ? new File[0] : files;
    }

    default File[] getNestedFolders(File parent) {
        File[] folders = parent.listFiles(File::isDirectory);
        return folders == null ? new File[0] : folders;
    }

    default File getTelematikFolder(String telematikId) {
        return Arrays.stream(getTelematikFolders())
            .filter(f -> f.getName().equals(telematikId))
            .findFirst()
            .orElse(getTelematikFolderSupplier(telematikId).get());
    }

    default File getInsurantFolder(String telematikId, String insurantId) {
        return Arrays.stream(getNestedFolders(getTelematikFolder(telematikId)))
            .filter(f -> f.getName().equals(insurantId))
            .findFirst()
            .orElse(getInsurantFolderSupplier(telematikId, insurantId).get());
    }

    default File[] getInsurantMedFolders(String telematikId, String insurantId) {
        return getNestedFolders(getInsurantFolder(telematikId, insurantId));
    }

    default File getMedFolder(String telematikId, String insurantId, String medFolder) {
        return Arrays.stream(getInsurantMedFolders(telematikId, insurantId))
            .filter(f -> f.getName().equals(medFolder))
            .findFirst()
            .orElse(getFolderSupplier(telematikId, insurantId, medFolder).get());
    }

    default File[] getMedFiles(String telematikId, String insurantId, String medFolder) {
        return getNestedFiles(getMedFolder(telematikId, insurantId, medFolder));
    }

    default List<File> getAllMedFiles(String telematikId, String insurantId, Set<String> extSet) {
        return Arrays.stream(getInsurantMedFolders(telematikId, insurantId))
            .filter(f -> !f.getName().equals(LOCAL_FOLDER))
            .flatMap(f -> {
                File[] nestedFiles = getNestedFiles(f);
                return nestedFiles == null ? Stream.empty() : Stream.of(nestedFiles);
            })
            .filter(f -> extSet == null || extSet.isEmpty() || extSet.stream().anyMatch(f.getName().toLowerCase()::endsWith))
            .toList();
    }
}
