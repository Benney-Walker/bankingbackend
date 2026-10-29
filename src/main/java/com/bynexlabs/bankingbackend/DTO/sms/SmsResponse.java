package com.bynexlabs.bankingbackend.DTO.sms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SmsResponse {

    private int audienceCount;

    private float smsCost;

    private boolean success;
}
