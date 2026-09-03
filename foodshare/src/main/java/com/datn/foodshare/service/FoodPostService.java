package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.FoodPostImage;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateFoodPostRequest;
import com.datn.foodshare.domain.request.FoodPostFilterRequest;
import com.datn.foodshare.domain.request.UpdateFoodPostRequest;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.CategoryRepository;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.repository.specification.FoodPostSpecification;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import org.springframework.context.ApplicationEventPublisher;
import com.datn.foodshare.service.matching.DynamicMatchingGraphSynchronizer;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FoodPostService {

    private final FoodPostRepository foodPostRepository;
    private final CategoryRepository categoryRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final DynamicMatchingGraphSynchronizer matchingGraphSynchronizer;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public FoodPostResponse create(CreateFoodPostRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        requireProfileCompleted(currentUser);

        BusinessProfile businessProfile = resolveBusinessProfile(currentUser);
        Category category = resolveCategory(request.getCategoryId());

        validatePrice(request.getPostType(), request.getUnitPrice());
        validatePickupWindow(request.getPickupStartAt(), request.getPickupEndAt());
        validateExpiration(request.getExpiresAt(), request.getPickupEndAt());

        FoodPost post = FoodPost.builder()
                .businessProfile(businessProfile)
                .category(category)
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .totalQuantity(request.getTotalQuantity())
                .availableQuantity(request.getTotalQuantity())
                .unitPrice(request.getUnitPrice())
                .originalPrice(request.getOriginalPrice())
                .postType(request.getPostType())
                .postStatus(Boolean.TRUE.equals(request.getIsDraft()) ? PostStatus.DRAFT : PostStatus.AVAILABLE)
                .expiresAt(request.getExpiresAt())
                .pickupAddress(request.getPickupAddress().trim())
                .pickupStartAt(request.getPickupStartAt())
                .pickupEndAt(request.getPickupEndAt())
                .build();

        attachImages(post, request.getImages());

        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional
    public FoodPostResponse publish(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        FoodPost post = findPostOrThrow(id);
        requireOwnership(currentUser, post);

        if (post.getPostStatus() != PostStatus.DRAFT) {
            throw new BusinessException("Chỉ có thể đăng bài ở trạng thái nháp");
        }
        validateExpiration(post.getExpiresAt(), post.getPickupEndAt());

        post.setPostStatus(PostStatus.AVAILABLE);
        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional
    public void decreaseQuantity(Long foodPostId, int quantity) {
        FoodPost post = findPostOrThrow(foodPostId);

        if (post.getPostStatus() != PostStatus.AVAILABLE) {
            throw new BusinessException("Bài đăng không ở trạng thái khả dụng");
        }
        if (post.getAvailableQuantity() < quantity) {
            throw new BusinessException("Không đủ số lượng. Còn lại: " + post.getAvailableQuantity());
        }

        post.setAvailableQuantity(post.getAvailableQuantity() - quantity);

        if (post.getAvailableQuantity() == 0) {
            post.setPostStatus(PostStatus.OUT_OF_STOCK);
        }

        saveAndSynchronize(post);
    }

    @Transactional
    public void restoreQuantity(Long foodPostId, int quantity) {
        FoodPost post = findPostOrThrow(foodPostId);

        int newAvailable = post.getAvailableQuantity() + quantity;
        if (newAvailable > post.getTotalQuantity()) {
            throw new BusinessException("Số lượng khôi phục vượt quá tổng số lượng");
        }

        post.setAvailableQuantity(newAvailable);

        if (post.getPostStatus() == PostStatus.OUT_OF_STOCK && newAvailable > 0) {
            post.setPostStatus(PostStatus.AVAILABLE);
        }

        saveAndSynchronize(post);
    }

    @Transactional(readOnly = true)
    public Page<FoodPostResponse> getPublicList(FoodPostFilterRequest filter, Pageable pageable) {
        validateSearchFilters(filter);

        Page<FoodPost> posts = foodPostRepository.findAll(
                FoodPostSpecification.filterBy(filter, Instant.now()),
                pageable);
        return mapPageWithImages(posts);
    }

    @Transactional(readOnly = true)
    public Page<FoodPostResponse> getMyPosts(PostStatus status, String keyword, Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        BusinessProfile businessProfile = resolveBusinessProfile(currentUser);
        Page<FoodPost> posts = foodPostRepository.searchSupplierPosts(businessProfile.getId(), status, keyword, pageable);
        return mapPageWithImages(posts);
    }

    @Transactional(readOnly = true)
    public FoodPostResponse getDetail(Long id) {
        FoodPost post = findPostWithDetails(id);
        if (post.getPostStatus() != PostStatus.AVAILABLE && post.getPostStatus() != PostStatus.OUT_OF_STOCK) {
            throw new BusinessException("Bài đăng không tồn tại hoặc không ở trạng thái công khai");
        }
        if (post.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Bài đăng đã hết hạn");
        }
        return FoodPostResponse.from(post);
    }

    @Transactional(readOnly = true)
    public FoodPostResponse getDetailForOwner(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        FoodPost post = findPostWithDetails(id);
        if (!isOwner(currentUser, post) && currentUser.getRole() != Role.ADMIN) {
            throw new PermissionException("Bạn không có quyền xem bài đăng này");
        }
        return FoodPostResponse.from(post);
    }

    @Transactional
    public FoodPostResponse update(Long id, UpdateFoodPostRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        FoodPost post = findPostOrThrow(id);
        requireOwnership(currentUser, post);
        requireUpdatableStatus(post);

        if (request.getName() != null) {
            post.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            post.setDescription(trimToNull(request.getDescription()));
        }
        if (request.getCategoryId() != null) {
            post.setCategory(resolveCategory(request.getCategoryId()));
        }
        if (request.getTotalQuantity() != null) {
            validateNewTotalQuantity(post, request.getTotalQuantity());
            post.setTotalQuantity(request.getTotalQuantity());
        }

        PostType newPostType = request.getPostType() != null ? request.getPostType() : post.getPostType();
        BigDecimal newUnitPrice = request.getUnitPrice() != null ? request.getUnitPrice() : post.getUnitPrice();
        validatePrice(newPostType, newUnitPrice);
        post.setPostType(newPostType);
        post.setUnitPrice(newUnitPrice);
        
        if (request.getOriginalPrice() != null) {
            post.setOriginalPrice(request.getOriginalPrice());
        }

        Instant newPickupStart = request.getPickupStartAt() != null ? request.getPickupStartAt() : post.getPickupStartAt();
        Instant newPickupEnd = request.getPickupEndAt() != null ? request.getPickupEndAt() : post.getPickupEndAt();
        Instant newExpiresAt = request.getExpiresAt() != null ? request.getExpiresAt() : post.getExpiresAt();
        validatePickupWindow(newPickupStart, newPickupEnd);
        validateExpiration(newExpiresAt, newPickupEnd);
        post.setPickupStartAt(newPickupStart);
        post.setPickupEndAt(newPickupEnd);
        post.setExpiresAt(newExpiresAt);

        if (request.getPickupAddress() != null) {
            post.setPickupAddress(request.getPickupAddress().trim());
        }

        if (request.getImages() != null) {
            List<String> oldUrls = post.getImages().stream()
                    .map(img -> img.getImageUrl())
                    .toList();
            post.getImages().clear();
            attachImages(post, request.getImages());
            oldUrls.forEach(cloudinaryService::deleteFoodPostImage);
        }

        if (Boolean.FALSE.equals(request.getIsDraft()) && post.getPostStatus() == PostStatus.DRAFT) {
            post.setPostStatus(PostStatus.AVAILABLE);
        }

        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional
    public FoodPostResponse hide(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        FoodPost post = findPostOrThrow(id);
        requireOwnership(currentUser, post);

        if (post.getPostStatus() == PostStatus.DRAFT) {
            throw new BusinessException("Bài đăng nháp chưa được đăng, không thể ẩn");
        }
        if (post.getPostStatus() == PostStatus.HIDDEN) {
            throw new BusinessException("Bài đăng đã được ẩn");
        }
        if (post.getPostStatus() == PostStatus.DELETED) {
            throw new BusinessException("Bài đăng đã bị hủy, không thể ẩn");
        }
        if (post.getPostStatus() == PostStatus.EXPIRED) {
            throw new BusinessException("Bài đăng đã hết hạn, không thể ẩn");
        }

        post.setPostStatus(PostStatus.HIDDEN);
        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional
    public FoodPostResponse unhide(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        FoodPost post = findPostOrThrow(id);
        requireOwnership(currentUser, post);

        if (post.getPostStatus() != PostStatus.HIDDEN) {
            throw new BusinessException("Bài đăng không ở trạng thái ẩn");
        }
        if (post.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Bài đăng đã hết hạn, không thể khôi phục");
        }

        PostStatus restored = post.getAvailableQuantity() > 0 ? PostStatus.AVAILABLE : PostStatus.OUT_OF_STOCK;
        post.setPostStatus(restored);
        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional
    public FoodPostResponse cancel(Long id) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireRole(currentUser, Role.SUPPLIER);
        FoodPost post = findPostOrThrow(id);
        requireOwnership(currentUser, post);

        if (post.getPostStatus() == PostStatus.DELETED) {
            throw new BusinessException("Bài đăng đã bị hủy");
        }

        post.setPostStatus(PostStatus.DELETED);
        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional(readOnly = true)
    public Page<FoodPostResponse> adminGetAll(PostStatus status, Pageable pageable) {
        return mapPageWithImages(foodPostRepository.findAllForAdmin(status, pageable));
    }

    @Transactional(readOnly = true)
    public FoodPostResponse adminGetDetail(Long id) {
        return FoodPostResponse.from(findPostWithDetails(id));
    }

    @Transactional
    public FoodPostResponse adminHide(Long id) {
        FoodPost post = findPostOrThrow(id);
        if (post.getPostStatus() == PostStatus.HIDDEN) {
            throw new BusinessException("Bài đăng đã bị ẩn");
        }
        if (post.getPostStatus() == PostStatus.DELETED) {
            throw new BusinessException("Bài đăng đã bị hủy");
        }
        post.setPostStatus(PostStatus.HIDDEN);
        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    @Transactional
    public FoodPostResponse adminRestore(Long id) {
        FoodPost post = findPostOrThrow(id);
        if (post.getPostStatus() != PostStatus.HIDDEN) {
            throw new BusinessException("Bài đăng không ở trạng thái bị ẩn");
        }
        if (post.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Bài đăng đã hết hạn, không thể khôi phục");
        }
        PostStatus restored = post.getAvailableQuantity() > 0 ? PostStatus.AVAILABLE : PostStatus.OUT_OF_STOCK;
        post.setPostStatus(restored);
        return FoodPostResponse.from(saveAndSynchronize(post));
    }

    private User getAuthenticatedUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }

    private void requireRole(User user, Role expected) throws PermissionException {
        if (user.getRole() != expected) {
            throw new PermissionException("Chỉ " + expected.name() + " mới có quyền thực hiện hành động này");
        }
    }

    private void requireProfileCompleted(User user) {
        if (!user.isProfileCompleted()) {
            throw new BusinessException("Vui lòng hoàn thiện hồ sơ trước khi đăng bài");
        }
    }

    private BusinessProfile resolveBusinessProfile(User user) {
        return businessProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ kinh doanh của Nhà cung cấp"));
    }

    private Category resolveCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new BusinessException("Danh mục không tồn tại: " + categoryId));
    }

    private FoodPost findPostOrThrow(Long id) {
        return foodPostRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Bài đăng không tồn tại: " + id));
    }

    private FoodPost findPostWithDetails(Long id) {
        return foodPostRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new BusinessException("Bài đăng không tồn tại: " + id));
    }

    private boolean isOwner(User user, FoodPost post) {
        BusinessProfile bp = post.getBusinessProfile();
        return bp != null && bp.getUser() != null && bp.getUser().getId().equals(user.getId());
    }

    private void requireOwnership(User user, FoodPost post) throws PermissionException {
        if (!isOwner(user, post)) {
            throw new PermissionException("Bạn không có quyền thao tác với bài đăng này");
        }
    }

    private void requireUpdatableStatus(FoodPost post) {
        if (post.getPostStatus() == PostStatus.DELETED) {
            throw new BusinessException("Bài đăng đã bị hủy, không thể chỉnh sửa");
        }
        if (post.getPostStatus() == PostStatus.EXPIRED) {
            throw new BusinessException("Bài đăng đã hết hạn, không thể chỉnh sửa");
        }
    }

    private void validatePrice(PostType postType, BigDecimal unitPrice) {
        if (unitPrice == null) {
            throw new BusinessException("Giá không được để trống");
        }
        if (unitPrice.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá không được âm");
        }
        if (postType == PostType.FREE && unitPrice.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException("Bài đăng miễn phí phải có giá bằng 0");
        }
        if (postType == PostType.PAID && unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Bài đăng có phí phải có giá lớn hơn 0");
        }
    }

    private void validatePickupWindow(Instant start, Instant end) {
        if (!start.isBefore(end)) {
            throw new BusinessException("Thời gian bắt đầu nhận phải trước thời gian kết thúc nhận");
        }
    }

    private void validateExpiration(Instant expiresAt, Instant pickupEndAt) {
        Instant now = Instant.now();
        if (!expiresAt.isAfter(now)) {
            throw new BusinessException("Thời gian hết hạn phải ở tương lai");
        }
        if (pickupEndAt.isAfter(expiresAt)) {
            throw new BusinessException("Thời gian kết thúc nhận không được vượt quá thời gian hết hạn");
        }
    }

    private void validateNewTotalQuantity(FoodPost post, int newTotal) {
        int alreadyUsed = post.getTotalQuantity() - post.getAvailableQuantity();
        if (newTotal < alreadyUsed) {
            throw new BusinessException(
                    "Số lượng tổng mới (" + newTotal + ") không thể nhỏ hơn số lượng đã được đặt (" + alreadyUsed + ")");
        }
        int newAvailable = newTotal - alreadyUsed;
        post.setAvailableQuantity(newAvailable);
    }

    private void validateSearchFilters(FoodPostFilterRequest filter) {
        if (filter.getMinPrice() != null && filter.getMinPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá tối thiểu không được âm");
        }
        if (filter.getMaxPrice() != null && filter.getMaxPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Giá tối đa không được âm");
        }
        if (filter.getMinPrice() != null && filter.getMaxPrice() != null
                && filter.getMinPrice().compareTo(filter.getMaxPrice()) > 0) {
            throw new BusinessException("Giá tối thiểu không được lớn hơn giá tối đa");
        }
        if (filter.getMinAvailableQuantity() != null && filter.getMinAvailableQuantity() < 0) {
            throw new BusinessException("Số lượng còn lại tối thiểu không được âm");
        }
        if (filter.getExpiresFrom() != null && filter.getExpiresTo() != null
                && filter.getExpiresFrom().isAfter(filter.getExpiresTo())) {
            throw new BusinessException("Thời gian hết hạn bắt đầu không được sau thời gian kết thúc");
        }
    }

    private Page<FoodPostResponse> mapPageWithImages(Page<FoodPost> posts) {
        if (posts.hasContent()) {
            List<Long> postIds = posts.getContent().stream()
                    .map(FoodPost::getId)
                    .toList();
            foodPostRepository.findAllWithImagesByIdIn(postIds);
        }
        return posts.map(FoodPostResponse::from);
    }

    private FoodPost saveAndSynchronize(FoodPost post) {
        FoodPost saved = foodPostRepository.save(post);
        matchingGraphSynchronizer.foodPostChangedAfterCommit(saved.getId());
        return saved;
    }

    private void attachImages(FoodPost post, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return;
        List<FoodPostImage> images = new ArrayList<>();
        for (String url : imageUrls) {
            if (url != null && !url.isBlank()) {
                images.add(FoodPostImage.builder()
                        .foodPost(post)
                        .imageUrl(url.trim())
                        .build());
            }
        }
        post.getImages().addAll(images);
    }

    private String trimToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void markExpiredPosts() {
        Instant now = Instant.now();
        List<FoodPost> expiredPosts = foodPostRepository.findExpiredPosts(now);
        if (!expiredPosts.isEmpty()) {
            int count = foodPostRepository.markExpired(now);
            matchingGraphSynchronizer.rebuildAfterCommit();
            for (FoodPost post : expiredPosts) {
                eventPublisher.publishEvent(NotificationEvent.builder()
                        .source(this)
                        .user(post.getBusinessProfile().getUser())
                        .title("Bài đăng đã hết hạn")
                        .content("Bài đăng \"" + post.getName() + "\" của bạn đã hết thời gian chia sẻ.")
                        .type(NotificationType.SYSTEM)
                        .referenceType(NotificationReferenceType.FOOD_POST)
                        .referenceId(post.getId())
                        .build());
            }
            log.info("Đã chuyển {} bài đăng sang trạng thái EXPIRED", count);
        }
    }
}
