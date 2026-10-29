package com.bynexlabs.bankingbackend.DTO.sms;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PayloadMetaData {

    @JsonProperty("campaign_id")
    private String campaignId;

    @JsonProperty("customer_segment")
    private String customerSegment;

    @JsonProperty("order_id")
    private String orderId;
}
