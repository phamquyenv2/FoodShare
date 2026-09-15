package com.datn.foodshare.security;

import com.datn.foodshare.config.SecurityConfiguration;
import com.datn.foodshare.controller.PaymentController;
import com.datn.foodshare.repository.UserRepository;
import com.datn.foodshare.service.CustomUserDetailsService;
import com.datn.foodshare.service.payment.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class, RateLimitFilter.class,
        CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
class PaymentAuthorizationMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;
    @MockitoBean
    private UserRepository userRepository;
    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void recipientCannotRefundPayment() throws Exception {
        mockMvc.perform(patch("/api/payments/7/refund").with(user("1").roles("RECIPIENT")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService);
    }

    @Test
    void adminCanRefundPayment() throws Exception {
        mockMvc.perform(patch("/api/payments/7/refund").with(user("1").roles("ADMIN")))
                .andExpect(status().isOk());

        verify(paymentService).refundPayment(7L);
    }
}
