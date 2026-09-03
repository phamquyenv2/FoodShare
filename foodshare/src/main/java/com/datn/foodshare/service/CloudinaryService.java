package com.datn.foodshare.service;

import com.cloudinary.Cloudinary;
import com.datn.foodshare.util.error.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryService {

    private final Cloudinary cloudinary;

    private static final long MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    public String uploadFoodPostImage(MultipartFile file) throws StorageException {
        validateFile(file);
        try {
            String publicId = "food-posts/" + UUID.randomUUID();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(file.getBytes(), Map.of(
                    "public_id", publicId,
                    "overwrite", false
            ));
            String url = (String) result.get("secure_url");
            if (url == null || url.isBlank()) {
                throw new StorageException("Cloudinary không trả về URL hợp lệ");
            }
            return url;
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            log.error("Tải ảnh lên Cloudinary thất bại", e);
            throw new StorageException("Upload ảnh thất bại: " + e.getMessage());
        } catch (Exception e) {
            log.error("Lỗi không xác định khi tải ảnh lên Cloudinary", e);
            throw new StorageException("Upload ảnh thất bại: " + e.getMessage());
        }
    }

    public void deleteFoodPostImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return;
        try {
            String publicId = extractPublicId(imageUrl);
            if (publicId != null) {
                cloudinary.uploader().destroy(publicId, Map.of());
            }
        } catch (Exception e) {
            log.warn("Xóa ảnh trên Cloudinary thất bại cho url={}: {}", imageUrl, e.getMessage());
        }
    }

    private void validateFile(MultipartFile file) throws StorageException {
        if (file == null || file.isEmpty()) {
            throw new StorageException("File ảnh không được để trống");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new StorageException("File ảnh không được vượt quá 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new StorageException("Chỉ chấp nhận file ảnh định dạng JPEG, PNG, WebP hoặc GIF");
        }
    }

    private String extractPublicId(String url) {
        try {
            int uploadIdx = url.indexOf("/upload/");
            if (uploadIdx < 0) return null;
            String afterUpload = url.substring(uploadIdx + "/upload/".length());
            if (afterUpload.startsWith("v") && afterUpload.length() > 1) {
                int slashIdx = afterUpload.indexOf('/');
                if (slashIdx > 0) {
                    afterUpload = afterUpload.substring(slashIdx + 1);
                }
            }
            int dotIdx = afterUpload.lastIndexOf('.');
            return dotIdx > 0 ? afterUpload.substring(0, dotIdx) : afterUpload;
        } catch (Exception e) {
            log.warn("Không thể trích xuất publicId từ url={}", url);
            return null;
        }
    }
}
