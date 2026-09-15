package com.datn.foodshare.service;

import com.datn.foodshare.util.error.BusinessException;
import com.datn.foodshare.util.error.ExternalServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.net.http.HttpClient;
import java.time.Duration;

@Component
public class InfobipOtpClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String applicationId;
    private final String messageId;
    private final String sender;

    public InfobipOtpClient(
            @Value("${infobip.base-url:}") String baseUrl,
            @Value("${infobip.api-key:}") String apiKey,
            @Value("${infobip.application-id:}") String applicationId,
            @Value("${infobip.message-id:}") String messageId,
            @Value("${infobip.sender:}") String sender,
            @Value("${infobip.timeout-ms:5000}") int timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl == null || baseUrl.isBlank() ? "https://api.infobip.com" : baseUrl)
                .requestFactory(requestFactory)
                .build();
        this.apiKey = apiKey;
        this.applicationId = applicationId;
        this.messageId = messageId;
        this.sender = sender;
    }

    public String sendPin(String internationalPhone) {
        requireConfigured();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("applicationId", applicationId);
        body.put("messageId", messageId);
        if (sender != null && !sender.isBlank()) body.put("from", sender);
        body.put("to", "84383870916");

        try {
            SendPinResponse response = restClient.post()
                    .uri("/2fa/2/pin")
                    .header(HttpHeaders.AUTHORIZATION, "App " + apiKey)
                    .body(body)
                    .retrieve()
                    .body(SendPinResponse.class);
            String pinId = response == null ? null : response.pinId();
            if (pinId == null || pinId.isBlank()) {
                throw new ExternalServiceException("Infobip không trả về mã phiên OTP");
            }
            return pinId;
        } catch (HttpClientErrorException.TooManyRequests ex) {
            throw new BusinessException("Bạn đã yêu cầu OTP quá nhiều lần. Vui lòng thử lại sau");
        } catch (HttpClientErrorException ex) {
            throw new ExternalServiceException("Infobip từ chối yêu cầu gửi OTP");
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Không thể kết nối dịch vụ gửi OTP");
        }
    }

    public boolean verifyPin(String pinId, String otp) {
        requireConfigured();
        try {
            VerifyPinResponse response = restClient.post()
                    .uri("/2fa/2/pin/{pinId}/verify", pinId)
                    .header(HttpHeaders.AUTHORIZATION, "App " + apiKey)
                    .body(Map.of("pin", otp))
                    .retrieve()
                    .body(VerifyPinResponse.class);
            return response != null && response.verified();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new BusinessException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng gửi mã mới");
            }
            return false;
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Không thể kết nối dịch vụ xác minh OTP");
        }
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()
                || applicationId == null || applicationId.isBlank()
                || messageId == null || messageId.isBlank()) {
            throw new ExternalServiceException("Dịch vụ OTP chưa được cấu hình");
        }
    }

    record SendPinResponse(String pinId) {
    }

    record VerifyPinResponse(boolean verified) {
    }
}
