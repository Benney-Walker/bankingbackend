package com.bynexlabs.bankingbackend.rabbitMQ;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.service.AsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RabbitMQConsumer {

    private final AsyncService asyncService;

    @RabbitListener(queues = RabbitMQConfig.DEFAULT_SHARES_ACCOUNT_QUEUE_NAME)
    public void consumeDefaultShareAccountEvent(SharesAccountEvent sharesAccountEvent) {
        asyncService.createDefaultSharesAccount(sharesAccountEvent);
    }

    @RabbitListener(queues = RabbitMQConfig.OTHER_ACCOUNT_CREATION_QUEUE_NAME)
    public void consumeOtherAccountCreationEvent(OtherAccountCreationEvent otherEvent) {
        asyncService.createOtherAccount(otherEvent);
    }
}
