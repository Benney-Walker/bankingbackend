package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // Validate the input before touching the database or consuming a staff ID.
        String phoneNumber = staffDetails.getPhoneNumber();
        if (!phoneNumber.startsWith("0") || phoneNumber.length() != 10) {
            throw new BadRequestException("Invalid phone number");
        }
        List<Roles> roles = parseRoles(staffDetails.getRoles());

        if (
                staffRepo.existsByFirstNameAndLastNameAndDateOfBirth(
                        staffDetails.getFirstName(), staffDetails.getLastName(), dob
                )
        ) {
            throw new ConflictException("Staff already exist");
        }

        if (staffRepo.existsByPhoneNumber(staffDetails.getPhoneNumber())) {
            throw new ConflictException("Phone number already exist");
        }

        if (staffRepo.existsByEmail(staffDetails.getEmail())) {
            throw new ConflictException("Email already exist");
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

        saveStaffRoles(staff, roles);

        //Asynchronous task will be here later
        return ResponseEntity.ok().build();
    }

    private List<Roles> parseRoles(List<String> requestedRoles) {
        List<Roles> roles = new ArrayList<>();
        for (String requested : requestedRoles) {
            try {
                roles.add(Roles.valueOf(requested.toUpperCase()));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("Invalid role: " + requested
                        + ". Allowed roles: " + Arrays.toString(Roles.values()));
            }
        }
        return roles;
    }

    private void saveStaffRoles(Staff staff, List<Roles> roles) {
        List<StaffRoles> staffRoles = new ArrayList<>();
        for (Roles role : roles) {
            StaffRoles newRole = new StaffRoles();
            newRole.setStaff(staff);
            newRole.setRole(role);
            staffRoles.add(newRole);
        }
        staffRolesRepo.saveAll(staffRoles);
    }
}
