package com.bynexlabs.bankingbackend.DTO.sms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VynfySmsPayload {

    private String message;

    @JsonProperty("metadata")
    private PayloadMetaData metaData;

    private List<String> recipients;

    private String sender;
}
