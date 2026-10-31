package com.luoyujia.yjoj.judge.rabbitmq;

import static org.mockito.Mockito.*;

import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import com.luoyujia.yjoj.judge.config.JudgeRetryProperties;
import com.luoyujia.yjoj.judge.exception.*;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class JudgeRetryCoordinatorTest {
  private final QuestionFeignClient client = mock(QuestionFeignClient.class);
  private final ConfirmedJudgeMessagePublisher publisher =
      mock(ConfirmedJudgeMessagePublisher.class);
  private final JudgeRetryProperties properties = new JudgeRetryProperties();
  private final JudgeRetryCoordinator coordinator =
      new JudgeRetryCoordinator(client, publisher, properties);

  private QuestionSubmit submit() {
    QuestionSubmit s = new QuestionSubmit();
    s.setId(7L);
    s.setQuestionId(3L);
    return s;
  }

  @Test
  void prepareBeforePublishAndNeverWritesFailedDuringRetry() {
    properties.setEnabled(true);
    when(client.prepareRetry(7L, 0)).thenReturn(true);
    coordinator.handleFailure(submit(), 0, new RetryableJudgeException("offline"));
    InOrder order = inOrder(client, publisher);
    order.verify(client).prepareRetry(7L, 0);
    order.verify(publisher).retry(7L, 1);
    verify(client, never()).failQuestionSubmit(any(), anyInt());
  }

  @Test
  void exhaustedAttemptFinallyFails() {
    properties.setEnabled(true);
    when(client.failQuestionSubmit(any(), eq(3))).thenReturn(true);
    coordinator.handleFailure(submit(), 3, new RetryableJudgeException("offline"));
    InOrder order = inOrder(client, publisher);
    order.verify(client).failQuestionSubmit(any(), eq(3));
    order.verify(publisher).dead(eq(7L), eq(3), anyString());
    verify(client, never()).prepareRetry(anyLong(), anyInt());
  }

  @Test
  void disabledRetryFinallyFails() {
    when(client.failQuestionSubmit(any(), eq(0))).thenReturn(true);
    coordinator.handleFailure(submit(), 0, new RetryableJudgeException("offline"));
    verify(publisher).dead(eq(7L), eq(0), anyString());
    verify(client, never()).prepareRetry(anyLong(), anyInt());
  }

  @Test
  void credentialsAndProtocolErrorsAreNotRetried() {
    properties.setEnabled(true);
    when(client.failQuestionSubmit(any(), eq(0))).thenReturn(true);
    coordinator.handleFailure(submit(), 0, new NonRetryableJudgeException("HTTP 403"));
    verify(client, never()).prepareRetry(anyLong(), anyInt());
  }
}
