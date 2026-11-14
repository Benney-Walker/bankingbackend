package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.CollectorCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CollectorCorrectionRepo extends JpaRepository<CollectorCorrection, Long> {
}
