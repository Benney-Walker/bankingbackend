package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.Roles;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "staff_roles")
public class StaffRoles {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    private long id;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private Staffs staff;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Roles role;
}
