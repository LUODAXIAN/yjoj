package com.luoyujia.yjoj.judge.rabbitmq;

import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.common.constant.JudgeMqConstants;
import com.luoyujia.yjoj.judge.config.JudgeRetryProperties;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** 只有 broker 确认且没有不可路由退回，才允许消费者 ACK 原消息。 */
@Component
public class ConfirmedJudgeMessagePublisher {
  private final RabbitTemplate template;
  private final JudgeRetryProperties properties;

  public ConfirmedJudgeMessagePublisher(RabbitTemplate template, JudgeRetryProperties properties) {
    this.template = template;
    this.properties = properties;
    template.setMandatory(true);
  }

  public void retry(long submitId, int attempt) {
    send(
        JudgeMqConstants.RETRY_EXCHANGE,
        JudgeMqConstants.retryRoutingKey(attempt),
        String.valueOf(submitId),
        attempt,
        null);
  }

  public void dead(Long submitId, int attempt, String reason) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("submitId", submitId == null ? null : submitId.toString());
    payload.put("attempt", attempt);
    payload.put("reason", abbreviate(reason));
    send(
        JudgeMqConstants.DEAD_EXCHANGE,
        JudgeMqConstants.DEAD_ROUTING_KEY,
        JSONUtil.toJsonStr(payload),
        attempt,
        reason);
  }

  private void send(String exchange, String routing, String body, int attempt, String reason) {
    CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
    try {
      template.convertAndSend(
          exchange,
          routing,
          body,
          message -> {
            message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            message.getMessageProperties().setHeader(JudgeMqConstants.ATTEMPT_HEADER, attempt);
            if (reason != null)
              message.getMessageProperties().setHeader("x-yjoj-error", abbreviate(reason));
            return message;
          },
          correlation);
      CorrelationData.Confirm confirm =
          correlation.getFuture().get(properties.getConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
      if (!confirm.isAck() || correlation.getReturned() != null) {
        throw new IllegalStateException("判题消息未成功路由或未获 broker 确认");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("等待消息确认被中断", e);
    } catch (Exception e) {
      throw new IllegalStateException("判题消息发布失败", e);
    }
  }

  private String abbreviate(String text) {
    return text == null ? "System Error" : text.substring(0, Math.min(256, text.length()));
  }
}
