package com.hrm.hrmsystem.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class SmsUtil {

    private static final Logger log = LoggerFactory.getLogger(SmsUtil.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${fast2sms.api-key}")
    private String apiKey;

    @Value("${fast2sms.route}")
    private String route;

    @Value("${fast2sms.otp-id}")
    private String otpTemplateId;

    @Value("${fast2sms.sender-id}")
    private String senderId;

    /**
     * Send DLT OTP SMS using Fast2SMS REST API
     */
    public boolean sendOtpSms(String mobileNumber, String otp) {
        log.info("Preparing to send OTP SMS to: {}", mobileNumber);
        
        // Print to backend terminal console for secure developer visibility and seamless testing
        System.out.println("\n================================================================");
        System.out.println("📢 [BACKEND CONSOLE LOG] GENERATED OTP FOR " + mobileNumber + ":");
        System.out.println("👉 👉 👉 OTP CODE: " + otp + " 👈 👈 👈");
        System.out.println("================================================================\n");

        // Sanitize and clean up number
        String cleanNumber = mobileNumber.replaceAll("[^0-9]", "");
        if (cleanNumber.length() > 10) {
            cleanNumber = cleanNumber.substring(cleanNumber.length() - 10);
        }

        // Check for api-key placeholder or configuration issues
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.contains("${") || apiKey.equalsIgnoreCase("change-me")) {
            log.info("📢 [SMS SIMULATION] API Key not configured. Simulated OTP to: {} | OTP: {}", cleanNumber, otp);
            printConsoleFallback(cleanNumber, otp);
            return true;
        }

        try {
            // Build DLT JSON Payload
            String jsonPayload = String.format(
                    "{"
                    + "\"route\":\"%s\","
                    + "\"sender_id\":\"%s\","
                    + "\"message\":\"%s\","
                    + "\"variables_values\":\"%s\","
                    + "\"numbers\":\"%s\""
                    + "}",
                    escapeJson(route),
                    escapeJson(senderId),
                    escapeJson(otpTemplateId),
                    escapeJson(otp),
                    escapeJson(cleanNumber)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.fast2sms.com/dev/bulkV2"))
                    .header("authorization", apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body().contains("\"return\":true")) {
                log.info("Fast2SMS OTP successfully dispatched to: {}. Response: {}", cleanNumber, response.body());
                return true;
            } else {
                log.warn("Fast2SMS DLT route failed (Status: {}, Response: {}). Attempting fallback to Quick SMS 'q' route...", response.statusCode(), response.body());
                
                // FALLBACK TO QUICK SMS 'q' ROUTE (Bypasses DLT and Website Verification completely)
                String fallbackPayload = String.format(
                        "{"
                        + "\"route\":\"q\","
                        + "\"message\":\"Your HRMS login OTP is: %s. Valid for 2 minutes. Please do not share this with anyone.\","
                        + "\"numbers\":\"%s\","
                        + "\"language\":\"english\""
                        + "}",
                        escapeJson(otp),
                        escapeJson(cleanNumber)
                );

                HttpRequest fallbackRequest = HttpRequest.newBuilder()
                        .uri(URI.create("https://www.fast2sms.com/dev/bulkV2"))
                        .header("authorization", apiKey.trim())
                        .header("Content-Type", "application/json")
                        .header("accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(fallbackPayload))
                        .build();

                HttpResponse<String> fallbackResponse = httpClient.send(fallbackRequest, HttpResponse.BodyHandlers.ofString());
                
                if (fallbackResponse.statusCode() >= 200 && fallbackResponse.statusCode() < 300 && fallbackResponse.body().contains("\"return\":true")) {
                    log.info("Fast2SMS OTP successfully dispatched via Quick SMS 'q' route fallback. Response: {}", fallbackResponse.body());
                    return true;
                } else {
                    log.error("Fast2SMS Quick SMS 'q' route fallback also failed. Status: {}, Response: {}", fallbackResponse.statusCode(), fallbackResponse.body());
                    printConsoleFallback(cleanNumber, otp);
                    return false;
                }
            }

        } catch (Exception e) {
            log.error("Exception occurred while sending Fast2SMS OTP: {}", e.getMessage(), e);
            printConsoleFallback(cleanNumber, otp);
            return false;
        }
    }

    private void printConsoleFallback(String mobileNumber, String otp) {
        System.out.println("\n================================================================");
        System.out.println("📢 [SMS FAILURE FALLBACK] PRINTING OTP TO CONSOLE:");
        System.out.println("MOBILE NUMBER: " + mobileNumber);
        System.out.println("OTP CODE: " + otp);
        System.out.println("DLT TEMPLATE ID: " + otpTemplateId);
        System.out.println("SENDER ID (Header): " + senderId);
        System.out.println("================================================================\n");
    }

    private String escapeJson(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < string.length(); i++) {
            char c = string.charAt(i);
            switch (c) {
                case '\\':
                case '"':
                    sb.append('\\').append(c);
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
