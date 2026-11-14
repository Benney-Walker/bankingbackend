package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.ProcessedMessage;
import com.bynexlabs.bankingbackend.models.ProcessedMessageId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedMessageRepo extends JpaRepository<ProcessedMessage, ProcessedMessageId> {

    // Returns 1 when the ID is recorded now and 0 when it was already there. Done as a single
    // insert so two deliveries of the same message can't both pass a separate "exists?" check:
    // the second waits on the first's row and then gets 0 (or 1 if the first rolled back).
    @Modifying
    @Query(value = """
            insert into processed_messages (message_id, consumer, processed_at)
            values (:messageId, :consumer, now())
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("messageId") String messageId, @Param("consumer") String consumer);

    @Query(value = "select exists (select 1 from processed_messages where message_id = :messageId and consumer = :consumer)",
            nativeQuery = true)
    boolean isProcessed(@Param("messageId") String messageId, @Param("consumer") String consumer);
}
