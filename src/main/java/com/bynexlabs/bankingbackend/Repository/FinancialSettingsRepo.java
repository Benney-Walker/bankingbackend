package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.FinancialSettings;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FinancialSettingsRepo extends JpaRepository<FinancialSettings, Integer> {

    // Two administrators saving at the same moment are applied one after the other.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from FinancialSettings s where s.id = :id")
    Optional<FinancialSettings> lockById(@Param("id") Integer id);
}
