package com.datn.foodshare.util.error;

import com.datn.foodshare.domain.response.RestResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.OptimisticLockException;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String SYSTEM_ERROR_MESSAGE = "Lỗi hệ thống, xin lỗi vì sự bất tiện này.";

    @ExceptionHandler(value = {
            ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class
    })
    public ResponseEntity<RestResponse<Object>> handleOptimisticLockingException(Exception ex) {
        log.warn("Optimistic locking conflict: {}", ex.getMessage());
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.CONFLICT.value());
        res.setError("Conflict");
        res.setMessage("Số lượng thực phẩm vừa được cập nhật bởi một yêu cầu khác. Vui lòng làm mới và thử lại.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(res);
    }

    @ExceptionHandler(value = {
            IdInvalidException.class,
            BusinessException.class,
            StorageException.class
    })
    public ResponseEntity<RestResponse<Object>> handleBusinessException(Exception ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("Business Exception");
        res.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(value = {
            PermissionException.class,
            AccessDeniedException.class
    })
    public ResponseEntity<RestResponse<Object>> handleAccessDeniedException(Exception ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.FORBIDDEN.value());
        res.setError("Forbidden");
        res.setMessage(ex.getMessage() != null ? ex.getMessage() : "Bạn không có quyền truy cập tài nguyên này");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(res);
    }

    @ExceptionHandler(value = {
            BadCredentialsException.class,
            UsernameNotFoundException.class,
            AuthenticationException.class
    })
    public ResponseEntity<RestResponse<Object>> handleAuthException(Exception ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.UNAUTHORIZED.value());
        res.setError("Unauthorized");
        
        String message = ex.getMessage();
        if (message != null && message.equals("Bad credentials")) {
            message = "Tài khoản hoặc mật khẩu không chính xác";
        } else if (message == null) {
            message = "Thông tin đăng nhập không chính xác";
        }
        
        res.setMessage(message);
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(res);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestResponse<Object>> handleValidationException(MethodArgumentNotValidException ex) {
        BindingResult result = ex.getBindingResult();
        List<FieldError> fieldErrors = result.getFieldErrors();

        Map<String, List<FieldError>> errorsByField = fieldErrors.stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<String> errors = new ArrayList<>();

        for (List<FieldError> errorsForField : errorsByField.values()) {
            if (errorsForField.isEmpty()) {
                continue;
            }
            FieldError selected = errorsForField.stream()
                    .filter(err -> {
                        String code = err.getCode();
                        return "NotBlank".equals(code) || "NotEmpty".equals(code) || "NotNull".equals(code);
                    })
                    .findFirst()
                    .orElse(errorsForField.get(0));

            String msg = selected.getDefaultMessage();
            if (msg != null && !msg.isBlank()) {
                errors.add(msg);
            }
        }

        for (ObjectError globalError : result.getGlobalErrors()) {
            String msg = globalError.getDefaultMessage();
            if (msg != null && !msg.isBlank()) {
                errors.add(msg);
            }
        }

        errors = errors.stream().distinct().toList();

        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("Validation Error");
        res.setMessage(errors.size() > 1 ? errors : (errors.isEmpty() ? "Dữ liệu không hợp lệ" : errors.get(0)));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RestResponse<Object>> handleNoResourceFound(NoResourceFoundException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.NOT_FOUND.value());
        res.setError("404 Not Found");
        res.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(res);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<RestResponse<Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        res.setError("Method Not Allowed");
        res.setMessage(ex.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(res);
    }

    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<RestResponse<Object>> handleExternalServiceException(ExternalServiceException ex) {
        log.error("External service failure", ex);
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.SERVICE_UNAVAILABLE.value());
        res.setError("Service Unavailable");
        res.setMessage(SYSTEM_ERROR_MESSAGE);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(res);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<RestResponse<Object>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.BAD_REQUEST.value());
        res.setError("Bad Request");
        res.setMessage("Tham số yêu cầu không hợp lệ: " + ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(res);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestResponse<Object>> handleAllExceptions(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR.value());
        res.setError("Internal Server Error");
        res.setMessage(SYSTEM_ERROR_MESSAGE);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
    }
}
