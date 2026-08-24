package com.datn.foodshare.service;

import com.datn.foodshare.domain.entity.BusinessProfile;
import com.datn.foodshare.domain.entity.FoodPost;
import com.datn.foodshare.domain.entity.Order;
import com.datn.foodshare.domain.entity.OrderDetail;
import com.datn.foodshare.domain.entity.User;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.repository.FoodPostRepository;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.util.SecurityUtil;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.constant.PostStatus;
import com.datn.foodshare.util.constant.Role;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final FoodPostRepository foodPostRepository;
    private final UserRepository userRepository;
    private final FoodPostService foodPostService;
    private final com.datn.foodshare.repository.PaymentRepository paymentRepository;
    private final com.datn.foodshare.service.payment.strategy.PaymentStrategyFactory paymentStrategyFactory;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);
        requireProfileCompleted(currentUser);

        FoodPost foodPost = foodPostRepository.findByIdWithDetails(request.getFoodPostId())
                .orElseThrow(() -> new BusinessException("Bài đăng không tồn tại: " + request.getFoodPostId()));

        validateFoodPostAvailability(foodPost);
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
                .subtotal(subtotal)
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
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public List<OrderResponse> batchCreateOrders(com.datn.foodshare.domain.request.BatchCreateOrderRequest batchRequest) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);
        requireProfileCompleted(currentUser);

        Map<BusinessProfile, List<CreateOrderRequest>> ordersBySupplier = new HashMap<>();
        
        for (CreateOrderRequest request : batchRequest.getOrders()) {
            FoodPost foodPost = foodPostRepository.findByIdWithDetails(request.getFoodPostId())
                    .orElseThrow(() -> new BusinessException("Bài đăng không tồn tại: " + request.getFoodPostId()));

            validateFoodPostAvailability(foodPost);
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
                        .subtotal(subtotal)
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
                    .build());
        }

        return responses;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Page<Order> ordersPage = orderRepository.findByReceiverId(currentUser.getId(), pageable);
        if (ordersPage.hasContent()) {
            List<Long> orderIds = ordersPage.getContent().stream().map(Order::getId).toList();
            orderRepository.findAllWithDetailsByIdIn(orderIds);
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

        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse cancelOrder(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với đơn tiếp nhận này");
        }

        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Chỉ có thể hủy đơn tiếp nhận ở trạng thái chờ xác nhận");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(Instant.now());

        for (OrderDetail detail : order.getOrderDetails()) {
            foodPostService.restoreQuantity(detail.getFoodPost().getId(), detail.getQuantity());
        }

        processRefundIfPaid(orderId);

        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getSupplierOrders(Pageable pageable) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireSupplierRole(currentUser);

        BusinessProfile businessProfile = currentUser.getBusinessProfile();
        if (businessProfile == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp của bạn");
        }

        Page<Order> ordersPage = orderRepository.findByBusinessProfileId(businessProfile.getId(), pageable);
        if (ordersPage.hasContent()) {
            List<Long> orderIds = ordersPage.getContent().stream().map(Order::getId).toList();
            orderRepository.findAllWithDetailsByIdIn(orderIds);
        }
        return ordersPage.map(OrderResponse::from);
    }

    @Transactional
    public OrderResponse acceptOrder(Long orderId) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Chỉ có thể chấp nhận đơn ở trạng thái chờ");
        }
        order.setOrderStatus(OrderStatus.ACCEPTED);
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getReceiver())
                .title("Đơn yêu cầu đã được chấp nhận!")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã được nhà cung cấp chấp nhận.")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse rejectOrder(Long orderId, com.datn.foodshare.domain.request.RejectOrderRequest request) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new BusinessException("Chỉ có thể từ chối đơn ở trạng thái chờ");
        }
        order.setOrderStatus(OrderStatus.REJECTED);
        order.setRejectionReason(trimToNull(request.getRejectionReason()));
        order.setRejectedAt(Instant.now());

        for (OrderDetail detail : order.getOrderDetails()) {
            foodPostService.restoreQuantity(detail.getFoodPost().getId(), detail.getQuantity());
        }

        processRefundIfPaid(orderId);

        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getReceiver())
                .title("Đơn yêu cầu bị từ chối")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã bị từ chối với lý do: " + savedOrder.getRejectionReason())
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse readyForPickupOrder(Long orderId) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (order.getOrderStatus() != OrderStatus.ACCEPTED) {
            throw new BusinessException("Chỉ có thể chuẩn bị xong đơn đã được chấp nhận");
        }
        order.setOrderStatus(OrderStatus.READY_FOR_PICKUP);
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
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse deliverOrder(Long orderId) throws PermissionException {
        Order order = getSupplierOrder(orderId);
        if (order.getOrderStatus() != OrderStatus.READY_FOR_PICKUP) {
            throw new BusinessException("Chỉ có thể xác nhận giao đơn ở trạng thái đã chuẩn bị xong (READY_FOR_PICKUP)");
        }
        order.setOrderStatus(OrderStatus.DELIVERED);
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
                .build());

        return OrderResponse.from(savedOrder);
    }

    @Transactional
    public OrderResponse completeOrder(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireReceiverRole(currentUser);

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getReceiver().getId().equals(currentUser.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với đơn tiếp nhận này");
        }

        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            throw new BusinessException("Chỉ có thể hoàn thành đơn ở trạng thái đã giao (DELIVERED)");
        }

        order.setOrderStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(Instant.now());
        
        Order savedOrder = orderRepository.save(order);

        eventPublisher.publishEvent(NotificationEvent.builder()
                .source(this)
                .user(savedOrder.getBusinessProfile().getUser())
                .title("Đơn yêu cầu đã hoàn thành")
                .content("Đơn yêu cầu " + savedOrder.getOrderCode() + " đã được người nhận xác nhận hoàn thành.")
                .type(NotificationType.ORDER)
                .referenceType(NotificationReferenceType.ORDER)
                .referenceId(savedOrder.getId())
                .build());

        return OrderResponse.from(savedOrder);
    }

    private Order getSupplierOrder(Long orderId) throws PermissionException {
        User currentUser = getAuthenticatedUser();
        requireSupplierRole(currentUser);

        BusinessProfile businessProfile = currentUser.getBusinessProfile();
        if (businessProfile == null) {
            throw new BusinessException("Không tìm thấy hồ sơ doanh nghiệp của bạn");
        }

        Order order = orderRepository.findByIdWithDetails(orderId)
                .orElseThrow(() -> new BusinessException("Đơn tiếp nhận không tồn tại: " + orderId));

        if (!order.getBusinessProfile().getId().equals(businessProfile.getId())) {
            throw new PermissionException("Bạn không có quyền thao tác với đơn tiếp nhận này");
        }

        return order;
    }

    private void requireSupplierRole(User user) throws PermissionException {
        if (user.getRole() != Role.SUPPLIER) {
            throw new PermissionException("Chỉ SUPPLIER mới có quyền thực hiện thao tác này");
        }
    }

    private User getAuthenticatedUser() {
        Long userId = SecurityUtil.getCurrentUserId()
                .orElseThrow(() -> new BadCredentialsException("Không xác định được người dùng hiện tại"));
        return userRepository.findById(userId)
                .orElseThrow(() -> new BadCredentialsException("Tài khoản không tồn tại"));
    }

    private void requireReceiverRole(User user) throws PermissionException {
        if (user.getRole() != Role.RECIPIENT && user.getRole() != Role.ORGANIZATION) {
            throw new PermissionException("Chỉ RECIPIENT hoặc ORGANIZATION mới có quyền tạo đơn tiếp nhận");
        }
    }

    private void requireProfileCompleted(User user) {
        if (!user.isProfileCompleted()) {
            throw new BusinessException("Vui lòng hoàn thiện hồ sơ trước khi tạo đơn tiếp nhận");
        }
    }

    private void validateFoodPostAvailability(FoodPost foodPost) {
        if (foodPost.getPostStatus() != PostStatus.AVAILABLE) {
            throw new BusinessException("Bài đăng không ở trạng thái khả dụng");
        }
        if (foodPost.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("Bài đăng đã hết hạn");
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

    private String generateOrderCode() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String trimToNull(String value) {
        return (value != null && !value.isBlank()) ? value.trim() : null;
    }

    private void processRefundIfPaid(Long orderId) {
        java.util.List<com.datn.foodshare.domain.entity.Payment> payments = paymentRepository.findByOrderId(orderId);
        for (com.datn.foodshare.domain.entity.Payment payment : payments) {
            if (payment.getPaymentStatus() == com.datn.foodshare.util.constant.TransactionStatus.SUCCESS) {
                com.datn.foodshare.service.payment.strategy.PaymentStrategy strategy = paymentStrategyFactory.getStrategy(payment.getMethod());
                com.datn.foodshare.domain.entity.Payment refundedPayment = strategy.processRefund(payment);
                paymentRepository.save(refundedPayment);
            }
        }
    }
}
