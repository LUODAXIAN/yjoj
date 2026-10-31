package com.luoyujia.yjoj.question.rabbitmq;

import com.luoyujia.yjoj.common.constant.JudgeMqConstants;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 首次任务投递：消息确认不等于数据库与 RabbitMQ 原子提交。 */
@Component
public class MyMessageProducer {
  @Resource private RabbitTemplate rabbitTemplate;

  @Value("${yjoj.judge.confirm-timeout-ms:5000}")
  private long confirmTimeoutMs;

  public void sendMessage(String exchange, String routingKey, String message) {
    // 原实现：rabbitTemplate.convertAndSend(exchange, routingKey, message);
    CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
    rabbitTemplate.setMandatory(true);
    try {
      rabbitTemplate.convertAndSend(
          exchange,
          routingKey,
          message,
          outbound -> {
            outbound.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            outbound.getMessageProperties().setHeader(JudgeMqConstants.ATTEMPT_HEADER, 0);
            return outbound;
          },
          correlation);
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(confirmTimeoutMs, TimeUnit.MILLISECONDS);
      if (!confirm.isAck() || correlation.getReturned() != null)
        throw new IllegalStateException("首次判题消息未成功投递");
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("等待首次消息确认被中断", interrupted);
    } catch (Exception failed) {
      throw new IllegalStateException("提交已保存，但判题消息投递失败，需排查等待记录", failed);
    }
  }
}
