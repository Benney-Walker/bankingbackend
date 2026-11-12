package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxEventRepo extends JpaRepository<OutboxEvent, UUID> {

    // FOR UPDATE SKIP LOCKED: the after-commit publish and the retry job (and any other app
    // instance) never send the same row at the same time; whoever locks it first sends it.
    @Query(value = """
            select * from outbox_events
            where id = :id and published_at is null
            for update skip locked
            """, nativeQuery = true)
    Optional<OutboxEvent> lockUnpublishedById(@Param("id") UUID id);

    @Query(value = """
            select * from outbox_events
            where published_at is null
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("limit") int limit);
}
