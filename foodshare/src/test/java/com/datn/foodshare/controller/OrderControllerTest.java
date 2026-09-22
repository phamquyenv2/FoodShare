package com.datn.foodshare.controller;

import com.datn.foodshare.domain.request.BatchCreateOrderRequest;
import com.datn.foodshare.domain.request.CreateOrderRequest;
import com.datn.foodshare.domain.request.RejectOrderRequest;
import com.datn.foodshare.domain.response.OrderResponse;
import com.datn.foodshare.service.OrderService;
import com.datn.foodshare.util.constant.OrderStatus;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private OrderController controller;

    @BeforeEach
    void setUp() {
        controller = new OrderController(orderService);
    }

    private OrderResponse mockOrderResponse(Long id) {
        return OrderResponse.builder().id(id).orderCode("ORD-" + id).build();
    }

    @Test
    void createOrder_returns201() throws PermissionException {
        CreateOrderRequest request = new CreateOrderRequest(1L, 2, "Note");
        OrderResponse response = mockOrderResponse(10L);
        when(orderService.createOrder(request)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.createOrder(request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void batchCreateOrders_returns201() throws PermissionException {
        BatchCreateOrderRequest request = new BatchCreateOrderRequest(List.of());
        List<OrderResponse> responses = List.of(mockOrderResponse(11L), mockOrderResponse(12L));
        when(orderService.batchCreateOrders(request)).thenReturn(responses);

        ResponseEntity<List<OrderResponse>> entity = controller.batchCreateOrders(request);

        assertEquals(HttpStatus.CREATED, entity.getStatusCode());
        assertSame(responses, entity.getBody());
    }

    @Test
    void getMyOrders_returns200() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 20);
        Page<OrderResponse> page = new PageImpl<>(List.of(mockOrderResponse(1L)));
        when(orderService.getMyOrders(pageable)).thenReturn(page);

        ResponseEntity<Page<OrderResponse>> entity = controller.getMyOrders(pageable);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(page, entity.getBody());
    }

    @Test
    void getOrderDetail_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        when(orderService.getOrderDetail(5L)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.getOrderDetail(5L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void cancelOrder_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        when(orderService.cancelOrder(5L)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.cancelOrder(5L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void getSupplierOrders_withVariousStatuses() throws PermissionException {
        Pageable pageable = PageRequest.of(0, 10);
        Page<OrderResponse> page = new PageImpl<>(List.of(mockOrderResponse(1L)));

        when(orderService.getSupplierOrders(eq(OrderStatus.PENDING), eq("food"), eq(pageable))).thenReturn(page);
        ResponseEntity<Page<OrderResponse>> e1 = controller.getSupplierOrders("PENDING", "food", pageable);
        assertEquals(HttpStatus.OK, e1.getStatusCode());

        when(orderService.getSupplierOrders(eq(null), eq(null), eq(pageable))).thenReturn(page);
        ResponseEntity<Page<OrderResponse>> e2 = controller.getSupplierOrders("all", null, pageable);
        assertEquals(HttpStatus.OK, e2.getStatusCode());

        ResponseEntity<Page<OrderResponse>> e3 = controller.getSupplierOrders("invalid_status", null, pageable);
        assertEquals(HttpStatus.OK, e3.getStatusCode());
    }

    @Test
    void acceptOrder_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        when(orderService.acceptOrder(5L)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.acceptOrder(5L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void rejectOrder_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        RejectOrderRequest request = new RejectOrderRequest("Hết món");
        when(orderService.rejectOrder(5L, request)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.rejectOrder(5L, request);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void readyForPickupOrder_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        when(orderService.readyForPickupOrder(5L)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.readyForPickupOrder(5L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void deliverOrder_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        when(orderService.deliverOrder(5L)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.deliverOrder(5L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }

    @Test
    void completeOrder_returns200() throws PermissionException {
        OrderResponse response = mockOrderResponse(5L);
        when(orderService.completeOrder(5L)).thenReturn(response);

        ResponseEntity<OrderResponse> entity = controller.completeOrder(5L);

        assertEquals(HttpStatus.OK, entity.getStatusCode());
        assertSame(response, entity.getBody());
    }
}
