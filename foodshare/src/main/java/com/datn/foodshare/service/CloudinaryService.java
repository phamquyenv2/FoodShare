package com.datn.foodshare.service;

import com.cloudinary.Cloudinary;
import com.datn.foodshare.util.error.StorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class CloudinaryService {

    private final Cloudinary cloudinary;
    private final int maxFileSizeMb;
    private final long maxFileSizeBytes;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    public CloudinaryService(Cloudinary cloudinary) {
        this(cloudinary, 10);
    }

    @Autowired
    public CloudinaryService(
            Cloudinary cloudinary,
            @Value("${app.upload.max-document-size-mb:10}") int maxFileSizeMb) {
        this.cloudinary = cloudinary;
        this.maxFileSizeMb = maxFileSizeMb;
        this.maxFileSizeBytes = (long) maxFileSizeMb * 1024 * 1024;
    }

    public String uploadFoodPostImage(MultipartFile file) throws StorageException {
        return uploadImage(file, "food-posts");
    }

    public String uploadBusinessDocument(MultipartFile file) throws StorageException {
        return uploadImage(file, "business-documents");
    }

    public String uploadUserAvatar(byte[] bytes, String contentType) throws StorageException {
        if (bytes == null || bytes.length == 0) throw new StorageException("Ảnh đại diện không được để trống");
        if (bytes.length > maxFileSizeBytes) throw new StorageException("Ảnh đại diện vượt quá giới hạn kích thước");
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType) || !hasExpectedSignature(bytes, contentType)) {
            throw new StorageException("Ảnh đại diện không đúng định dạng");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(bytes, Map.of(
                    "public_id", "avatars/" + UUID.randomUUID(), "overwrite", false));
            String url = (String) result.get("secure_url");
            if (url == null || url.isBlank()) throw new StorageException("Cloudinary không trả về URL hợp lệ");
            return url;
        } catch (StorageException e) { throw e;
        } catch (Exception e) {
            log.warn("Không thể upload avatar lên Cloudinary", e);
            throw new StorageException("Upload ảnh đại diện thất bại");
        }
    }

    private String uploadImage(MultipartFile file, String folder) throws StorageException {
        validateFile(file);
        try {
            String publicId = folder + "/" + UUID.randomUUID();
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
            throw new StorageException("Upload ảnh thất bại");
        } catch (Exception e) {
            log.error("Lỗi không xác định khi tải ảnh lên Cloudinary", e);
            throw new StorageException("Upload ảnh thất bại");
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
        if (file.getSize() > maxFileSizeBytes) {
            throw new StorageException("File ảnh không được vượt quá " + maxFileSizeMb + "MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new StorageException("Chỉ chấp nhận file ảnh định dạng JPEG, PNG, WebP hoặc GIF");
        }
        if (!hasExpectedSignature(file, contentType)) {
            throw new StorageException("Nội dung file không khớp với định dạng ảnh đã khai báo");
        }
    }

    private boolean hasExpectedSignature(MultipartFile file, String contentType) throws StorageException {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            return switch (contentType) {
                case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
                case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
                case "image/gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII))
                        || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
                case "image/webp" -> header.length >= 12
                        && Arrays.equals(Arrays.copyOfRange(header, 0, 4), "RIFF".getBytes(StandardCharsets.US_ASCII))
                        && Arrays.equals(Arrays.copyOfRange(header, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
                default -> false;
            };
        } catch (IOException exception) {
            throw new StorageException("Không thể đọc nội dung file ảnh");
        }
    }

    private boolean hasExpectedSignature(byte[] header, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
            case "image/png" -> startsWith(header, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            case "image/gif" -> startsWith(header, "GIF87a".getBytes(StandardCharsets.US_ASCII)) || startsWith(header, "GIF89a".getBytes(StandardCharsets.US_ASCII));
            case "image/webp" -> header.length >= 12 && Arrays.equals(Arrays.copyOfRange(header, 0, 4), "RIFF".getBytes(StandardCharsets.US_ASCII)) && Arrays.equals(Arrays.copyOfRange(header, 8, 12), "WEBP".getBytes(StandardCharsets.US_ASCII));
            default -> false;
        };
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        return value.length >= prefix.length
                && Arrays.equals(Arrays.copyOf(value, prefix.length), prefix);
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
