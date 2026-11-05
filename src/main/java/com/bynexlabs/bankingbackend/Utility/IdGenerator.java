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

    public String generateId(Id_Type idType) {
        String newCode = "";

        switch (idType) {
            case STAFF_ID ->  {
                long STAFF_ID = 900100;
                newCode = String.valueOf(getEntityCode(idType, STAFF_ID));
            }
            case MEMBER_ACCOUNT -> {
                long MEMBER_ACCOUNT = 10000;
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

        return code + String.format("%05d", currentMillis);
    }

}
