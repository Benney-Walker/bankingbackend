package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.Id_Type;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "id_entity")
public class IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, unique = true)
    private Id_Type idType;

    @Column(name = "id_code", nullable = false)
    private long idCode;
}
