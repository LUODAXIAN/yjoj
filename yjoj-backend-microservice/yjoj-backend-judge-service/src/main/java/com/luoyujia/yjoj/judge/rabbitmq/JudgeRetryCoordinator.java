package com.luoyujia.yjoj.judge.rabbitmq;

import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import com.luoyujia.yjoj.judge.config.JudgeRetryProperties;
import com.luoyujia.yjoj.judge.exception.RetryableJudgeException;
import com.luoyujia.yjoj.model.codesandbox.JudgeInfo;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.model.enums.*;
import javax.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** 管理重试状态与投递顺序；任何投递异常向上传播，原消息不可被确认。 */
@Component
public class JudgeRetryCoordinator {
  private final QuestionFeignClient client;
  private final ConfirmedJudgeMessagePublisher publisher;
  private final JudgeRetryProperties properties;

  public JudgeRetryCoordinator(
      QuestionFeignClient client,
      ConfirmedJudgeMessagePublisher publisher,
      JudgeRetryProperties properties) {
    this.client = client;
    this.publisher = publisher;
    this.properties = properties;
  }

  @PostConstruct
  public void validate() {
    if (properties.getMaxRetries() < 0
        || properties.getMaxRetries() > 3
        || properties.getConfirmTimeoutMs() <= 0)
      throw new IllegalArgumentException("重试次数必须为 0..3，发布确认超时必须大于 0");
  }

  public void handleFailure(QuestionSubmit submit, int attempt, Exception error) {
    if (error instanceof RetryableJudgeException
        && properties.isEnabled()
        && attempt < properties.getMaxRetries()) {
      if (client.prepareRetry(submit.getId(), attempt)) {
        publisher.retry(submit.getId(), attempt + 1);
        return;
      }
      QuestionSubmit latest = client.getQuestionSubmitById(submit.getId());
      if (latest != null
          && Integer.valueOf(0).equals(latest.getStatus())
          && Integer.valueOf(attempt + 1).equals(latest.getJudgeAttempt())) {
        // 准备重试接口可能已提交但响应丢失；只补发，不再增加轮次。
        publisher.retry(submit.getId(), attempt + 1);
        return;
      }
      if (latest != null
          && (!Integer.valueOf(attempt).equals(latest.getJudgeAttempt())
              || Integer.valueOf(2).equals(latest.getStatus())
              || Integer.valueOf(3).equals(latest.getStatus()))) return;
      throw new IllegalStateException("无法确定重试状态，停止消费等待排查");
    }
    JudgeInfo info = new JudgeInfo();
    info.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
    QuestionSubmit failed = new QuestionSubmit();
    failed.setId(submit.getId());
    failed.setQuestionId(submit.getQuestionId());
    failed.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
    failed.setJudgeInfo(JSONUtil.toJsonStr(info));
    if (client.failQuestionSubmit(failed, attempt)) {
      publisher.dead(submit.getId(), attempt, error.getMessage());
    } else {
      QuestionSubmit latest = client.getQuestionSubmitById(submit.getId());
      if (latest != null
          && Integer.valueOf(attempt).equals(latest.getJudgeAttempt())
          && !Integer.valueOf(2).equals(latest.getStatus())
          && !Integer.valueOf(3).equals(latest.getStatus()))
        throw new IllegalStateException("最终失败状态未写入");
    }
  }
}
