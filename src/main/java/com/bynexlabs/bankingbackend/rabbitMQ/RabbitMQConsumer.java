package com.bynexlabs.bankingbackend.rabbitMQ;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.service.AsyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class RabbitMQConsumer {

    private final AsyncService asyncService;

    @RabbitListener(queues = RabbitMQConfig.DEFAULT_SHARES_ACCOUNT_QUEUE_NAME)
    public void consumeDefaultShareAccountEvent(
            SharesAccountEvent sharesAccountEvent,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId
    ) {
        asyncService.createDefaultSharesAccount(sharesAccountEvent, messageId);
    }

    @RabbitListener(queues = RabbitMQConfig.OTHER_ACCOUNT_CREATION_QUEUE_NAME)
    public void consumeOtherAccountCreationEvent(
            OtherAccountCreationEvent otherEvent,
            @Header(name = AmqpHeaders.MESSAGE_ID, required = false) String messageId
    ) {
        asyncService.createOtherAccount(otherEvent, messageId);
    }

    @RabbitListener(queues = RabbitMQConfig.KYC_VERIFICATION_QUEUE_NAME)
    public void consumeKycVerificationEvent(KycVerificationEvent kycEvent) {
        asyncService.processKycVerification(kycEvent);
    }
}
