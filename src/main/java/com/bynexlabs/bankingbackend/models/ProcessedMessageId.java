package com.bynexlabs.bankingbackend.models;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** A message counts as processed per consumer, so two consumers of one message don't clash. */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class ProcessedMessageId implements Serializable {

    @Column(name = "message_id", nullable = false)
    private String messageId;

    @Column(name = "consumer", nullable = false)
    private String consumer;
}
