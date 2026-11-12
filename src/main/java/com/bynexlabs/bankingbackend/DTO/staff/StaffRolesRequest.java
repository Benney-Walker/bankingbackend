package com.bynexlabs.bankingbackend.DTO.staff;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The complete set of roles a staff member should have after the update. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StaffRolesRequest {

    @NotEmpty(message = "At least one role must be selected")
    private List<String> roles;
}
