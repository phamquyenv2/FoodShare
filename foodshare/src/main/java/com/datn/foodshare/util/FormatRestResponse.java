package com.datn.foodshare.util;

import com.datn.foodshare.domain.response.RestResponse;
import com.datn.foodshare.util.annotation.ApiMessage;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class FormatRestResponse implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        HttpServletResponse servletResponse = ((ServletServerHttpResponse) response).getServletResponse();
        int status = servletResponse.getStatus();

        String path = request.getURI().getPath();
        if (path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui") || path.equals("/swagger-ui.html")
                || path.startsWith("/actuator")) {
            return body;
        }

        if (body instanceof RestResponse || body instanceof String || body instanceof Resource || body instanceof byte[]) {
            return body;
        }

        RestResponse<Object> res = new RestResponse<>();
        res.setStatusCode(status);

        if (status >= 400) {
            return body;
        }

        ApiMessage messageAnnotation = returnType.getMethodAnnotation(ApiMessage.class);
        res.setMessage(messageAnnotation != null ? messageAnnotation.value() : "CALL API SUCCESS");
        res.setData(body);

        return res;
    }
}
