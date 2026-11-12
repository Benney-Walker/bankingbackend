package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepo extends JpaRepository<AuditLog, Long> {
}
