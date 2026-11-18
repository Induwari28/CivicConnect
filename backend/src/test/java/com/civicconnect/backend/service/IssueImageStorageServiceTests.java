package com.civicconnect.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

class IssueImageStorageServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void storesImagesWithGeneratedNamesAndDeletesThem() throws IOException {
        IssueImageStorageService storageService = new IssueImageStorageService(temporaryDirectory.toString());
        byte[] pngHeader = {
                (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x00
        };
        MockMultipartFile image = new MockMultipartFile("image", "street.png", "image/png", pngHeader);

        String filename = storageService.store(image);
        Path storedImage = temporaryDirectory.resolve(filename);

        assertTrue(filename.endsWith(".png"));
        assertArrayEquals(pngHeader, Files.readAllBytes(storedImage));
        storageService.delete(filename);
        assertTrue(Files.notExists(storedImage));
    }

    @Test
    void rejectsFilesThatDoNotHaveAnAllowedImageSignature() {
        IssueImageStorageService storageService = new IssueImageStorageService(temporaryDirectory.toString());
        MockMultipartFile notAnImage = new MockMultipartFile(
                "image", "report.txt", "text/plain", "not an image".getBytes());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storageService.store(notAnImage));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsImagesLargerThanFiveMegabytes() {
        IssueImageStorageService storageService = new IssueImageStorageService(temporaryDirectory.toString());
        byte[] oversizedImage = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile image = new MockMultipartFile("image", "large.png", "image/png", oversizedImage);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storageService.store(image));

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, exception.getStatusCode());
    }
}
