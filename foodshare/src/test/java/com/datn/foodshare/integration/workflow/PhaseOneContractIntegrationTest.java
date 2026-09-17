package com.datn.foodshare.integration.workflow;

import com.datn.foodshare.domain.entity.*;
import com.datn.foodshare.integration.IntegrationTestSupport;
import com.datn.foodshare.repository.OrderRepository;
import com.datn.foodshare.repository.PaymentRepository;
import com.datn.foodshare.security.JwtTokenProvider;
import com.datn.foodshare.util.constant.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PhaseOneContractIntegrationTest extends IntegrationTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RECIPIENT", "SUPPLIER", "ORGANIZATION"})
    void newAccountCompletesProfileWithRoleSpecificPayload(Role role) throws Exception {
        String phone = "09" + String.format("%08d",
                Integer.toUnsignedLong(UUID.randomUUID().hashCode()) % 100_000_000L);
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "phone", phone,
                                "registrationToken", jwtTokenProvider.createPhoneRegistrationToken(phone),
                                "password", "StrongPassword123",
                                "fullName", "Contract User", "role", role.name()))))
                .andExpect(status().isCreated()).andReturn();
        String token = "Bearer " + objectMapper.readTree(registered.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        Map<String, Object> payload = new HashMap<>(Map.of(
                "phone", phone, "specificAddress", "123 Contract Street",
                "latitude", 21.0285, "longitude", 105.8542, "role", role.name()));
        if (role != Role.RECIPIENT) {
            payload.put("name", "Contract User");
            payload.put("licenseUrls", List.of(
                    "https://res.cloudinary.com/demo/image/upload/licenses/contract.jpg"));
        }
        if (role == Role.SUPPLIER) payload.put("supplierType", "INDIVIDUAL");
        if (role == Role.ORGANIZATION) payload.put("organizationType", "OTHER");

        mockMvc.perform(put("/api/users/me/profile").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType("application/json").content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileCompleted").value(true))
                .andExpect(jsonPath("$.data.role").value(role.name()));
        User saved = userRepository.findByPhone(phone).orElseThrow();
        assertThat(saved.isProfileCompleted()).isTrue();
        var profile = businessProfileRepository.findByUserId(saved.getId());
        if (role == Role.RECIPIENT) assertThat(profile).isEmpty();
        else assertThat(profile).get().satisfies(p ->
                assertThat(p.getProfileType().name()).isEqualTo(role.name()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void organizationBatchReceivesBothItemsAndGroupsBySupplier(boolean separateSuppliers) throws Exception {
        User organization = createUser(Role.ORGANIZATION, true);
        BusinessProfile firstSupplier = createSupplierProfile(createUser(Role.SUPPLIER, true));
        BusinessProfile secondSupplier = separateSuppliers
                ? createSupplierProfile(createUser(Role.SUPPLIER, true)) : firstSupplier;
        Category category = createCategory();
        FoodPost first = createAvailablePost(firstSupplier, category, 5);
        FoodPost second = createAvailablePost(secondSupplier, category, 5);
        MvcResult result = mockMvc.perform(post("/api/orders/batch")
                        .header(HttpHeaders.AUTHORIZATION, bearer(organization))
                        .contentType("application/json").content("""
                                {"orders":[{"foodPostId":%d,"quantity":1},{"foodPostId":%d,"quantity":2}]}
                                """.formatted(first.getId(), second.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.length()").value(separateSuppliers ? 2 : 1))
                .andReturn();
        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        int details = 0;
        int quantity = 0;
        for (var order : data) for (var detail : order.path("orderDetails")) {
            details++;
            quantity += detail.path("quantity").asInt();
        }
        assertThat(details).isEqualTo(2);
        assertThat(quantity).isEqualTo(3);
        assertThat(foodPostRepository.findById(first.getId()).orElseThrow().getAvailableQuantity()).isEqualTo(4);
        assertThat(foodPostRepository.findById(second.getId()).orElseThrow().getAvailableQuantity()).isEqualTo(3);
    }

    @ParameterizedTest
    @EnumSource(PaymentMethod.class)
    void paymentCreationAndConfirmationAreReflectedInOrderResponses(PaymentMethod method) throws Exception {
        User receiver = createUser(Role.RECIPIENT, true);
        User supplier = createUser(Role.SUPPLIER, true);
        Order order = orderRepository.saveAndFlush(Order.builder()
                .orderCode("P1-" + UUID.randomUUID().toString().substring(0, 20))
                .receiver(receiver).businessProfile(createSupplierProfile(supplier))
                .orderStatus(OrderStatus.ACCEPTED).totalAmount(new BigDecimal("10000")).build());
        // No payment is represented by null, not a fabricated domain status.
        assertStatus(order, receiver, null);
        MvcResult created = mockMvc.perform(post("/api/payments/order/{id}", order.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiver))
                        .contentType("application/json").content("{\"method\":\"" + method.name() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value(method == PaymentMethod.CASH ? "PENDING" : "PROCESSING"))
                .andReturn();
        long paymentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertStatus(order, receiver, method == PaymentMethod.CASH ? "PENDING" : "PROCESSING");
        mockMvc.perform(patch("/api/payments/{id}/success", paymentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isOk());
        assertStatus(order, receiver, "SUCCESS");
        mockMvc.perform(get("/api/orders/my").header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].paymentStatus").value("SUCCESS"));
        mockMvc.perform(get("/api/orders/supplier").header(HttpHeaders.AUTHORIZATION, bearer(supplier)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].paymentStatus").value("SUCCESS"));
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getPaymentStatus())
                .isEqualTo(TransactionStatus.SUCCESS);
    }

    @Test
    void adminCanLockAndUnlockAccountThroughStatusEndpoint() throws Exception {
        User admin = createUser(Role.ADMIN, true);
        User target = createUser(Role.RECIPIENT, true);
        for (boolean active : new boolean[]{false, true}) {
            mockMvc.perform(patch("/api/admin/users/{id}/status", target.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                            .contentType("application/json").content("{\"active\":" + active + "}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.active").value(active));
            assertThat(userRepository.findById(target.getId()).orElseThrow().isActive()).isEqualTo(active);
        }
    }

    private void assertStatus(Order order, User receiver, String expected) throws Exception {
        mockMvc.perform(get("/api/orders/{id}", order.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(receiver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value(expected));
    }
}
