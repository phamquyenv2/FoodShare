package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.OrderDetail;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.repository.BusinessProfileRepository;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.constant.NotificationChannel;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.ReportReferenceType;
import com.datn.foodshare.util.constant.ReportStatus;
import com.datn.foodshare.util.constant.Role;
import com.datn.foodshare.util.constant.VerificationStatus;
import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.PermissionException;
import com.datn.foodshare.event.NotificationEvent;
import com.datn.foodshare.util.constant.NotificationType;
import com.datn.foodshare.util.constant.NotificationReferenceType;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.datn.foodshare.util.constant.PostType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int DAILY_FREE_LIMIT = 3;

    private static final Duration INSPECTION_WINDOW = Duration.ofHours(24);
    private static final List<ReportStatus> ACTIVE_REPORT_STATUSES =
            List.of(ReportStatus.PENDING, ReportStatus.REVIEWING);
    private static final List<OrderStatus> EXPIRABLE_ORDER_STATUSES = List.of(
            OrderStatus.PENDING,
            OrderStatus.ACCEPTED,
            OrderStatus.READY_FOR_PICKUP);

    private final OrderRepository orderRepository;
    private final FoodPostRepository foodPostRepository;
    private final UserRepository userRepository;
    private final FoodPostService foodPostService;
    private final com.datn.foodshare.repository.PaymentRepository paymentRepository;
    private final com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    private final SupplierEarningService supplierEarningService;
    private final ApplicationEventPublisher eventPublisher;
    private final PermissionService permissionService;
    private final BusinessProfileRepository businessProfileRepository;
    private final com.datn.foodshare.repository.ReportRepository reportRepository;
    private final com.datn.foodshare.repository.ReviewRepository reviewRepository;
    private final com.datn.foodshare.service.matching.DynamicMatchingGraphSynchronizer matchingGraphSynchronizer;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);
        requireProfileCompleted(currentUser);
        requireVerifiedOrganization(currentUser);
        Instant now = Instant.now();

        FoodPost foodPost = foodPostRepository.findByIdWithDetails(request.getFoodPostId())
                .orElseThrow(() -> new BusinessException("Bài đăng không tồn tại: " + request.getFoodPostId()));

        validateRecipientLimits(currentUser, foodPost, request.getQuantity(),
                currentUser.getRole() == Role.RECIPIENT ? countFreeQuantityToday(currentUser.getId()) : 0);
        validateFoodPostAvailability(foodPost, now);
        validateQuantity(request.getQuantity(), foodPost.getAvailableQuantity());

        BigDecimal unitPrice = foodPost.getUnitPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        BusinessProfile businessProfile = foodPost.getBusinessProfile();

        Order order = Order.builder()
                .orderCode(generateOrderCode())
                .orderStatus(OrderStatus.PENDING)
                .totalAmount(subtotal)
                .receiver(currentUser)
                .businessProfile(businessProfile)
                .receiverNote(trimToNull(request.getReceiverNote()))
                .build();

        OrderDetail orderDetail = OrderDetail.builder()
                .order(order)
                .foodPost(foodPost)
                .unitPrice(unitPrice)
                .quantity(request.getQuantity())
                .build();

        order.getOrderDetails().add(orderDetail);

        Order savedOrder = orderRepository.save(order);

        foodPostService.decreaseQuantity(foodPost.getId(), request.getQuantity());

        log.info("Đã tạo đơn tiếp nhận {} cho user {} với {} phần từ bài đăng {}",
                savedOrder.getOrderCode(), currentUser.getId(), request.getQuantity(), foodPost.getId());

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(foodPost.getBusinessProfile().getUser())
                .title("Có đơn yêu cầu mới!")
                .content("Bạn có một yêu cầu nhận thực phẩm mới cho bài đăng: " + foodPost.getName())
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .channels(pushChannels(false))
                .build());

        matchingGraphSynchronizer.userChangedAfterCommit(currentUser.getId());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public List<OrderResponse> batchCreateOrders(com.datn.foodshare.domain.request.BatchCreateOrderRequest batchRequest) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);
        requireProfileCompleted(currentUser);
        requireVerifiedOrganization(currentUser);
        Instant now = Instant.now();

        Map<BusinessProfile, List<CreateOrderRequest>> ordersBySupplier = new HashMap<>();
        Set<Long> requestedPostIds = new HashSet<>();
        long freeQuantityToday = currentUser.getRole() == Role.RECIPIENT ? countFreeQuantityToday(currentUser.getId()) : 0;
        long freeQuantityInBatch = 0;
        
        for (CreateOrderRequest request : batchRequest.getOrders()) {
            if (!requestedPostIds.add(request.getFoodPostId())) {
                throw new BusinessException("Không được lặp lại cùng một bài đăng trong một đơn");
            }
            FoodPost foodPost = foodPostRepository.findByIdWithDetails(request.getFoodPostId())
                    .orElseThrow(() -> new BusinessException("Bài đăng không tồn tại: " + request.getFoodPostId()));

            validateRecipientLimits(currentUser, foodPost, request.getQuantity(),
                    freeQuantityToday + freeQuantityInBatch);
            if (currentUser.getRole() == Role.RECIPIENT && foodPost.getPostType() == PostType.FREE) {
                freeQuantityInBatch += request.getQuantity();
            }
            validateFoodPostAvailability(foodPost, now);
            validateQuantity(request.getQuantity(), foodPost.getAvailableQuantity());
            
            BusinessProfile businessProfile = foodPost.getBusinessProfile();
            ordersBySupplier.computeIfAbsent(businessProfile, k -> new ArrayList<>()).add(request);
        }

        List<OrderResponse> responses = new ArrayList<>();

        for (Map.Entry<BusinessProfile, List<CreateOrderRequest>> entry : ordersBySupplier.entrySet()) {
            BusinessProfile businessProfile = entry.getKey();
            List<CreateOrderRequest> supplierRequests = entry.getValue();

            BigDecimal totalAmount = BigDecimal.ZERO;
            Order order = Order.builder()
                    .orderCode(generateOrderCode())
                    .orderStatus(OrderStatus.PENDING)
                    .receiver(currentUser)
                    .businessProfile(businessProfile)
                    .receiverNote(supplierRequests.stream()
                            .map(CreateOrderRequest::getReceiverNote)
                            .filter(n -> n != null && !n.isBlank())
                            .reduce((a, b) -> a + "; " + b)
                            .map(this::trimToNull)
                            .orElse(null))
                    .build();

            for (CreateOrderRequest req : supplierRequests) {
                FoodPost foodPost = foodPostRepository.findByIdWithDetails(req.getFoodPostId()).get();
                BigDecimal unitPrice = foodPost.getUnitPrice();
                BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(req.getQuantity()));

                OrderDetail orderDetail = OrderDetail.builder()
                        .order(order)
                        .foodPost(foodPost)
                        .unitPrice(unitPrice)
                        .quantity(req.getQuantity())
                        .build();

                order.getOrderDetails().add(orderDetail);
                totalAmount = totalAmount.add(subtotal);

                foodPostService.decreaseQuantity(foodPost.getId(), req.getQuantity());
            }
            
            order.setTotalAmount(totalAmount);
            Order savedOrder = orderRepository.save(order);
            responses.add(OrderResponse.from(savedOrder));
            
            log.info("Đã tạo đơn tiếp nhận {} (Batch) cho user {} từ nhà cung cấp {}",
                    savedOrder.getOrderCode(), currentUser.getId(), businessProfile.getId());

            eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                    .user(businessProfile.getUser())
                    .title("Có đơn yêu cầu mới (Batch)!")
                    .content("Bạn có yêu cầu nhận thực phẩm mới gồm nhiều món.")
                    .type(NotificationType.ORDER)
                    .referenceType(NotificationReferenceType.ORDER)
                    .referenceId(savedOrder.getId())
                    .channels(pushChannels(false))
                    .build());
        }

        matchingGraphSynchronizer.userChangedAfterCommit(currentUser.getId());

        return responses;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Page<Order> ordersPage = orderRepository.findByReceiverId(currentUser.getId(), pageable);
        if (ordersPage.hasContent()) {
            hydrateOrderPage(ordersPage.getContent());
        }
        return ordersPage.map(OrderResponse::from);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderDetail(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền xem đơn tiếp nhận này");
        }

        com.datn.foodshare.domain.entity.Report refundReport = reportRepository.findRefundReportsByOrder(
                com.datn.foodshare.util.constant.ReportReferenceType.ORDER, orderId
        ).stream().findFirst().orElse(null);

        com.datn.foodshare.domain.entity.Review review = reviewRepository.findByOrderId(orderId).orElse(null);

        boolean hasActiveReport = hasActiveDispute(orderId);

        return OrderResponse.from(order, refundReport, review, hasActiveReport);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với đơn tiếp nhận này");
        }

        if (!transitionOrder(
                order,
                OrderStatus.PENDING,
                OrderStatus.CANCELLED,
                "Chỉ có thể hủy đơn tiếp nhận ở trạng thái chờ xác nhận")) {
            return OrderResponse.from(order);
        }
        order.setCancelledAt(Instant.now());

        for (OrderDetail detail : order.getOrderDetails()) {
            foodPostService.restoreQuantity(detail.getFoodPost().getId(), detail.getQuantity());
        }

        boolean refunded = reconcilePaymentsForTerminatedOrder(orderId);
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getBusinessProfile().getUser())
                .title("Đơn yêu cầu đã bị hủy")
                .content("Người nhận đã hủy đơn " + savedOrder.getOrderCode() + ".")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .channels(pushChannels(false))
                .build());
        if (refunded) {
            publishRefundNotification(savedOrder);
        }

        matchingGraphSynchronizer.userChangedAfterCommit(currentUser.getId());

        return OrderResponse.from(savedOrder);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getSupplierOrders(OrderStatus status, String keyword, Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireSupplierRole(currentUser);

        BusinessProfile businessProfile = currentUser.getBusinessProfile();
        if (businessProfile == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp của bạn");
        }

        Page<Order> ordersPage = orderRepository.searchSupplierOrders(businessProfile.getId(), status, keyword, pageable);
        
        if (ordersPage.hasContent()) {
            hydrateOrderPage(ordersPage.getContent());
        }
        return ordersPage.map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse acceptOrder(Long orderId) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (order.getOrderStatus() == OrderStatus.PENDING) {
            Instant now = Instant.now();
            validateOrderAcceptWindow(order, now);
            order.setPickupDeadline(calculatePickupDeadline(order));
        }
        if (!transitionOrder(
                order,
                OrderStatus.PENDING,
                OrderStatus.ACCEPTED,
                "Chỉ có thể chấp nhận đơn ở trạng thái chờ")) {
            return OrderResponse.from(order);
        }
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getReceiver())
                .title("Đơn yêu cầu đã được chấp nhận!")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã được nhà cung cấp chấp nhận.")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .channels(pushChannels(false))
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse rejectOrder(Long orderId, com.datn.foodshare.domain.request.RejectOrderRequest request) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (!transitionOrder(
                order,
                OrderStatus.PENDING,
                OrderStatus.REJECTED,
                "Chỉ có thể từ chối đơn ở trạng thái chờ")) {
            return OrderResponse.from(order);
        }
        order.setRejectionReason(request == null ? null : trimToNull(request.getRejectionReason()));
        order.setRejectedAt(Instant.now());

        for (OrderDetail detail : order.getOrderDetails()) {
            foodPostService.restoreQuantity(detail.getFoodPost().getId(), detail.getQuantity());
        }

        boolean refunded = reconcilePaymentsForTerminatedOrder(orderId);

        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getReceiver())
                .title("Đơn yêu cầu bị từ chối")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã bị từ chối với lý do: "
                        + savedOrder.getRejectionReason()
                        + (refunded ? ". Khoản thanh toán đã được hoàn lại." : ""))
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .channels(pushChannels(refunded))
                .build());

        Order responseOrder = orderRepository.findByIdWithDetails(savedOrder.getId()).orElse(savedOrder);
        orderRepository.findAllWithPaymentsByIdIn(List.of(savedOrder.getId()));

        matchingGraphSynchronizer.userChangedAfterCommit(order.getReceiver().getId());

        return OrderResponse.from(responseOrder);
    }

    @Transactional
    public OrderResponse readyForPickupOrder(Long orderId) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (!transitionOrder(
                order,
                OrderStatus.ACCEPTED,
                OrderStatus.READY_FOR_PICKUP,
                "Chỉ có thể chuẩn bị xong đơn đã được chấp nhận")) {
            return OrderResponse.from(order);
        }
        order.setReadyAt(Instant.now());
        
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getReceiver())
                .title("Đơn yêu cầu đã sẵn sàng!")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã sẵn sàng để bạn đến nhận.")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .channels(pushChannels(false))
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse deliverOrder(Long orderId) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (!transitionOrder(
                order,
                OrderStatus.READY_FOR_PICKUP,
                OrderStatus.DELIVERED,
                "Chỉ có thể xác nhận giao đơn ở trạng thái đã chuẩn bị xong (READY_FOR_PICKUP)")) {
            return OrderResponse.from(order);
        }
        order.setDeliveredAt(Instant.now());
                
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getReceiver())
                .title("Đơn yêu cầu đã được giao")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã được giao cho bạn. Vui lòng xác nhận hoàn thành.")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .channels(pushChannels(false))
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với đơn tiếp nhận này");
        }

        if (order.getOrderStatus() == OrderStatus.COMPLETED) {
            return OrderResponse.from(order);
        }
        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException("Chỉ có thể hoàn thành đơn ở trạng thái đã giao (DELIVERED)");
        }
        if (hasActiveDispute(orderId)) {
            throw new BusinessException("Đơn hàng đang có khiếu nại cần được đối soát");
        }
        if (!hasSuccessfulPaymentWhenRequired(order)) {
            throw new BusinessException("Đơn hàng có phí chỉ có thể hoàn thành sau khi thanh toán thành công");
        }

        Order savedOrder = completeDeliveredOrder(order, Instant.now(), false);
        return OrderResponse.from(savedOrder);
    }

    private Order getSupplierOrder(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireSupplierRole(currentUser);

        BusinessProfile businessProfile = currentUser.getBusinessProfile();
        if (businessProfile == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp của bạn");
        }

        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getBusinessProfile().getId().equals(businessProfile.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với đơn tiếp nhận này");
        }

        return order;
    }

    private void requireSupplierRole(User user) throws PermissionException {
        permissionService.requireRole(user, Role.SUPPLIER);
    }

    private User getAuthenticatedUser() {
        return permissionService.currentUser();
    }

    private void requireReceiverRole(User user) throws PermissionException {
        permissionService.requireReceiver(user);
    }

    private void requireProfileCompleted(User user) {
        if (!user.isProfileCompleted()) {
            throw new BusinessException("Vui lòng hoàn thiện hồ sơ trước khi tạo đơn tiếp nhận");
        }
    }

    private void requireVerifiedOrganization(User user) {
        if (user.getRole() == Role.ORGANIZATION) {
            BusinessProfile profile = businessProfileRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new BusinessException("Hồ sơ Tổ chức không tồn tại"));
            if (profile.getVerificationStatus() != VerificationStatus.VERIFIED) {
                throw new BusinessException("Tài khoản Tổ chức phải được xác minh trước khi tạo đơn tiếp nhận");
            }
        }
    }

    private void validateFoodPostAvailability(FoodPost foodPost, Instant now) {
        if (foodPost.getPostStatus() != PostStatus.AVAILABLE) {
            throw new BusinessException("Bài đăng không ở trạng thái khả dụng");
        }
        if (!foodPost.getExpiresAt().isAfter(now)) {
            throw new BusinessException("Bài đăng đã hết hạn");
        }
        if (foodPost.getPickupEndAt().isBefore(now)) {
            throw new BusinessException("Đã quá thời gian kết thúc nhận hàng");
        }
    }

    private void validateQuantity(int requestedQuantity, int availableQuantity) {
        if (requestedQuantity <= 0) {
            throw new BusinessException("Số lượng phải lớn hơn 0");
        }
        if (requestedQuantity > availableQuantity) {
            throw new BusinessException("Không đủ số lượng. Còn lại: " + availableQuantity);
        }
    }

    private void validateRecipientLimits(User user, FoodPost foodPost, int quantity, long freeQuantityAlreadyConsidered) {
        if (user.getRole() != Role.RECIPIENT) return;
        if (quantity != 1) {
            throw new BusinessException("Người nhận cá nhân chỉ được nhận 1 phần cho mỗi bài đăng");
        }
        if (foodPost.getPostType() == PostType.FREE
                && freeQuantityAlreadyConsidered + quantity > DAILY_FREE_LIMIT) {
            throw new BusinessException("Daily free-food limit exceeded (maximum " + DAILY_FREE_LIMIT + " portions)");
        }
        if (orderRepository.existsByReceiverAndFoodPost(user.getId(), foodPost.getId())) {
            throw new BusinessException("Bạn đã gửi yêu cầu cho bài đăng này rồi");
        }
    }

    private long countFreeQuantityToday(Long receiverId) {
        ZoneId businessZone = ZoneId.of("Asia/Ho_Chi_Minh");
        LocalDate today = LocalDate.now(businessZone);
        Instant from = today.atStartOfDay(businessZone).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(businessZone).toInstant();
        return orderRepository.sumFreeQuantityByReceiverBetween(receiverId, from, to);
    }

    private boolean transitionOrder(
            Order order,
            OrderStatus expectedCurrentStatus,
            OrderStatus targetStatus,
            String invalidTransitionMessage) {
        if (order.getOrderStatus() == targetStatus) {
            return false;
        }
        if (order.getOrderStatus() != expectedCurrentStatus) {
            throw new BusinessException(invalidTransitionMessage);
        }
        order.setOrderStatus(targetStatus);
        return true;
    }

    private void validateOrderAcceptWindow(Order order, Instant now) {
        for (OrderDetail detail : order.getOrderDetails()) {
            FoodPost foodPost = detail.getFoodPost();
            if (!foodPost.getExpiresAt().isAfter(now)) {
                throw new BusinessException("Không thể chấp nhận đơn vì bài đăng đã hết hạn");
            }
            if (foodPost.getPickupEndAt().isBefore(now)) {
                throw new BusinessException("Không thể chấp nhận đơn sau thời gian kết thúc nhận hàng");
            }
        }
    }

    private Instant calculatePickupDeadline(Order order) {
        return order.getOrderDetails().stream()
                .map(OrderDetail::getFoodPost)
                .map(FoodPost::getPickupEndAt)
                .min(Instant::compareTo)
                .orElseThrow(() -> new BusinessException("Đơn hàng không có thông tin thời gian nhận hàng"));
    }

    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cancelTimedOutOrders() {
        Instant now = Instant.now();
        List<Long> orderIds = orderRepository.findIdsPastFulfillmentWindow(EXPIRABLE_ORDER_STATUSES, now);

        for (Long orderId : orderIds) {
            Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null
                    || !EXPIRABLE_ORDER_STATUSES.contains(order.getOrderStatus())
                    || !isPastFulfillmentWindow(order, now)) {
                continue;
            }

            boolean hasExpiredFood = order.getOrderDetails().stream()
                    .map(OrderDetail::getFoodPost)
                    .anyMatch(post -> !post.getExpiresAt().isAfter(now));
            order.setOrderStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(now);
            order.setCancellationReason(hasExpiredFood
                    ? "Tự động hủy do món ăn đã hết hạn"
                    : "Tự động hủy do quá thời hạn nhận hàng");
            for (OrderDetail detail : order.getOrderDetails()) {
                foodPostService.restoreQuantity(detail.getFoodPost().getId(), detail.getQuantity());
            }
            boolean refunded = reconcilePaymentsForTerminatedOrder(orderId);
            Order savedOrder = orderRepository.save(order);

            eventPublisher.publishEvent(NotificationEvent.builder()
                    .source(this)
                    .user(savedOrder.getReceiver())
                    .title("Đơn yêu cầu đã tự động hủy")
                    .content("Đơn " + savedOrder.getOrderCode() + " đã tự động hủy vì "
                            + (hasExpiredFood ? "món ăn hết hạn" : "quá thời gian nhận hàng")
                            + (refunded ? ". Khoản thanh toán đã được hoàn lại." : "."))
                    .type(NotificationType.ORDER)
                    .referenceType(NotificationReferenceType.ORDER)
                    .referenceId(savedOrder.getId())
                    .channels(pushChannels(refunded))
                    .build());
        }
    }

    private boolean isPastFulfillmentWindow(Order order, Instant now) {
        if (order.getPickupDeadline() != null && order.getPickupDeadline().isBefore(now)) {
            return true;
        }
        return order.getOrderDetails().stream()
                .map(OrderDetail::getFoodPost)
                .anyMatch(post -> post.getPickupEndAt().isBefore(now)
                        || !post.getExpiresAt().isAfter(now));
    }

    @Scheduled(fixedDelayString = "${foodshare.orders.auto-complete-interval-ms:60000}")
    @Transactional
    public void autoCompleteDeliveredOrders() {
        Instant now = Instant.now();
        Instant deliveredBefore = now.minus(INSPECTION_WINDOW);
        List<Long> orderIds = orderRepository.findIdsEligibleForAutoCompletion(
                OrderStatus.DELIVERED,
                deliveredBefore,
                ReportReferenceType.ORDER,
                ACTIVE_REPORT_STATUSES);

        for (Long orderId : orderIds) {
            Order order = orderRepository.findByIdForUpdate(orderId).orElse(null);
            if (order == null
                    || order.getOrderStatus() != OrderStatus.DELIVERED
                    || order.getDeliveredAt() == null
                    || order.getDeliveredAt().isAfter(deliveredBefore)
                    || hasActiveDispute(orderId)
                    || !hasSuccessfulPaymentWhenRequired(order)) {
                continue;
            }

            completeDeliveredOrder(order, now, true);
        }
    }

    private Order completeDeliveredOrder(Order order, Instant completedAt, boolean automatic) {
        transitionOrder(
                order,
                OrderStatus.DELIVERED,
                OrderStatus.COMPLETED,
                "Chỉ có thể hoàn thành đơn ở trạng thái đã giao (DELIVERED)");
        order.setCompletedAt(completedAt);

        Order savedOrder = orderRepository.save(order);
        supplierEarningService.recordForCompletedOrder(savedOrder);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getBusinessProfile().getUser())
                .title("Đơn yêu cầu đã hoàn thành")
                .content(automatic
                        ? "Đơn yêu cầu " + savedOrder.getOrderCode()
                                + " đã tự động hoàn thành sau 24 giờ không có khiếu nại."
                        : "Đơn yêu cầu " + savedOrder.getOrderCode()
                                + " đã được người nhận xác nhận hoàn thành.")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .build());
        return savedOrder;
    }

    private boolean hasSuccessfulPaymentWhenRequired(Order order) {
        return order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0
                || paymentRepository.existsByOrderIdAndPaymentStatus(
                        order.getId(),
                        com.datn.foodshare.util.constant.TransactionStatus.SUCCESS);
    }

    private boolean hasActiveDispute(Long orderId) {
        return orderRepository.hasActiveReport(
                orderId,
                ReportReferenceType.ORDER,
                ACTIVE_REPORT_STATUSES);
    }

    private String generateOrderCode() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void hydrateOrderPage(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getId).toList();
        List<Order> hydratedOrders = orderRepository.findAllWithDetailsByIdIn(orderIds);
        List<Long> foodPostIds = hydratedOrders.stream()
                .flatMap(order -> order.getOrderDetails().stream())
                .map(OrderDetail::getFoodPost)
                .map(FoodPost::getId)
                .distinct()
                .toList();
        if (!foodPostIds.isEmpty()) {
            foodPostRepository.findAllWithImagesByIdIn(foodPostIds);
        }
        orderRepository.findAllWithPaymentsByIdIn(orderIds);
    }

    private String trimToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private boolean reconcilePaymentsForTerminatedOrder(Long orderId) {
        boolean refunded = false;
        java.util.List<com.datn.foodshare.domain.entity.Payment> payments = paymentRepository.findByOrderId(orderId);
        for (com.datn.foodshare.domain.entity.Payment payment : payments) {
            if (payment.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.SUCCESS) {
                com.datn.foodshare.service.payment.strategy.PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
                com.datn.foodshare.domain.entity.Payment refundedPayment = strategy.processRefund(payment);
                paymentRepository.save(refundedPayment);
                supplierEarningService.reverseForRefundedPayment(refundedPayment);
                refunded = true;
            } else if (payment.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.PENDING
                    || payment.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.PROCESSING) {
                payment.setPaymentStatus(com.datn.foodshare.util.constant.TransactionStatus.CANCELLED);
                paymentRepository.save(payment);
            }
        }
        return refunded;
    }

    private Set<NotificationChannel> pushChannels(boolean email) {
        return email
                ? Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH, NotificationChannel.EMAIL)
                : Set.of(NotificationChannel.IN_APP, NotificationChannel.PUSH);
    }

    private void publishRefundNotification(Order order) {
        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(order.getReceiver())
                .title("Khoản thanh toán đã được hoàn lại")
                .content("Khoản thanh toán của đơn " + order.getOrderCode() + " đã được hoàn lại.")
                .type(NotificationType.PAYMENT)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(order.getId())
                .channels(pushChannels(true))
                .build());
    }
}
