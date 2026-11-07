package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.OutboxEventRepo;
import com.bynexlabs.bankingbackend.models.OutboxEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepo outboxEventRepo;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OutboxPublisher publisher;

    private OutboxEvent pending(String routingKey) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .exchange("cash_client_ecosystem")
                .routingKey(routingKey)
                .payload("{\"memberId\":\"5f1c0a8e-0000-0000-0000-000000000001\"}")
                .contentType("application/json")
                .contentEncoding("UTF-8")
                .typeId("com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent")
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build();
    }

    @Test
    void sendsTheStoredMessageAndMarksItPublished() {
        OutboxEvent event = pending("default_share_account_routing_key");
        when(outboxEventRepo.lockUnpublishedById(event.getId())).thenReturn(Optional.of(event));

        publisher.publishById(event.getId());

        ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq("cash_client_ecosystem"), eq("default_share_account_routing_key"), sent.capture());
        Message message = sent.getValue();
        assertThat(new String(message.getBody(), StandardCharsets.UTF_8)).isEqualTo(event.getPayload());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry(OutboxPublisher.TYPE_ID_HEADER, event.getTypeId());
        assertThat(message.getMessageProperties().getMessageId()).isEqualTo(event.getId().toString());
        assertThat(message.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);

        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(outboxEventRepo).save(event);
    }

    @Test
    void alreadyPublishedOrLockedElsewhereSendsNothing() {
        UUID id = UUID.randomUUID();
        when(outboxEventRepo.lockUnpublishedById(id)).thenReturn(Optional.empty());

        publisher.publishById(id);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void brokerFailureIsRecordedAndLeftForRetry() {
        OutboxEvent event = pending("default_share_account_routing_key");
        when(outboxEventRepo.lockUnpublishedById(event.getId())).thenReturn(Optional.of(event));
        doThrow(new AmqpException("Connection refused"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        publisher.publishById(event.getId());

        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Connection refused");
        verify(outboxEventRepo).save(event);
    }

    @Test
    void retryJobSendsPendingRowsInOrder() {
        OutboxEvent first = pending("default_share_account_routing_key");
        OutboxEvent second = pending("other_account_creation_routing_key");
        when(outboxEventRepo.lockUnpublishedBatch(OutboxPublisher.BATCH_SIZE)).thenReturn(List.of(first, second));

        publisher.publishPending();

        verify(rabbitTemplate).send(eq("cash_client_ecosystem"), eq("default_share_account_routing_key"), any(Message.class));
        verify(rabbitTemplate).send(eq("cash_client_ecosystem"), eq("other_account_creation_routing_key"), any(Message.class));
        assertThat(first.getPublishedAt()).isNotNull();
        assertThat(second.getPublishedAt()).isNotNull();
    }

    @Test
    void retryJobStopsAtTheFirstBrokerFailure() {
        OutboxEvent first = pending("default_share_account_routing_key");
        OutboxEvent second = pending("other_account_creation_routing_key");
        when(outboxEventRepo.lockUnpublishedBatch(OutboxPublisher.BATCH_SIZE)).thenReturn(List.of(first, second));
        doThrow(new AmqpException("Connection refused"))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        publisher.publishPending();

        verify(rabbitTemplate, times(1)).send(anyString(), anyString(), any(Message.class));
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(second.getAttempts()).isZero();
    }
}
