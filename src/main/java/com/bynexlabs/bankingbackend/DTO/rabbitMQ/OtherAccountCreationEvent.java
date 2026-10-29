package com.bynexlabs.bankingbackend.DTO.rabbitMQ;

import com.bynexlabs.bankingbackend.enums.AccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OtherAccountCreationEvent {

    private UUID memberId;

    private AccountType accountType;
}
