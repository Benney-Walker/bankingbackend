package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.OutboxEventRepo;
import com.bynexlabs.bankingbackend.models.OutboxEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock
    private OutboxEventRepo outboxEventRepo;

    @Mock
    private OutboxPublisher outboxPublisher;

    // The real converter from RabbitMQConfig, so the test checks the actual stored payload.
    private final MessageConverter converter = new JacksonJsonMessageConverter();

    private OutboxService outboxService;

    private final UUID outboxId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        outboxService = new OutboxService(outboxEventRepo, converter, outboxPublisher);
        // Stand-in for the caller's transaction, so after-commit callbacks can be registered.
        TransactionSynchronizationManager.initSynchronization();
        when(outboxEventRepo.save(any(OutboxEvent.class))).thenAnswer(invocation -> {
            OutboxEvent event = invocation.getArgument(0);
            event.setId(outboxId);
            return event;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void storesTheMessageExactlyAsTheConverterWouldSendIt() {
        UUID memberId = UUID.randomUUID();
        SharesAccountEvent event = SharesAccountEvent.builder().memberId(memberId).build();

        outboxService.enqueue("cash_client_ecosystem", "default_share_account_routing_key", event);

        ArgumentCaptor<OutboxEvent> saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepo).save(saved.capture());
        OutboxEvent row = saved.getValue();
        assertThat(row.getExchange()).isEqualTo("cash_client_ecosystem");
        assertThat(row.getRoutingKey()).isEqualTo("default_share_account_routing_key");
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getTypeId()).isEqualTo(SharesAccountEvent.class.getName());

        // The message rebuilt from the row has the same body and type header as a direct send.
        Message direct = converter.toMessage(event, new MessageProperties());
        Message rebuilt = OutboxPublisher.toMessage(row);
        assertThat(rebuilt.getBody()).isEqualTo(direct.getBody());
        assertThat(new String(rebuilt.getBody(), StandardCharsets.UTF_8)).contains(memberId.toString());
        assertThat(rebuilt.getMessageProperties().getContentType())
                .isEqualTo(direct.getMessageProperties().getContentType());
        assertThat(rebuilt.getMessageProperties().getMessageId()).isEqualTo(outboxId.toString());
    }

    @Test
    void publishesOnlyAfterTheCallerCommits() {
        outboxService.enqueue("x", "k", SharesAccountEvent.builder().memberId(UUID.randomUUID()).build());

        verify(outboxPublisher, never()).publishById(any());

        List<TransactionSynchronization> callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertThat(callbacks).hasSize(1);
        callbacks.get(0).afterCommit();

        verify(outboxPublisher).publishById(outboxId);
    }

    @Test
    void failedImmediatePublishDoesNotBreakTheRequest() {
        outboxService.enqueue("x", "k", SharesAccountEvent.builder().memberId(UUID.randomUUID()).build());
        doThrow(new IllegalStateException("database blip")).when(outboxPublisher).publishById(outboxId);

        // Must not throw: the business change is committed and the retry job will send it.
        TransactionSynchronizationManager.getSynchronizations().get(0).afterCommit();
    }
}
