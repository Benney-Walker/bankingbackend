package com.bynexlabs.bankingbackend.rabbitMQ;

import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RabbitMQProducer {

    private final RabbitTemplate rabbitTemplate;

    // Account-creation events go through OutboxService (decision D6) so they can't be lost;
    // don't add direct sends for events that must survive a RabbitMQ outage.

    public void sendKycVerificationEvent(Object payload) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY,
                payload
        );
    }
}
