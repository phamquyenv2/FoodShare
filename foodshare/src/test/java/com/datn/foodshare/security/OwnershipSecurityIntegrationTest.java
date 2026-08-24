package com.datn.foodshare.security;

import com.datn.foodshare.config.SecurityConfiguration;
import com.datn.foodshare.controller.OrderController;
import com.datn.foodshare.service.OrderService;
import com.datn.foodshare.util.error.PermissionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@Import({
        SecurityConfiguration.class,
        JwtAuthenticationFilter.class,
        CustomAuthenticationEntryPoint.class,
        CustomAccessDeniedHandler.class
})
class OwnershipSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private com.datn.foodshare.service.CustomUserDetailsService customUserDetailsService;

    @Test
    void userTryingToCancelOtherUsersOrder_ThrowsPermissionException_TranslatedTo403() throws Exception {
        // Mock the service to throw PermissionException when trying to cancel an order
        // This simulates the ownership check inside the service layer failing.
        when(orderService.cancelOrder(1L)).thenThrow(new PermissionException("Bạn không có quyền sửa đơn hàng này"));

        // Execute the request as a RECIPIENT
        mockMvc.perform(patch("/api/orders/1/cancel")
                        .with(user("attacker").roles("RECIPIENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.statusCode").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Bạn không có quyền sửa đơn hàng này"));
    }
}