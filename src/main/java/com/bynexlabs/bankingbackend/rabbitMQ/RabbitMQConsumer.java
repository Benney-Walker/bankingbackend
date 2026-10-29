package com.bynexlabs.bankingbackend.rabbitMQ;

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

    @RabbitListener(queues = RabbitMQConfig.DEFAULT_SHARE_ACCOUNT_QUEUE_NAME)
    public void consumeDefaultShareAccountEvent(SharesAccountEvent sharesAccountEvent) {
        asyncService.createDefaultSharesAccount(sharesAccountEvent);
    }
}
