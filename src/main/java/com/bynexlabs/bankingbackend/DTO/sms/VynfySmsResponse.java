package com.bynexlabs.bankingbackend.DTO.sms;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class VynfySmsResponse {

    private ResponseBalance balance;

    private ResponseData data;

    private boolean success;
}
