package com.luoyujia.yjoj.judge.rabbitmq;

import static org.mockito.Mockito.*;

import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import com.luoyujia.yjoj.common.constant.JudgeMqConstants;
import com.luoyujia.yjoj.judge.exception.RetryableJudgeException;
import com.luoyujia.yjoj.judge.judge.JudgeService;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.*;

class MyMessageConsumerTest {
  private final JudgeService judge = mock(JudgeService.class);
  private final QuestionFeignClient client = mock(QuestionFeignClient.class);
  private final JudgeRetryCoordinator retries = mock(JudgeRetryCoordinator.class);
  private final ConfirmedJudgeMessagePublisher publisher =
      mock(ConfirmedJudgeMessagePublisher.class);
  private final JudgeConsumerFailureHandler failures = mock(JudgeConsumerFailureHandler.class);
  private final Channel channel = mock(Channel.class);
  private final MyMessageConsumer consumer =
      new MyMessageConsumer(judge, client, retries, publisher, failures);

  private QuestionSubmit submit(int status, int attempt) {
    QuestionSubmit s = new QuestionSubmit();
    s.setId(7L);
    s.setQuestionId(3L);
    s.setStatus(status);
    s.setJudgeAttempt(attempt);
    when(client.getQuestionSubmitById(7L)).thenReturn(s);
    return s;
  }

  private Message message(String body, Integer attempt, boolean redelivered) {
    MessageProperties p = new MessageProperties();
    p.setDeliveryTag(11L);
    p.setRedelivered(redelivered);
    if (attempt != null) p.setHeader(JudgeMqConstants.ATTEMPT_HEADER, attempt);
    return new Message(body.getBytes(StandardCharsets.UTF_8), p);
  }

  @Test
  void ackOnlyAfterJudgeCompletes() throws Exception {
    submit(0, 0);
    consumer.receiveMessage(message("7", 0, false), channel);
    InOrder order = inOrder(judge, channel);
    order.verify(judge).doJudge(7L, 0);
    order.verify(channel).basicAck(11L, false);
  }

  @Test
  void temporaryFailureIsCoordinatedBeforeAck() throws Exception {
    QuestionSubmit s = submit(0, 0);
    RetryableJudgeException error = new RetryableJudgeException("offline");
    doThrow(error).when(judge).doJudge(7L, 0);
    consumer.receiveMessage(message("7", 0, false), channel);
    InOrder order = inOrder(retries, channel);
    order.verify(retries).handleFailure(s, 0, error);
    order.verify(channel).basicAck(11L, false);
  }

  @Test
  void failedRetryPublishNeverAcksOriginal() throws Exception {
    QuestionSubmit s = submit(0, 0);
    doThrow(new RetryableJudgeException("offline")).when(judge).doJudge(7L, 0);
    doThrow(new IllegalStateException("publish failed"))
        .when(retries)
        .handleFailure(eq(s), eq(0), any());
    consumer.receiveMessage(message("7", 0, false), channel);
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
    verify(failures).stop(any());
  }

  @Test
  void missingHeaderIsZeroNotCurrentDatabaseAttempt() throws Exception {
    submit(0, 1);
    consumer.receiveMessage(message("7", null, false), channel);
    verifyNoInteractions(judge, retries, publisher);
    verify(channel).basicAck(11L, false);
  }

  @Test
  void redeliveredOldMessageCanOnlyRepublishPreparedRound() throws Exception {
    submit(0, 1);
    consumer.receiveMessage(message("7", 0, true), channel);
    verify(publisher).retry(7L, 1);
    verifyNoInteractions(judge, retries);
    verify(channel).basicAck(11L, false);
  }

  @Test
  void aheadMessageDoesNotAdvanceTask() {
    submit(0, 1);
    consumer.receiveMessage(message("7", 2, false), channel);
    verify(publisher).dead(eq(7L), eq(2), anyString());
    verifyNoInteractions(judge, retries);
  }

  @Test
  void runningDuplicateDoesNotFailOtherWorker() {
    submit(1, 1);
    consumer.receiveMessage(message("7", 1, false), channel);
    verifyNoInteractions(judge, retries, publisher, failures);
  }

  @Test
  void terminalDuplicateDoesNotJudgeAgain() {
    submit(2, 0);
    consumer.receiveMessage(message("7", 0, false), channel);
    verifyNoInteractions(judge, retries, publisher);
  }

  @Test
  void malformedMessageIsSentToDeadQueue() {
    consumer.receiveMessage(message("invalid", null, false), channel);
    verify(publisher).dead(isNull(), eq(0), anyString());
    verifyNoInteractions(client, judge);
  }

  @Test
  void failedRedeliveryRepublishesAuditWithoutExecutingTask() throws Exception {
    submit(3, 3);
    consumer.receiveMessage(message("7", 3, true), channel);
    verify(publisher).dead(eq(7L), eq(3), anyString());
    verifyNoInteractions(judge, retries);
    verify(channel).basicAck(11L, false);
  }

  @Test
  void lookupFailureStopsWithoutAck() throws Exception {
    when(client.getQuestionSubmitById(7L)).thenThrow(new IllegalStateException("database offline"));
    consumer.receiveMessage(message("7", 0, false), channel);
    verify(failures).stop(any());
    verify(channel, never()).basicAck(anyLong(), anyBoolean());
  }
}
// 原测试实现（直接 FAILED/NACK 的旧行为）保留供对照：
// package com.luoyujia.yjoj.judge.rabbitmq;
//
// import com.luoyujia.yjoj.client.service.QuestionFeignClient;
// import com.luoyujia.yjoj.judge.judge.JudgeService;
// import com.luoyujia.yjoj.model.entity.QuestionSubmit;
// import com.luoyujia.yjoj.model.enums.QuestionSubmitStatusEnum;
// import com.rabbitmq.client.Channel;
// import org.junit.jupiter.api.Test;
// import org.mockito.ArgumentCaptor;
//
// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.mockito.ArgumentMatchers.anyLong;
// import static org.mockito.Mockito.*;
//
// class MyMessageConsumerTest {
//
//     @Test
//     void acknowledgesSuccessfulJudge() throws Exception {
//         JudgeService judgeService = mock(JudgeService.class);
//         QuestionFeignClient questionClient = mock(QuestionFeignClient.class);
//         Channel channel = mock(Channel.class);
//         QuestionSubmit waiting = new QuestionSubmit();
//         waiting.setId(7L);
//         waiting.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
//         when(questionClient.getQuestionSubmitById(7L)).thenReturn(waiting);
//
//         new MyMessageConsumer(judgeService, questionClient)
//                 .receiveMessage("7", channel, 11L);
//
//         verify(judgeService).doJudge(7L);
//         verify(channel).basicAck(11L, false);
//         verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
//     }
//
//     @Test
//     void writesFailedAndNacksOnError() throws Exception {
//         JudgeService judgeService = mock(JudgeService.class);
//         QuestionFeignClient questionClient = mock(QuestionFeignClient.class);
//         Channel channel = mock(Channel.class);
//         QuestionSubmit waiting = new QuestionSubmit();
//         waiting.setId(7L);
//         waiting.setQuestionId(3L);
//         waiting.setStatus(QuestionSubmitStatusEnum.WAITING.getValue());
//         when(questionClient.getQuestionSubmitById(7L)).thenReturn(waiting);
//         when(questionClient.failQuestionSubmit(any())).thenReturn(true);
//         doThrow(new IllegalStateException("sandbox down")).when(judgeService).doJudge(7L);
//
//         new MyMessageConsumer(judgeService, questionClient)
//                 .receiveMessage("7", channel, 12L);
//
//         ArgumentCaptor<QuestionSubmit> failed = ArgumentCaptor.forClass(QuestionSubmit.class);
//         verify(questionClient).failQuestionSubmit(failed.capture());
//         assertEquals(QuestionSubmitStatusEnum.FAILED.getValue(), failed.getValue().getStatus());
//         verify(channel).basicNack(12L, false, false);
//     }
// }
