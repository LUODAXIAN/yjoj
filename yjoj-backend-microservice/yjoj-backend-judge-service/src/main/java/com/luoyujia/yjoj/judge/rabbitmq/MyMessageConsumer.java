package com.luoyujia.yjoj.judge.rabbitmq;

import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import com.luoyujia.yjoj.common.constant.JudgeMqConstants;
import com.luoyujia.yjoj.judge.exception.*;
import com.luoyujia.yjoj.judge.judge.JudgeService;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 消费者只在状态写入及后续消息确认成功后 ACK；状态与轮次共同保护幂等。 */
@Component
@Slf4j
public class MyMessageConsumer {
  private final JudgeService judgeService;
  private final QuestionFeignClient questionClient;
  private final JudgeRetryCoordinator retries;
  private final ConfirmedJudgeMessagePublisher publisher;
  private final JudgeConsumerFailureHandler failures;

  public MyMessageConsumer(
      JudgeService judgeService,
      QuestionFeignClient questionClient,
      JudgeRetryCoordinator retries,
      ConfirmedJudgeMessagePublisher publisher,
      JudgeConsumerFailureHandler failures) {
    this.judgeService = judgeService;
    this.questionClient = questionClient;
    this.retries = retries;
    this.publisher = publisher;
    this.failures = failures;
  }

  @RabbitListener(
      id = JudgeConsumerFailureHandler.LISTENER_ID,
      queues = JudgeMqConstants.QUEUE,
      ackMode = "MANUAL")
  public void receiveMessage(Message message, Channel channel) {
    try {
      process(message);
      channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
    } catch (Exception infrastructure) {
      // 未确认消息由容器关闭后重新投递；不立即 NACK requeue 形成故障热循环。
      failures.stop(infrastructure);
    }
  }

  private void process(Message message) {
    Long submitId = null;
    int attempt = 0;
    try {
      submitId = Long.parseLong(new String(message.getBody(), StandardCharsets.UTF_8));
      if (submitId <= 0) throw new IllegalArgumentException("无效提交 ID");
      Object header =
          message.getMessageProperties().getHeaders().get(JudgeMqConstants.ATTEMPT_HEADER);
      // 缺失头只能是初始第 0 轮，禁止替换为数据库当前轮次。
      attempt = header == null ? 0 : Integer.parseInt(header.toString());
      if (attempt < 0 || attempt > JudgeMqConstants.MAX_ATTEMPT)
        throw new IllegalArgumentException("无效执行轮次");
    } catch (IllegalArgumentException invalid) {
      publisher.dead(null, 0, "非法判题消息：提交 ID 或轮次格式错误");
      return;
    }
    QuestionSubmit current = questionClient.getQuestionSubmitById(submitId);
    if (current == null) {
      publisher.dead(submitId, attempt, "提交不存在");
      return;
    }
    int databaseAttempt = current.getJudgeAttempt() == null ? 0 : current.getJudgeAttempt();
    if (Integer.valueOf(2).equals(current.getStatus())) return;
    if (Integer.valueOf(3).equals(current.getStatus())) {
      // FAILED 已提交但失败通知发布/原 ACK 失败：重投时补通知，不重新判题。
      // 失败通知允许重复，排查时按 submitId + attempt 去重；任务结果不会重复更新。
      if (attempt == databaseAttempt
          && Boolean.TRUE.equals(message.getMessageProperties().isRedelivered())) {
        publisher.dead(submitId, attempt, "恢复最终失败通知，原故障详见判题日志");
      }
      return;
    }
    if (attempt < databaseAttempt) {
      // 原消息确认前断线：只补发已准备的下一轮，绝不让旧消息冒充新轮次执行。
      if (Boolean.TRUE.equals(message.getMessageProperties().isRedelivered())
          && attempt + 1 == databaseAttempt
          && Integer.valueOf(0).equals(current.getStatus())) {
        publisher.retry(submitId, databaseAttempt);
      }
      return;
    }
    if (attempt > databaseAttempt) {
      publisher.dead(submitId, attempt, "消息轮次超前，未修改任务");
      return;
    }
    if (Integer.valueOf(1).equals(current.getStatus())) return;
    if (!Integer.valueOf(0).equals(current.getStatus())) {
      publisher.dead(submitId, attempt, "未知提交状态");
      return;
    }
    try {
      judgeService.doJudge(submitId, attempt);
    } catch (RetryableJudgeException | NonRetryableJudgeException taskFailure) {
      log.warn(
          "判题执行失败，submitId={}，attempt={}，type={}",
          submitId,
          attempt,
          taskFailure.getClass().getSimpleName());
      retries.handleFailure(current, attempt, taskFailure);
    }
    // Feign/数据库/回写异常不冒充已完成：向上传播并停止消费者，等待人工排查。
  }

  // region 原消费者实现（未区分轮次、异常后直接 FAILED 并 NACK）；保留供对照。
  // private final JudgeService judgeService;
  //     private final QuestionFeignClient questionClient;
  //
  //     public MyMessageConsumer(JudgeService judgeService, QuestionFeignClient questionClient) {
  //         this.judgeService = judgeService;
  //         this.questionClient = questionClient;
  //     }
  //
  //     @RabbitListener(queues = JudgeMqConstants.QUEUE, ackMode = "MANUAL")
  //     public void receiveMessage(
  //             String message,
  //             Channel channel,
  //             @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
  //         long submitId;
  //         try {
  //             submitId = Long.parseLong(message);
  //         } catch (NumberFormatException e) {
  //             channel.basicNack(deliveryTag, false, false);
  //             return;
  //         }
  //
  //         QuestionSubmit current = questionClient.getQuestionSubmitById(submitId);
  //         if (current != null &&
  // (QuestionSubmitStatusEnum.SUCCEED.getValue().equals(current.getStatus())
  //                 || QuestionSubmitStatusEnum.FAILED.getValue().equals(current.getStatus()))) {
  //             channel.basicAck(deliveryTag, false);
  //             return;
  //         }
  //
  //         try {
  //             judgeService.doJudge(submitId);
  //             channel.basicAck(deliveryTag, false);
  //         } catch (Exception e) {
  //             log.error("判题消息处理失败，submitId={}", submitId, e);
  //             markFailed(submitId, current);
  //             channel.basicNack(deliveryTag, false, false);
  //         }
  //     }
  //
  //     private void markFailed(long submitId, QuestionSubmit current) {
  //         JudgeInfo info = new JudgeInfo();
  //         info.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
  //         QuestionSubmit failed = new QuestionSubmit();
  //         failed.setId(submitId);
  //         if (current != null) {
  //             failed.setQuestionId(current.getQuestionId());
  //         }
  //         failed.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
  //         failed.setJudgeInfo(JSONUtil.toJsonStr(info));
  //         try {
  //             questionClient.failQuestionSubmit(failed);
  //         } catch (Exception writeBackError) {
  //             log.error("FAILED 状态回写失败，submitId={}", submitId, writeBackError);
  //         }
  //     }
  // endregion
}
