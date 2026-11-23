package com.bynexlabs.bankingbackend.DTO.location;

/** How many points were stored, and how many were already stored (a re-sent batch). */
public record LocationBatchResponse(int stored, int duplicates) {
}
