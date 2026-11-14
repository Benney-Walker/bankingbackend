package com.bynexlabs.bankingbackend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "cash_client_ecosystem";

    // Messages that still fail after the listener retries (see application.properties) are
    // rejected, and RabbitMQ moves them here with their original routing key (roadmap 2.2).
    public static final String DEAD_LETTER_EXCHANGE_NAME = "cash_client_ecosystem_dead_letter";

    /************************************************
                QUEUES_DECLARATIONS
     *************************************************/
    public static final String DEFAULT_SHARES_ACCOUNT_QUEUE_NAME = "default_share_account_queue";
    public static final String OTHER_ACCOUNT_CREATION_QUEUE_NAME = "other_account_creation_queue";
    public static final String KYC_VERIFICATION_QUEUE_NAME = "kyc_verification_queue";
    // Member SMS after every transaction (owner decision 2026-11-14).
    public static final String TRANSACTION_SMS_QUEUE_NAME = "transaction_sms_queue";

    public static final String DEFAULT_SHARES_ACCOUNT_DEAD_LETTER_QUEUE_NAME = "default_share_account_dead_letter_queue";
    public static final String OTHER_ACCOUNT_CREATION_DEAD_LETTER_QUEUE_NAME = "other_account_creation_dead_letter_queue";
    public static final String KYC_VERIFICATION_DEAD_LETTER_QUEUE_NAME = "kyc_verification_dead_letter_queue";
    public static final String TRANSACTION_SMS_DEAD_LETTER_QUEUE_NAME = "transaction_sms_dead_letter_queue";

    /************************************************
     ROUTING
     *************************************************/
    public static final String DEFAULT_SHARE_ACCOUNT_ROUTING_KEY = "default_share_account_routing_key";
    public static final String OTHER_ACCOUNT_CREATION_ROUTING_KEY = "other_account_creation_routing_key";
    public static final String KYC_VERIFICATION_ROUTING_KEY = "kyc_verification_routing_key";
    public static final String TRANSACTION_SMS_ROUTING_KEY = "transaction_sms_routing_key";


    /************************************************
     * *******       QUEUES METHODS   ***********
     *************************************************/
    // NOTE: adding the dead-letter argument changes these queues' definition. RabbitMQ refuses to
    // redeclare an existing queue with different arguments, so queues created before this
    // change must be deleted once (when empty) before deploying; the app then recreates them.
    @Bean
    public Queue defaultShareAccountQueue() {
        return QueueBuilder.durable(DEFAULT_SHARES_ACCOUNT_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .build();
    }

    @Bean
    public Queue otherAccountCreationQueue() {
        return QueueBuilder.durable(OTHER_ACCOUNT_CREATION_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .build();
    }

    @Bean
    public Queue kycVerificationQueue() {
        return QueueBuilder.durable(KYC_VERIFICATION_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .build();
    }

    @Bean
    public Queue transactionSmsQueue() {
        return QueueBuilder.durable(TRANSACTION_SMS_QUEUE_NAME)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE_NAME)
                .build();
    }

    @Bean
    public Queue defaultShareAccountDeadLetterQueue() {
        return QueueBuilder.durable(DEFAULT_SHARES_ACCOUNT_DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    public Queue otherAccountCreationDeadLetterQueue() {
        return QueueBuilder.durable(OTHER_ACCOUNT_CREATION_DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    public Queue kycVerificationDeadLetterQueue() {
        return QueueBuilder.durable(KYC_VERIFICATION_DEAD_LETTER_QUEUE_NAME).build();
    }

    @Bean
    public Queue transactionSmsDeadLetterQueue() {
        return QueueBuilder.durable(TRANSACTION_SMS_DEAD_LETTER_QUEUE_NAME).build();
    }

    /************************************************
     BINDINGS
     *************************************************/
    @Bean
    public Binding defaultShareAccountBinding(Queue defaultShareAccountQueue, TopicExchange exchange) {
        return BindingBuilder.bind(defaultShareAccountQueue).to(exchange).with(DEFAULT_SHARE_ACCOUNT_ROUTING_KEY);
    }

    @Bean
    public Binding otherAccountCreationBinding(Queue otherAccountCreationQueue, TopicExchange exchange) {
        return BindingBuilder.bind(otherAccountCreationQueue).to(exchange).with(OTHER_ACCOUNT_CREATION_ROUTING_KEY);
    }

    @Bean
    public Binding kycVerificationBinding(Queue kycVerificationQueue, TopicExchange exchange) {
        return BindingBuilder.bind(kycVerificationQueue).to(exchange).with(KYC_VERIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding transactionSmsBinding(Queue transactionSmsQueue, TopicExchange exchange) {
        return BindingBuilder.bind(transactionSmsQueue).to(exchange).with(TRANSACTION_SMS_ROUTING_KEY);
    }

    // Dead-lettered messages keep their original routing key, so each dead-letter queue is
    // bound with the same key as the queue it backs up.
    @Bean
    public Binding defaultShareAccountDeadLetterBinding() {
        return BindingBuilder.bind(defaultShareAccountDeadLetterQueue()).to(deadLetterExchange())
                .with(DEFAULT_SHARE_ACCOUNT_ROUTING_KEY);
    }

    @Bean
    public Binding otherAccountCreationDeadLetterBinding() {
        return BindingBuilder.bind(otherAccountCreationDeadLetterQueue()).to(deadLetterExchange())
                .with(OTHER_ACCOUNT_CREATION_ROUTING_KEY);
    }

    @Bean
    public Binding kycVerificationDeadLetterBinding() {
        return BindingBuilder.bind(kycVerificationDeadLetterQueue()).to(deadLetterExchange())
                .with(KYC_VERIFICATION_ROUTING_KEY);
    }

    @Bean
    public Binding transactionSmsDeadLetterBinding() {
        return BindingBuilder.bind(transactionSmsDeadLetterQueue()).to(deadLetterExchange())
                .with(TRANSACTION_SMS_ROUTING_KEY);
    }

    /************************************************
     BROKER_STATIC_CONFIG
     *************************************************/
    // @Primary: the main exchange is the one injected wherever a TopicExchange is asked for.
    @Bean
    @Primary
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
    }

    @Bean
    public TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE_NAME);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public AmqpTemplate customRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
