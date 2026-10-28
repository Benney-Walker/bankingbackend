package com.bynexlabs.bankingbackend.rabbitMQ;

import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RabbitMQProducer {

    private final RabbitTemplate rabbitTemplate;

    public void sendDefaultShareAccountEvent(Object payload) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.DEFAULT_SHARE_ACCOUNT_ROUTING_KEY,
                payload
        );
    }
}
