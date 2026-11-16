package com.bynexlabs.bankingbackend.DTO.branch;

import com.bynexlabs.bankingbackend.models.Branch;

import java.util.UUID;

/** A branch as listed for add-staff and member registration (desktop spec section 16). */
public record BranchResponse(UUID id, String name, String location) {

    public static BranchResponse from(Branch branch) {
        return new BranchResponse(branch.getId(), branch.getName(), branch.getLocation());
    }
}
