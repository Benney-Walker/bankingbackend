package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepo staffRepo;

    private final StaffRolesRepo staffRolesRepo;

    private final IdGenerator idGenerator;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    public Staff getStaffDetails(String staffId){
        return staffRepo.findByStaffId(staffId).orElse(null);
    }

    @Transactional
    public ResponseEntity<?> addNewStaff(StaffDetails staffDetails){
        LocalDate dob = LocalDate.parse(staffDetails.getDateOfBirth(), DateTimeFormatter.ISO_DATE);

        if (
                staffRepo.existsByFirstNameAndLastNameAndDateOfBirth(
                        staffDetails.getFirstName(), staffDetails.getLastName(), dob
                )
        ) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Staff already exist"
            ));
        }

        if (!staffDetails.getPhoneNumber().startsWith("0") &&
        staffDetails.getPhoneNumber().length() != 10) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Invalid phone number"
            ));
        }

        if (staffRepo.existsByEmail(staffDetails.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "message", "Email already exist"
            ));
        }

        String newStaffId = idGenerator.generateId(Id_Type.STAFF_ID);
        String hashedPassword = bCryptPasswordEncoder.encode(staffDetails.getPassword());

        Staff staff = Staff.builder()
                .staffId(newStaffId)
                .firstName(staffDetails.getFirstName())
                .middleName(staffDetails.getMiddleName() == null ? " " :staffDetails.getMiddleName())
                .lastName(staffDetails.getLastName())
                .dateOfBirth(dob)
                .phoneNumber(staffDetails.getPhoneNumber())
                .email(staffDetails.getEmail())
                .password(hashedPassword)
                .residentialAddress(staffDetails.getResidentialAddress())
                .staffStatus(StaffStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now()).build();
        staffRepo.save(staff);

        saveStaffRoles(staff, staffDetails.getRoles());

        //Asynchronous task will be here later
        return ResponseEntity.ok().build();
    }

    private void saveStaffRoles(Staff staff, List<String> staffRoles) {

        try {
            List<StaffRoles> roles = new ArrayList<>();
            for (String staffRole : staffRoles) {
                StaffRoles newRole = new StaffRoles();
                newRole.setStaff(staff);
                newRole.setRole(Roles.valueOf(staffRole.toUpperCase()));
                roles.add(newRole);
            }
            staffRolesRepo.saveAll(roles);

        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Error: " + e.getMessage());
        }
    }
}
