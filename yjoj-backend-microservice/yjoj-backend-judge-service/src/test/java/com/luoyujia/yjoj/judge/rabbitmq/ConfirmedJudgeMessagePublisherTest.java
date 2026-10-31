package com.luoyujia.yjoj.judge.rabbitmq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.luoyujia.yjoj.judge.config.JudgeRetryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class ConfirmedJudgeMessagePublisherTest {
  @Test
  void waitsForBrokerAckAndRejectsUnroutableMessages() {
    RabbitTemplate template = mock(RabbitTemplate.class);
    JudgeRetryProperties properties = new JudgeRetryProperties();
    ConfirmedJudgeMessagePublisher publisher =
        new ConfirmedJudgeMessagePublisher(template, properties);
    doAnswer(
            call -> {
              CorrelationData c = call.getArgument(4);
              c.setReturned(
                  new ReturnedMessage(
                      new Message(new byte[0], new MessageProperties()),
                      312,
                      "NO_ROUTE",
                      "retry",
                      "key"));
              c.getFuture().set(new CorrelationData.Confirm(true, null));
              return null;
            })
        .when(template)
        .convertAndSend(
            anyString(),
            anyString(),
            any(Object.class),
            any(MessagePostProcessor.class),
            any(CorrelationData.class));
    assertThrows(IllegalStateException.class, () -> publisher.retry(7L, 1));
  }

  @Test
  void brokerNackIsFailure() {
    RabbitTemplate template = mock(RabbitTemplate.class);
    doAnswer(
            call -> {
              CorrelationData c = call.getArgument(4);
              c.getFuture().set(new CorrelationData.Confirm(false, "rejected"));
              return null;
            })
        .when(template)
        .convertAndSend(
            anyString(),
            anyString(),
            any(Object.class),
            any(MessagePostProcessor.class),
            any(CorrelationData.class));
    assertThrows(
        IllegalStateException.class,
        () ->
            new ConfirmedJudgeMessagePublisher(template, new JudgeRetryProperties()).retry(7L, 1));
  }
}
