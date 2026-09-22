package com.datn.foodshare.util.error;

import com.datn.foodshare.domain.request.SendPhoneOtpRequest;
import com.datn.foodshare.domain.response.RestResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void testHandleValidationException_prioritizesNotBlankOverPatternForSameField() throws Exception {
        SendPhoneOtpRequest target = new SendPhoneOtpRequest("");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "sendPhoneOtpRequest");
        
        bindingResult.addError(new FieldError("sendPhoneOtpRequest", "phone", "", false, new String[]{"NotBlank"}, null, "Số điện thoại không được để trống"));
        bindingResult.addError(new FieldError("sendPhoneOtpRequest", "phone", "", false, new String[]{"Pattern"}, null, "Số điện thoại không đúng định dạng"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", SendPhoneOtpRequest.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<RestResponse<Object>> response = handler.handleValidationException(ex);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Số điện thoại không được để trống", response.getBody().getMessage());
    }

    @Test
    void testHandleValidationException_returnsPatternWhenFieldIsInvalidFormat() throws Exception {
        SendPhoneOtpRequest target = new SendPhoneOtpRequest("123");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "sendPhoneOtpRequest");
        bindingResult.addError(new FieldError("sendPhoneOtpRequest", "phone", "123", false, new String[]{"Pattern"}, null, "Số điện thoại không đúng định dạng"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", SendPhoneOtpRequest.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<RestResponse<Object>> response = handler.handleValidationException(ex);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Số điện thoại không đúng định dạng", response.getBody().getMessage());
    }

    @Test
    void testHandleValidationException_returnsMultipleErrorsForDifferentFields() throws Exception {
        SendPhoneOtpRequest target = new SendPhoneOtpRequest("");
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "request");
        bindingResult.addError(new FieldError("request", "phone", "", false, new String[]{"NotBlank"}, null, "Số điện thoại không được để trống"));
        bindingResult.addError(new FieldError("request", "password", "", false, new String[]{"NotBlank"}, null, "Mật khẩu không được để trống"));

        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", SendPhoneOtpRequest.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<RestResponse<Object>> response = handler.handleValidationException(ex);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getMessage() instanceof List);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) response.getBody().getMessage();
        assertEquals(2, list.size());
        assertEquals("Số điện thoại không được để trống", list.get(0));
        assertEquals("Mật khẩu không được để trống", list.get(1));
    }

    @Test
    void systemExceptionDoesNotExposeInternalDetails() {
        var response = handler.handleAllExceptions(new IllegalArgumentException(
                "No enum constant com.datn.foodshare.util.constant.OrderStatus.INVALID"));
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        String message = assertInstanceOf(String.class, response.getBody().getMessage());
        assertFalse(message.isBlank());
        assertFalse(message.contains("No enum constant"));
        assertFalse(message.contains("OrderStatus.INVALID"));
    }

    @Test
    void externalServiceExceptionDoesNotExposeInternalDetails() {
        var response = handler.handleExternalServiceException(new ExternalServiceException("Internal provider details"));
        assertEquals(503, response.getStatusCode().value());
        assertNotNull(response.getBody());
        String message = assertInstanceOf(String.class, response.getBody().getMessage());
        assertFalse(message.isBlank());
        assertFalse(message.contains("Internal provider details"));
    }

    @Test
    void optimisticLockingExceptionReturnsConflict() {
        var res1 = handler.handleOptimisticLockingException(new jakarta.persistence.OptimisticLockException("conflict"));
        assertEquals(409, res1.getStatusCode().value());
        assertEquals("Conflict", res1.getBody().getError());

        var res2 = handler.handleOptimisticLockingException(new org.springframework.orm.ObjectOptimisticLockingFailureException("entity", 1L));
        assertEquals(409, res2.getStatusCode().value());
    }

    @Test
    void businessExceptionReturnsBadRequest() {
        var res1 = handler.handleBusinessException(new BusinessException("Business error"));
        assertEquals(400, res1.getStatusCode().value());
        assertEquals("Business error", res1.getBody().getMessage());

        var res2 = handler.handleBusinessException(new IdInvalidException("Invalid ID"));
        assertEquals(400, res2.getStatusCode().value());

        var res3 = handler.handleBusinessException(new StorageException("Storage error"));
        assertEquals(400, res3.getStatusCode().value());
    }

    @Test
    void accessDeniedExceptionReturnsForbidden() {
        var res1 = handler.handleAccessDeniedException(new PermissionException("No permission"));
        assertEquals(403, res1.getStatusCode().value());
        assertEquals("No permission", res1.getBody().getMessage());

        var res2 = handler.handleAccessDeniedException(new org.springframework.security.access.AccessDeniedException("Access denied"));
        assertEquals(403, res2.getStatusCode().value());
    }

    @Test
    void authExceptionReturnsUnauthorized() {
        var res1 = handler.handleAuthException(new org.springframework.security.authentication.BadCredentialsException("Bad credentials"));
        assertEquals(401, res1.getStatusCode().value());
        assertEquals("Tài khoản hoặc mật khẩu không chính xác", res1.getBody().getMessage());

        var res2 = handler.handleAuthException(new org.springframework.security.core.userdetails.UsernameNotFoundException("User not found"));
        assertEquals(401, res2.getStatusCode().value());
        assertEquals("User not found", res2.getBody().getMessage());

        var res3 = handler.handleAuthException(new org.springframework.security.authentication.BadCredentialsException(null));
        assertEquals(401, res3.getStatusCode().value());
        assertEquals("Thông tin đăng nhập không chính xác", res3.getBody().getMessage());
    }

    @Test
    void noResourceFoundExceptionReturnsNotFound() {
        var ex = mock(org.springframework.web.servlet.resource.NoResourceFoundException.class);
        when(ex.getMessage()).thenReturn("Resource not found");
        var res = handler.handleNoResourceFound(ex);
        assertEquals(404, res.getStatusCode().value());
        assertEquals("404 Not Found", res.getBody().getError());
        assertEquals("Resource not found", res.getBody().getMessage());
    }

    @Test
    void methodNotSupportedReturnsMethodNotAllowed() {
        var res = handler.handleMethodNotSupported(new org.springframework.web.HttpRequestMethodNotSupportedException("POST"));
        assertEquals(405, res.getStatusCode().value());
        assertEquals("Method Not Allowed", res.getBody().getError());
    }

    @Test
    void methodArgumentTypeMismatchReturnsBadRequest() {
        var ex = mock(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("orderId");
        var res = handler.handleMethodArgumentTypeMismatch(ex);
        assertEquals(400, res.getStatusCode().value());
        assertTrue(res.getBody().getMessage().toString().contains("orderId"));
    }

    public void dummyMethod(SendPhoneOtpRequest req) {}
}
