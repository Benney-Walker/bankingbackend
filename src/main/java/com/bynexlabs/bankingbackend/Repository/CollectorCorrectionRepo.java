package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.CorrectionRecordType;
import com.bynexlabs.bankingbackend.models.CollectorCorrection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectorCorrectionRepo extends JpaRepository<CollectorCorrection, Long> {

    /** The first correction of a record: its previous values are the record as first saved. */
    Optional<CollectorCorrection> findFirstByRecordTypeAndRecordIdOrderByIdAsc(CorrectionRecordType recordType,
                                                                                UUID recordId);
}
