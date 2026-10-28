package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.Repository.IdEntityRepo;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.IdEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdGenerator {

    private final IdEntityRepo idEntityRepo;

    private final long ASSETS = 1000;
    private final long LIABILITY = 2000;
    private final long EQUITY = 3000;
    private final long REVENUE = 4000;
    private final long EXPENSES = 5000;
    private final long MEMBER_ACCOUNT = 100200;
    private final long STAFF_ID = 900100;

    public String generateId(Id_Type idType) {
        String newCode = "";

        switch (idType) {
            case ASSET -> {
                newCode = String.valueOf(getEntityCode(idType, ASSETS));
            }
            case LIABILITY ->  {
                newCode = String.valueOf(getEntityCode(idType, LIABILITY));
            }
            case EQUITY -> {
                newCode = String.valueOf(getEntityCode(idType, EQUITY));
            }
            case REVENUE ->  {
                newCode = String.valueOf(getEntityCode(idType, REVENUE));
            }
            case EXPENSE ->  {
                newCode = String.valueOf(getEntityCode(idType, EXPENSES));
            }
            case STAFF_ID ->  {
                newCode = String.valueOf(getEntityCode(idType, STAFF_ID));
            }
            case MEMBER_ACCOUNT -> {
                newCode = getEntityCode(idType, MEMBER_ACCOUNT);
            }
        }

        return newCode;
    }

    private String getEntityCode(Id_Type idType, long entityCode) {
        try {
            IdEntity idEntity = idEntityRepo.findByIdType(idType).orElse(null);
            if (idEntity == null) {
                idEntity = IdEntity.builder()
                        .idType(idType)
                        .idCode(entityCode)
                        .build();
            }
            if (idType == Id_Type.MEMBER_ACCOUNT) {
                String code = String.valueOf(idEntity.getIdCode());
                idEntity.setIdCode(idEntity.getIdCode() + 1);
                idEntityRepo.save(idEntity);
                return generateUniqueAccountNumber(code);
            }

            long code = idEntity.getIdCode();
            idEntity.setIdCode(code + 1);
            idEntityRepo.save(idEntity);
            return String.valueOf(code);
        } catch (Exception e) {
            throw new RuntimeException("Error: " + e);
        }
    }

    private String generateUniqueAccountNumber(String code) {
        long currentMillis = System.currentTimeMillis() % 100000L;

        return code + currentMillis;
    }

}
