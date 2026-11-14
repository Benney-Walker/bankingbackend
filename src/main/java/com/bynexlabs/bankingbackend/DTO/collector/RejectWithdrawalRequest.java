package com.bynexlabs.bankingbackend.DTO.collector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectWithdrawalRequest {

    @NotBlank(message = "A reason is required")
    @Size(max = 1000, message = "Reason must be at most 1000 characters")
    private String reason;
}
