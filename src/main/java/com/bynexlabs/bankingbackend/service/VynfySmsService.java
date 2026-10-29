package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.DTO.sms.VynfySmsPayload;
import com.bynexlabs.bankingbackend.DTO.sms.VynfySmsResponse;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
public class VynfySmsService implements SmsInterface {

    @Value("${vynfy.sms.sender.id}")
    private String VYNFY_SENDER_ID;

    @Value("${vynfy.sms.api.key}")
    private String API_KEY;

    @Value("${vynfy.sms.url}")
    private String VYNFY_SMS_URL;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public SmsResponse sendBulkSms(String message, List<String> recipients) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", API_KEY);

        VynfySmsPayload payload = VynfySmsPayload.builder()
                .recipients(recipients)
                .message(message)
                .sender(VYNFY_SENDER_ID)
                .metaData(null)
                .build();

        HttpEntity<VynfySmsPayload> request = new HttpEntity<>(payload, headers);

        SmsResponse smsResponse;
        try {
            ResponseEntity<VynfySmsResponse> response = restTemplate.postForEntity(
                    VYNFY_SMS_URL, request, VynfySmsResponse.class
            );

            VynfySmsResponse vynfyResponse = response.getBody();
            smsResponse = SmsResponse.builder()
                    .success(vynfyResponse.isSuccess())
                    .audienceCount(vynfyResponse.getData().getRecipientsCount())
                    .smsCost(vynfyResponse.getBalance().getDeducted())
                    .build();
            return smsResponse;

        } catch (Exception e) {
            //log.error("Could not send bulk sms messages: " + e.getMessage());
            smsResponse = SmsResponse.builder()
                    .success(false)
                    .build();
            return smsResponse;
        }
    }

    @Override
    public SmsResponse sendSms(String message, String recipient) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", API_KEY);

        VynfySmsPayload payload = VynfySmsPayload.builder()
                .recipients(Collections.singletonList(recipient))
                .message(message)
                .sender(VYNFY_SENDER_ID)
                .metaData(null)
                .build();

        HttpEntity<VynfySmsPayload> request = new HttpEntity<>(payload, headers);

        SmsResponse smsResponse;
        try {
            ResponseEntity<VynfySmsResponse> response = restTemplate.postForEntity(
                    VYNFY_SMS_URL, request, VynfySmsResponse.class
            );

            VynfySmsResponse vynfyResponse = response.getBody();
            smsResponse = SmsResponse.builder()
                    .success(vynfyResponse.isSuccess())
                    .audienceCount(vynfyResponse.getData().getRecipientsCount())
                    .smsCost(vynfyResponse.getBalance().getDeducted())
                    .build();
            return smsResponse;

        } catch (Exception e) {
            //log.error("Could not send sms message: " + e.getMessage());
            smsResponse = SmsResponse.builder()
                    .success(false)
                    .build();
            return smsResponse;
        }
    }
}
