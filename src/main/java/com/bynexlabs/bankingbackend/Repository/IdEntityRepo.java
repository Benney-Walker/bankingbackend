package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.IdEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdEntityRepo extends JpaRepository<IdEntity, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdEntity> findByIdType(Id_Type idType);
}
