package com.ticket.platform.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：下单主队列、死信交换机与死信队列。
 * 队列和消息都持久化；消费者重试耗尽后由 DLX 转入死信队列。
 */
@Configuration
@EnableConfigurationProperties(AsyncOrderProperties.class)
public class RabbitMqConfig {

    public static final String ORDER_EXCHANGE = "ticket.order.exchange";
    public static final String ORDER_QUEUE = "ticket.order.create";
    public static final String ORDER_ROUTING_KEY = "order.create";

    public static final String ORDER_DLX = "ticket.order.dlx";
    public static final String ORDER_DLQ = "ticket.order.create.dlq";
    public static final String ORDER_DLQ_ROUTING_KEY = "order.create.dlq";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderCreateQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(ORDER_DLX)
                .deadLetterRoutingKey(ORDER_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding orderCreateBinding() {
        return BindingBuilder.bind(orderCreateQueue())
                .to(orderExchange())
                .with(ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(ORDER_DLX, true, false);
    }

    @Bean
    public Queue orderCreateDeadLetterQueue() {
        return QueueBuilder.durable(ORDER_DLQ).build();
    }

    @Bean
    public Binding orderCreateDeadLetterBinding() {
        return BindingBuilder.bind(orderCreateDeadLetterQueue())
                .to(orderDeadLetterExchange())
                .with(ORDER_DLQ_ROUTING_KEY);
    }

    /** 消息体使用 JSON 序列化，便于排查和手工重放。 */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
