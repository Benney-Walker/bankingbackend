package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.StaffStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class Staff {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
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

    @Column(name = "email")
    private String email;

    @Column(name = "password")
    private String password;

    @Column(name = "residential_address", columnDefinition = "TEXT")
    private String residentialAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "staff_status", length = 20)
    private StaffStatus staffStatus;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "staff")
    private List<StaffRoles> roles;

    /**
     * Access tokens issued before this moment are refused (V39). Written only by
     * SessionService's bulk update, never by saving the entity, so a stale copy of the staff
     * can't undo a sign-out.
     */
    @Column(name = "sessions_valid_after", insertable = false, updatable = false)
    private Instant sessionsValidAfter;

    /**
     * A collector's registered phone: its device ID as the app reads it (V42, owner decisions 2026-11-23).
     * Written only by CollectorDeviceService's bulk updates, like sessionsValidAfter.
     */
    @Column(name = "registered_device_id", insertable = false, updatable = false, length = 100)
    private String registeredDeviceId;

    @Column(name = "device_registered_at", insertable = false, updatable = false)
    private LocalDateTime deviceRegisteredAt;

    /**
     * Wrong passwords in a row (V47, owner decisions 2026-11-24). Written only by
     * LoginAttemptService's bulk updates, like sessionsValidAfter.
     */
    @Column(name = "failed_login_attempts", insertable = false, updatable = false)
    private int failedLoginAttempts;

    /** Set when the 5th wrong password in a row locks the account; cleared only by an administrator. */
    @Column(name = "locked_at", insertable = false, updatable = false)
    private LocalDateTime lockedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch", nullable = false, updatable = false)
    private Branch branch;
}
