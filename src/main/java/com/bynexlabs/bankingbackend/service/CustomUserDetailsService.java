package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final StaffRepo staffRepo;

    @Override
    public UserDetails loadUserByUsername(String staffId) throws UsernameNotFoundException {
        Staff staff = staffRepo.findByStaffId(staffId)
                .orElseThrow(() -> new UsernameNotFoundException("Staff not found"));

        List<SimpleGrantedAuthority> authorities = staff.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getRole().name()))
                .toList();

        // A disabled user makes Spring reject the login with DisabledException, which
        // GlobalExceptionHandler answers exactly like a wrong password.
        return User.withUsername(staff.getStaffId())
                .password(staff.getPassword())
                .authorities(authorities)
                .disabled(staff.getStaffStatus() != StaffStatus.ACTIVE)
                .build();
    }
}
