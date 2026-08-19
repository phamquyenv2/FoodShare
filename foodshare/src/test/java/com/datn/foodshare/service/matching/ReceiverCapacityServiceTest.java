package com.datn.foodshare.service.matching;

import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.util.constant.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReceiverCapacityServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private ReceiverCapacityService receiverCapacityService;

    @Test
    void countActiveOrders_mapsGroupedRepositoryResult() {
        when(orderRepository.countActiveOrdersByReceiverIds(any(), any())).thenReturn(List.of(
                new Object[]{1L, 2L},
                new Object[]{2L, 4L}
        ));

        Map<Long, Long> result = receiverCapacityService.countActiveOrders(List.of(1L, 2L));

        assertEquals(Map.of(1L, 2L, 2L, 4L), result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(orderRepository).countActiveOrdersByReceiverIds(any(), statuses.capture());
        assertEquals(
                List.of(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.READY_FOR_PICKUP),
                List.copyOf(statuses.getValue())
        );
    }

    @Test
    void countActiveOrders_emptyInputDoesNotQueryRepository() {
        assertEquals(Map.of(), receiverCapacityService.countActiveOrders(List.of()));
        verify(orderRepository, never()).countActiveOrdersByReceiverIds(any(), any());
    }
}
