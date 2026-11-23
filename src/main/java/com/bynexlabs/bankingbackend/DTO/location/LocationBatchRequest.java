package com.bynexlabs.bankingbackend.DTO.location;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One or more points; the app sends its offline backlog in batches of up to 500. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationBatchRequest {

    @NotEmpty(message = "points can't be empty")
    @Size(max = 500, message = "Send at most 500 points at a time")
    private List<@Valid LocationPointRequest> points;
}
