package com.bynexlabs.bankingbackend.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
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
    public static final String DEFAULT_SHARE_ACCOUNT_QUEUE_NAME = "default_share_account_queue";

    /************************************************
     ROUTING
     *************************************************/
    public static final String DEFAULT_SHARE_ACCOUNT_ROUTING_KEY = "default_share_account_routing_key";


    /************************************************
     * *******       QUEUES METHODS   ***********
     *************************************************/
    @Bean
    public Queue defaultShareAccountQueue() {
        return new Queue(DEFAULT_SHARE_ACCOUNT_QUEUE_NAME);
    }

    /************************************************
     BINDINGS
     *************************************************/
    @Bean
    public Binding defaultShareAccountBinding(Queue defaultShareAccountQueue, TopicExchange exchange) {
        return BindingBuilder.bind(defaultShareAccountQueue).to(exchange).with(DEFAULT_SHARE_ACCOUNT_ROUTING_KEY);
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
