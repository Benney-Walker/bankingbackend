package com.bynexlabs.bankingbackend.models;

import jakarta.persistence.*;

@Entity
@Table(name = "id_entity")
public class IdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    private
}
