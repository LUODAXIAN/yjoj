package com.luoyujia.yjoj.question.config;

import com.luoyujia.yjoj.common.constant.JudgeMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 声明持久化的判题交换机、队列及路由绑定。 */
@Configuration
public class RabbitMqConfig {

  @Bean
  public DirectExchange judgeExchange() {
    return new DirectExchange(JudgeMqConstants.EXCHANGE, true, false);
  }

  @Bean
  public Queue judgeQueue() {
    return new Queue(JudgeMqConstants.QUEUE, true);
  }

  @Bean
  public Binding judgeBinding(DirectExchange judgeExchange, Queue judgeQueue) {
    return BindingBuilder.bind(judgeQueue).to(judgeExchange).with(JudgeMqConstants.ROUTING_KEY);
  }

  /** 新增队列独立声明，绝不变更已有工作队列参数。两服务须保持完全一致。 */
  @Bean
  public org.springframework.amqp.core.Declarables retryTopology() {
    java.util.List<org.springframework.amqp.core.Declarable> declarations =
        new java.util.ArrayList<>();
    declarations.add(new DirectExchange(JudgeMqConstants.RETRY_EXCHANGE, true, false));
    declarations.add(new DirectExchange(JudgeMqConstants.DEAD_EXCHANGE, true, false));
    declarations.add(new Queue(JudgeMqConstants.DEAD_QUEUE, true));
    declarations.add(
        new Binding(
            JudgeMqConstants.DEAD_QUEUE,
            Binding.DestinationType.QUEUE,
            JudgeMqConstants.DEAD_EXCHANGE,
            JudgeMqConstants.DEAD_ROUTING_KEY,
            null));
    for (int attempt = 1; attempt <= JudgeMqConstants.MAX_ATTEMPT; attempt++) {
      java.util.Map<String, Object> arguments = new java.util.HashMap<>();
      arguments.put("x-message-ttl", JudgeMqConstants.retryDelaySeconds(attempt) * 1000);
      arguments.put("x-dead-letter-exchange", JudgeMqConstants.EXCHANGE);
      arguments.put("x-dead-letter-routing-key", JudgeMqConstants.ROUTING_KEY);
      String name = JudgeMqConstants.retryQueue(attempt);
      declarations.add(new Queue(name, true, false, false, arguments));
      declarations.add(
          new Binding(
              name,
              Binding.DestinationType.QUEUE,
              JudgeMqConstants.RETRY_EXCHANGE,
              JudgeMqConstants.retryRoutingKey(attempt),
              null));
    }
    return new org.springframework.amqp.core.Declarables(declarations);
  }
}
