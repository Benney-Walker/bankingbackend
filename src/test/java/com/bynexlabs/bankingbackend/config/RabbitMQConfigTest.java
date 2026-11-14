package com.bynexlabs.bankingbackend.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

/** Every work queue dead-letters into its own queue after the listener retries (roadmap 2.2). */
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void workQueuesDeadLetterToTheDeadLetterExchange() {
        for (Queue queue : new Queue[]{
                config.defaultShareAccountQueue(), config.otherAccountCreationQueue(), config.kycVerificationQueue(),
                config.transactionSmsQueue()}) {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments())
                    .containsEntry("x-dead-letter-exchange", RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME);
        }
    }

    @Test
    void eachDeadLetterQueueIsBoundWithTheOriginalRoutingKey() {
        assertBinding(config.defaultShareAccountDeadLetterBinding(),
                RabbitMQConfig.DEFAULT_SHARES_ACCOUNT_DEAD_LETTER_QUEUE_NAME, RabbitMQConfig.DEFAULT_SHARE_ACCOUNT_ROUTING_KEY);
        assertBinding(config.otherAccountCreationDeadLetterBinding(),
                RabbitMQConfig.OTHER_ACCOUNT_CREATION_DEAD_LETTER_QUEUE_NAME, RabbitMQConfig.OTHER_ACCOUNT_CREATION_ROUTING_KEY);
        assertBinding(config.kycVerificationDeadLetterBinding(),
                RabbitMQConfig.KYC_VERIFICATION_DEAD_LETTER_QUEUE_NAME, RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY);
        assertBinding(config.transactionSmsDeadLetterBinding(),
                RabbitMQConfig.TRANSACTION_SMS_DEAD_LETTER_QUEUE_NAME, RabbitMQConfig.TRANSACTION_SMS_ROUTING_KEY);
    }

    private void assertBinding(Binding binding, String queue, String routingKey) {
        assertThat(binding.getExchange()).isEqualTo(RabbitMQConfig.DEAD_LETTER_EXCHANGE_NAME);
        assertThat(binding.getDestination()).isEqualTo(queue);
        assertThat(binding.getRoutingKey()).isEqualTo(routingKey);
    }
}
