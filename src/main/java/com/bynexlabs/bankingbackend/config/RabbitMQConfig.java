package com.bynexlabs.bankingbackend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "cash_client_ecosystem";

    /************************************************
                QUEUES_DECLARATIONS
     *************************************************/
    public static final String DEFAULT_SHARES_ACCOUNT_QUEUE_NAME = "default_share_account_queue";
    public static final String OTHER_ACCOUNT_CREATION_QUEUE_NAME = "other_account_creation_queue";
    public static final String KYC_VERIFICATION_QUEUE_NAME = "kyc_verification_queue";

    /************************************************
     ROUTING
     *************************************************/
    public static final String DEFAULT_SHARE_ACCOUNT_ROUTING_KEY = "default_share_account_routing_key";
    public static final String OTHER_ACCOUNT_CREATION_ROUTING_KEY = "other_account_creation_routing_key";
    public static final String KYC_VERIFICATION_ROUTING_KEY = "kyc_verification_routing_key";


    /************************************************
     * *******       QUEUES METHODS   ***********
     *************************************************/
    @Bean
    public Queue defaultShareAccountQueue() {
        return new Queue(DEFAULT_SHARES_ACCOUNT_QUEUE_NAME);
    }

    @Bean
    public Queue otherAccountCreationQueue() {
        return new Queue(OTHER_ACCOUNT_CREATION_QUEUE_NAME);
    }

    @Bean
    public Queue kycVerificationQueue() {
        return new Queue(KYC_VERIFICATION_QUEUE_NAME);
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

    /************************************************
     BROKER_STATIC_CONFIG
     *************************************************/
    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME);
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
