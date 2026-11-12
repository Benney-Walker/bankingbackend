package com.bynexlabs.bankingbackend.models;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** A RabbitMQ message a consumer has already handled. See V18. */
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @EmbeddedId
    private ProcessedMessageId id;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
