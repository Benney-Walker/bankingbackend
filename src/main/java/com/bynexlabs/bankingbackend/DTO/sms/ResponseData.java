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
public class ResponseData {

    @JsonProperty("recipients_count")
    private int recipientsCount;

    private String queued;

    @JsonProperty("job_id")
    private String jobId;
}
