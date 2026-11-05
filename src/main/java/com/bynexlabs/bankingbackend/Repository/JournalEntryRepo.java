package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface JournalEntryRepo extends JpaRepository<JournalEntry, UUID> {
}
