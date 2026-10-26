package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.Repository.IdEntityRepo;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.IdEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class IdGenerator {

    private final IdEntityRepo idEntityRepo;

    private final long ASSETS = 1000;
    private final long LIABILITY = 2000;
    private final long EQUITY = 3000;
    private final long REVENUE = 4000;
    private final long EXPENSES = 5000;
    private final long ACCOUNT_NUMBER = 100200;
    private final long STAFF_ID = 900100;

    private long getEntityId(Id_Type idType, long entityCode) {
        try {
            IdEntity idEntity = idEntityRepo.findByIdType(idType).orElse(null);
            if (idEntity == null) {
                idEntity = IdEntity.builder()
                        .idType(idType)
                        .idCode(entityCode)
                        .build();
            }

        } catch (Exception e) {
            return null;
        }
    }

}
