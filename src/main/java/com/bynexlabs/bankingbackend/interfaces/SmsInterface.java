package com.bynexlabs.bankingbackend.interfaces;

import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;

import java.util.List;

public interface SmsInterface {
    SmsResponse sendBulkSms(String message, List<String> recipient);
    SmsResponse sendSms(String message, String recipient);
}
