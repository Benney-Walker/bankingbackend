package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private StaffRepo staffRepo;

    @InjectMocks
    private CustomUserDetailsService userDetailsService;

    private Staff staff(StaffStatus status, Roles... roles) {
        Staff staff = Staff.builder()
                .staffId("900100")
                .password("$2a$10$hash")
                .staffStatus(status)
                .build();
        List<StaffRoles> staffRoles = new ArrayList<>();
        for (Roles role : roles) {
            StaffRoles staffRole = new StaffRoles();
            staffRole.setStaff(staff);
            staffRole.setRole(role);
            staffRoles.add(staffRole);
        }
        staff.setRoles(staffRoles);
        return staff;
    }

    @Test
    void authoritiesAreRoleNames() {
        when(staffRepo.findByStaffId("900100"))
                .thenReturn(Optional.of(staff(StaffStatus.ACTIVE, Roles.ADMINISTRATOR, Roles.AGENT)));

        UserDetails user = userDetailsService.loadUserByUsername("900100");

        assertThat(user.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ADMINISTRATOR", "AGENT");
        assertThat(user.isEnabled()).isTrue();
    }

    @Test
    void inactiveStaffIsDisabled() {
        when(staffRepo.findByStaffId("900100"))
                .thenReturn(Optional.of(staff(StaffStatus.INACTIVE, Roles.COLLECTOR)));

        UserDetails user = userDetailsService.loadUserByUsername("900100");

        assertThat(user.isEnabled()).isFalse();
    }
}
