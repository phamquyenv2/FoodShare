package com.datn.foodshare.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.datn.foodshare.util.error.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        cloudinaryService = new CloudinaryService(cloudinary);
    }

    // ── Upload Tests ──

    @Test
    void upload_success() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/test/image.jpg"));

        MockMultipartFile file = jpegFile(1024);

        String url = cloudinaryService.uploadFoodPostImage(file);

        assertEquals("https://res.cloudinary.com/test/image.jpg", url);
        verify(uploader).upload(any(byte[].class), any(Map.class));
    }

    @Test
    void upload_acceptsAllAllowedFormats() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/test/img.png"));

        for (String contentType : new String[]{"image/jpeg", "image/png", "image/webp", "image/gif"}) {
            MockMultipartFile file = new MockMultipartFile("file", "food.img", contentType, new byte[1024]);
            assertDoesNotThrow(() -> cloudinaryService.uploadFoodPostImage(file),
                    "Should accept " + contentType);
        }
    }

    @Test
    void upload_rejectsNullFile() {
        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(null));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "food.jpg", "image/jpeg", new byte[0]);

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(file));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void upload_rejectsOversizedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "food.jpg", "image/jpeg", new byte[11 * 1024 * 1024]);

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(file));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void upload_rejectsInvalidContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "food.pdf", "application/pdf", new byte[1024]);

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(file));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void upload_rejectsNullContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "food.jpg", null, new byte[1024]);

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(file));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void upload_handlesCloudinaryIOException() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new IOException("Connection refused"));

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(jpegFile(1024)));
    }

    @Test
    void upload_handlesCloudinaryRuntimeException() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenThrow(new RuntimeException("Unexpected"));

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(jpegFile(1024)));
    }

    @Test
    void upload_rejectsNullUrlInResponse() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(byte[].class), any(Map.class)))
                .thenReturn(Map.of());

        assertThrows(StorageException.class, () -> cloudinaryService.uploadFoodPostImage(jpegFile(1024)));
    }

    // ── Delete Tests ──

    @Test
    void delete_success() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), any(Map.class))).thenReturn(Map.of("result", "ok"));

        assertDoesNotThrow(() -> cloudinaryService.deleteFoodPostImage(
                "https://res.cloudinary.com/demo/image/upload/v123/food-posts/abc.jpg"));

        verify(uploader).destroy(eq("food-posts/abc"), any(Map.class));
    }

    @Test
    void delete_ignoresNullUrl() {
        assertDoesNotThrow(() -> cloudinaryService.deleteFoodPostImage(null));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void delete_ignoresBlankUrl() {
        assertDoesNotThrow(() -> cloudinaryService.deleteFoodPostImage("   "));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void delete_handlesFailureGracefully() throws Exception {
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.destroy(anyString(), any(Map.class))).thenThrow(new IOException("error"));

        assertDoesNotThrow(() -> cloudinaryService.deleteFoodPostImage(
                "https://res.cloudinary.com/demo/image/upload/v123/food-posts/abc.jpg"));
    }

    // ── Helpers ──

    private MockMultipartFile jpegFile(int sizeBytes) {
        return new MockMultipartFile("file", "food.jpg", "image/jpeg", new byte[sizeBytes]);
    }
}
