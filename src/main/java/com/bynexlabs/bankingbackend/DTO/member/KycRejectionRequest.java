package com.bynexlabs.bankingbackend.DTO.member;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of a manual KYC rejection. The reason is stored on the KYC record so the staff member who
 * re-uploads knows what to fix.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class KycRejectionRequest {

    @NotBlank(message = "A rejection reason is required")
    private String reason;
}
