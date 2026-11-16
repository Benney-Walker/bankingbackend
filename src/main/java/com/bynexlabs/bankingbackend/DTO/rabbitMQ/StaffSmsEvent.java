package com.bynexlabs.bankingbackend.DTO.rabbitMQ;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The welcome SMS to a new staff member with their staff ID (owner decision 2026-11-15), queued
 * when the staff member is saved and sent once that has committed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StaffSmsEvent {

    private String staffId;

    /** The staff member's phone in the stored local format (0XXXXXXXXX); VynfySmsService adds +233. */
    private String phoneNumber;

    private String message;
}
