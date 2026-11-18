package com.civicconnect.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IssueImageStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private final Path uploadDirectory;

    public IssueImageStorageService(@Value("${app.upload.dir:uploads}") String uploadDirectory) {
        this.uploadDirectory = Paths.get(uploadDirectory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile image) throws IOException {
        if (image == null || image.isEmpty()) {
            return null;
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, "Images must be 5 MB or smaller.");
        }

        String extension;
        try (InputStream input = image.getInputStream()) {
            extension = detectImageExtension(input.readNBytes(12));
        }
        if (extension == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Upload a valid JPEG, PNG, GIF, or WebP image.");
        }

        Files.createDirectories(uploadDirectory);
        String filename = UUID.randomUUID() + extension;
        Path target = resolveFilename(filename);
        boolean created = false;
        try (InputStream input = image.getInputStream();
                OutputStream output = Files.newOutputStream(
                        target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            created = true;
            input.transferTo(output);
        } catch (IOException exception) {
            if (created) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }
            throw exception;
        }
        return filename;
    }

    public void delete(String filename) throws IOException {
        Files.deleteIfExists(resolveFilename(filename));
    }

    private Path resolveFilename(String filename) {
        Path target = uploadDirectory.resolve(filename).normalize();
        if (!target.getParent().equals(uploadDirectory)) {
            throw new IllegalArgumentException("Invalid stored image filename.");
        }
        return target;
    }

    private String detectImageExtension(byte[] header) {
        if (header.length >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (header.length >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G'
                && (header[4] & 0xFF) == 0x0D
                && (header[5] & 0xFF) == 0x0A
                && (header[6] & 0xFF) == 0x1A
                && (header[7] & 0xFF) == 0x0A) {
            return ".png";
        }
        if (header.length >= 6
                && header[0] == 'G'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == '8'
                && (header[4] == '7' || header[4] == '9')
                && header[5] == 'a') {
            return ".gif";
        }
        if (header.length >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P') {
            return ".webp";
        }
        return null;
    }
}
