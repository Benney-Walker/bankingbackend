package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.service.StaffService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffControllerTest {

    @Mock
    private StaffService staffService;

    @Mock
    private AuthenticatedStaffProvider staffProvider;

    @InjectMocks
    private StaffController controller;

    // Owner decision 2026-11-23: the signed-in staff member's own profile.
    @Test
    void meReturnsTheSignedInStaffMembersProfile() {
        StaffResponse profile = StaffResponse.builder().staffId("900105").branchId(UUID.randomUUID()).branchName("Tarkwa").build();
        when(staffProvider.getStaffId()).thenReturn("900105");
        when(staffService.getStaff("900105")).thenReturn(profile);

        assertThat(controller.getOwnProfile()).isSameAs(profile);
    }
}
