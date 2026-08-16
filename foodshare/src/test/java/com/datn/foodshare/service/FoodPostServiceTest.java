package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.Category;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateFoodPostRequest;
import com.datn.foodshare.domain.request.UpdateFoodPostRequest;
import com.datn.foodshare.domain.response.FoodPostResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.CategoryRepository;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.PostType;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodPostServiceTest {

    @Mock
    private FoodPostRepository foodPostRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private BusinessProfileRepository businessProfileRepository;
    @Mock
    private UserRepository userRepository;

    private FoodPostService foodPostService;

    private static final Long SUPPLIER_USER_ID = 10L;
    private static final Long OTHER_SUPPLIER_USER_ID = 20L;
    private static final Long CATEGORY_ID = 1L;
    private static final Long POST_ID = 100L;

    @BeforeEach
    void setUp() {
        foodPostService = new FoodPostService(
                foodPostRepository,
                categoryRepository,
                businessProfileRepository,
                userRepository
        );
    }

    @Test
    void create_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            when(businessProfileRepository.findByUserId(SUPPLIER_USER_ID)).thenReturn(Optional.of(businessProfile()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));
            when(foodPostRepository.save(any(FoodPost.class))).thenAnswer(inv -> {
                FoodPost post = inv.getArgument(0);
                post.setId(POST_ID);
                return post;
            });

            CreateFoodPostRequest req = validCreateRequest();
            FoodPostResponse response = foodPostService.create(req);

            assertNotNull(response);
            assertEquals("Bánh mì", response.getName());
            assertEquals(10, response.getTotalQuantity());
            assertEquals(10, response.getAvailableQuantity());
            assertEquals(PostStatus.AVAILABLE, response.getPostStatus());
            verify(foodPostRepository).save(any(FoodPost.class));
        }
    }

    @Test
    void create_rejectsNonSupplier() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User recipient = supplierUser();
            recipient.setRole(Role.RECIPIENT);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(recipient));

            assertThrows(PermissionException.class, () -> foodPostService.create(validCreateRequest()));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void create_rejectsIncompleteProfile() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            User user = supplierUser();
            user.setProfileCompleted(false);
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(user));

            assertThrows(BusinessException.class, () -> foodPostService.create(validCreateRequest()));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void create_rejectsPaidPostWithZeroPrice() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            when(businessProfileRepository.findByUserId(SUPPLIER_USER_ID)).thenReturn(Optional.of(businessProfile()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

            CreateFoodPostRequest req = validCreateRequest();
            req.setPostType(PostType.PAID);
            req.setUnitPrice(BigDecimal.ZERO);

            assertThrows(BusinessException.class, () -> foodPostService.create(req));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void create_rejectsFreePostWithNonZeroPrice() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            when(businessProfileRepository.findByUserId(SUPPLIER_USER_ID)).thenReturn(Optional.of(businessProfile()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

            CreateFoodPostRequest req = validCreateRequest();
            req.setUnitPrice(new BigDecimal("5000"));

            assertThrows(BusinessException.class, () -> foodPostService.create(req));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void create_rejectsExpiredExpiresAt() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            when(businessProfileRepository.findByUserId(SUPPLIER_USER_ID)).thenReturn(Optional.of(businessProfile()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

            CreateFoodPostRequest req = validCreateRequest();
            req.setExpiresAt(Instant.now().minus(1, ChronoUnit.HOURS));

            assertThrows(BusinessException.class, () -> foodPostService.create(req));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void create_rejectsInvalidPickupWindow() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            when(businessProfileRepository.findByUserId(SUPPLIER_USER_ID)).thenReturn(Optional.of(businessProfile()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category()));

            CreateFoodPostRequest req = validCreateRequest();
            req.setPickupStartAt(Instant.now().plus(3, ChronoUnit.HOURS));
            req.setPickupEndAt(Instant.now().plus(1, ChronoUnit.HOURS));

            assertThrows(BusinessException.class, () -> foodPostService.create(req));
        }
    }

    @Test
    void create_rejectsMissingCategory() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            when(businessProfileRepository.findByUserId(SUPPLIER_USER_ID)).thenReturn(Optional.of(businessProfile()));
            when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

            assertThrows(BusinessException.class, () -> foodPostService.create(validCreateRequest()));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void update_rejectsOtherSupplier() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(OTHER_SUPPLIER_USER_ID));
            User otherSupplier = supplierUser();
            otherSupplier.setId(OTHER_SUPPLIER_USER_ID);
            when(userRepository.findById(OTHER_SUPPLIER_USER_ID)).thenReturn(Optional.of(otherSupplier));
            when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(availablePost()));

            assertThrows(PermissionException.class,
                    () -> foodPostService.update(POST_ID, new UpdateFoodPostRequest()));
            verify(foodPostRepository, never()).save(any());
        }
    }

    @Test
    void hide_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            FoodPost post = availablePost();
            when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(post));
            when(foodPostRepository.save(post)).thenReturn(post);

            FoodPostResponse response = foodPostService.hide(POST_ID);

            assertEquals(PostStatus.HIDDEN, response.getPostStatus());
        }
    }

    @Test
    void unhide_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            FoodPost post = availablePost();
            post.setPostStatus(PostStatus.HIDDEN);
            when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(post));
            when(foodPostRepository.save(post)).thenReturn(post);

            FoodPostResponse response = foodPostService.unhide(POST_ID);

            assertEquals(PostStatus.AVAILABLE, response.getPostStatus());
        }
    }

    @Test
    void cancel_success() throws PermissionException {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            FoodPost post = availablePost();
            when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(post));
            when(foodPostRepository.save(post)).thenReturn(post);

            FoodPostResponse response = foodPostService.cancel(POST_ID);

            assertEquals(PostStatus.DELETED, response.getPostStatus());
        }
    }

    @Test
    void adminHide_success() {
        FoodPost post = availablePost();
        when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(foodPostRepository.save(post)).thenReturn(post);

        FoodPostResponse response = foodPostService.adminHide(POST_ID);

        assertEquals(PostStatus.HIDDEN, response.getPostStatus());
    }

    @Test
    void adminRestore_success() {
        FoodPost post = availablePost();
        post.setPostStatus(PostStatus.HIDDEN);
        when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(post));
        when(foodPostRepository.save(post)).thenReturn(post);

        FoodPostResponse response = foodPostService.adminRestore(POST_ID);

        assertEquals(PostStatus.AVAILABLE, response.getPostStatus());
    }

    @Test
    void update_quantityInvariant_rejectsNewTotalBelowUsed() {
        try (MockedStatic<SecurityUtil> su = mockStatic(SecurityUtil.class)) {
            su.when(SecurityUtil::getCurrentUserId).thenReturn(Optional.of(SUPPLIER_USER_ID));
            when(userRepository.findById(SUPPLIER_USER_ID)).thenReturn(Optional.of(supplierUser()));
            FoodPost post = availablePost();
            post.setTotalQuantity(10);
            post.setAvailableQuantity(3);
            when(foodPostRepository.findById(POST_ID)).thenReturn(Optional.of(post));

            UpdateFoodPostRequest req = new UpdateFoodPostRequest();
            req.setTotalQuantity(5);

            assertThrows(BusinessException.class, () -> foodPostService.update(POST_ID, req));
        }
    }

    @Test
    void getDetail_blocksHiddenPost() {
        FoodPost post = availablePost();
        post.setPostStatus(PostStatus.HIDDEN);
        when(foodPostRepository.findByIdWithDetails(POST_ID)).thenReturn(Optional.of(post));

        assertThrows(BusinessException.class, () -> foodPostService.getDetail(POST_ID));
    }

    private User supplierUser() {
        User user = new User();
        user.setId(SUPPLIER_USER_ID);
        user.setRole(Role.SUPPLIER);
        user.setProfileCompleted(true);
        user.setFullName("Supplier A");
        user.setActive(true);
        return user;
    }

    private BusinessProfile businessProfile() {
        User user = supplierUser();
        BusinessProfile bp = new BusinessProfile();
        bp.setId(1L);
        bp.setUser(user);
        bp.setName("Cửa hàng A");
        return bp;
    }

    private Category category() {
        Category cat = new Category();
        cat.setId(CATEGORY_ID);
        cat.setName("Bánh và đồ ăn nhẹ");
        return cat;
    }

    private FoodPost availablePost() {
        FoodPost post = FoodPost.builder()
                .name("Bánh mì")
                .description("Còn mới")
                .totalQuantity(10)
                .availableQuantity(10)
                .unitPrice(BigDecimal.ZERO)
                .postType(PostType.FREE)
                .postStatus(PostStatus.AVAILABLE)
                .expiresAt(Instant.now().plus(2, ChronoUnit.DAYS))
                .pickupAddress("123 ABC")
                .pickupStartAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .pickupEndAt(Instant.now().plus(3, ChronoUnit.HOURS))
                .build();
        post.setId(POST_ID);
        post.setCategory(category());
        post.setBusinessProfile(businessProfile());
        return post;
    }

    private CreateFoodPostRequest validCreateRequest() {
        CreateFoodPostRequest req = new CreateFoodPostRequest();
        req.setName("Bánh mì");
        req.setDescription("Còn nguyên");
        req.setCategoryId(CATEGORY_ID);
        req.setTotalQuantity(10);
        req.setPostType(PostType.FREE);
        req.setUnitPrice(BigDecimal.ZERO);
        req.setExpiresAt(Instant.now().plus(2, ChronoUnit.DAYS));
        req.setPickupAddress("123 ABC");
        req.setPickupStartAt(Instant.now().plus(1, ChronoUnit.HOURS));
        req.setPickupEndAt(Instant.now().plus(3, ChronoUnit.HOURS));
        return req;
    }
}
