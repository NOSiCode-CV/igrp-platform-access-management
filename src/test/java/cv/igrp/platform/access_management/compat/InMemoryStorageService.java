package cv.igrp.platform.access_management.compat;

import cv.igrp.platform.filemanager.StorageService;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Object storage for the compat suite, held in memory.
 *
 * <p>The suite runs in CI, which cannot pull a MinIO image, so storage is
 * replaced rather than containerised. Audit retention only needs storage to
 * accept a file and give the same bytes back.
 */
public class InMemoryStorageService implements StorageService {

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final Set<String> containers = ConcurrentHashMap.newKeySet();

    @Override
    public void createContainer(String name) {
        containers.add(name);
    }

    @Override
    public void createPublicContainer(String name) {
        containers.add(name);
    }

    @Override
    public void deleteContainer(String name) {
        containers.remove(name);
    }

    @Override
    public boolean containerExists(String name) {
        return containers.contains(name);
    }

    @Override
    public void uploadFile(byte[] content, String path, String contentType) {
        files.put(path, content.clone());
    }

    @Override
    public void uploadPublicFile(byte[] content, String path, String contentType) {
        files.put(path, content.clone());
    }

    @Override
    public InputStream downloadFile(String path) throws FileNotFoundException {
        byte[] content = files.get(path);
        if (content == null) {
            throw new FileNotFoundException(path);
        }
        return new ByteArrayInputStream(content);
    }

    @Override
    public void deleteFile(String path) {
        files.remove(path);
    }

    @Override
    public String getFileUrl(String path) {
        return "memory://" + path;
    }

    @Override
    public boolean fileExists(String path) {
        return files.containsKey(path);
    }
}
