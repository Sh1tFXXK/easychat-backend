package org.example.easychat.Config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ配置类
 */
@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.host")  // 只有配置了RabbitMQ才启用
public class RabbitMQConfig {
    
    // 队列名称
    public static final String NOTIFICATION_QUEUE = "attention.notification.queue";
    public static final String NOTIFICATION_DLQ = "attention.notification.dlq";
    public static final String HIGH_PRIORITY_QUEUE = "attention.notification.high.queue";
    
    // 交换机名称
    public static final String NOTIFICATION_EXCHANGE = "attention.notification.exchange";
    public static final String NOTIFICATION_DLX = "attention.notification.dlx";
    
    // 路由键
    public static final String NOTIFICATION_ROUTING_KEY = "attention.notification";
    public static final String HIGH_PRIORITY_ROUTING_KEY = "attention.notification.high";
    public static final String DLQ_ROUTING_KEY = "attention.notification.dlq";
    
    /**
     * 消息转换器
     */
    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    
    /**
     * RabbitTemplate配置
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        
        // 开启发送确认
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                System.err.println("消息发送失败: " + cause);
            }
        });
        
        // 开启返回确认
        template.setReturnsCallback(returned -> {
            System.err.println("消息返回: " + returned.getMessage());
        });
        
        return template;
    }
    
    /**
     * 监听器容器工厂
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        
        // 设置并发消费者数量
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(10);
        
        // 设置预取数量
        factory.setPrefetchCount(10);
        
        // 开启手动确认
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        
        return factory;
    }
    
    /**
     * 通知交换机
     */
    @Bean
    public DirectExchange notificationExchange() {
        return ExchangeBuilder.directExchange(NOTIFICATION_EXCHANGE)
                .durable(true)
                .build();
    }
    
    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(NOTIFICATION_DLX)
                .durable(true)
                .build();
    }
    
    /**
     * 普通通知队列
     */
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 300000) // 5分钟TTL
                .build();
    }
    
    /**
     * 高优先级通知队列
     */
    @Bean
    public Queue highPriorityQueue() {
        return QueueBuilder.durable(HIGH_PRIORITY_QUEUE)
                .withArgument("x-dead-letter-exchange", NOTIFICATION_DLX)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 300000) // 5分钟TTL
                .withArgument("x-max-priority", 10) // 设置最大优先级
                .build();
    }
    
    /**
     * 死信队列
     */
    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(NOTIFICATION_DLQ)
                .build();
    }
    
    /**
     * 绑定普通通知队列
     */
    @Bean
    public Binding notificationBinding() {
        return BindingBuilder.bind(notificationQueue())
                .to(notificationExchange())
                .with(NOTIFICATION_ROUTING_KEY);
    }
    
    /**
     * 绑定高优先级队列
     */
    @Bean
    public Binding highPriorityBinding() {
        return BindingBuilder.bind(highPriorityQueue())
                .to(notificationExchange())
                .with(HIGH_PRIORITY_ROUTING_KEY);
    }
    
    /**
     * 绑定死信队列
     */
    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange())
                .with(DLQ_ROUTING_KEY);
    }
}