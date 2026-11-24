package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Retention (V47): the database refuses to delete anything younger than 7 years. */
    @Modifying
    @Query("delete from AuditLog a where a.occurredAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
