package com.bynexlabs.bankingbackend.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "staffs")
public class Staffs {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "staff_id", length = 50, nullable = false, unique = true)
    private String staffId;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "residential_address", columnDefinition = "TEXT")
    private String residentialAddress;

    @Column(name = "staff_status", length = 20)
    private String staffStatus;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "staff")
    private List<StaffRoles> roles;
}
