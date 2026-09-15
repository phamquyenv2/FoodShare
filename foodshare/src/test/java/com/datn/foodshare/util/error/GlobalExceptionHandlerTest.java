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

    public void dummyMethod(SendPhoneOtpRequest req) {}
}
