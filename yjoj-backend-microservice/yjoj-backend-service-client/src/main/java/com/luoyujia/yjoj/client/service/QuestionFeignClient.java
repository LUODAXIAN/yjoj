package com.luoyujia.yjoj.client.service;

import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/** 判题服务访问题目数据和回写判题状态的内部契约。 这些路径只能由服务间调用，网关会拒绝外部访问 /inner/。 */
@FeignClient(name = "yjoj-backend-question-service", path = "/api/question/inner")
public interface QuestionFeignClient {

  @GetMapping("/get/id")
  Question getQuestionById(@RequestParam("questionId") long questionId);

  @GetMapping("/question_submit/get/id")
  QuestionSubmit getQuestionSubmitById(@RequestParam("submitId") long submitId);

  @PostMapping("/question_submit/claim")
  // 原签名：boolean claimQuestionSubmit(@RequestParam("submitId") long submitId);
  boolean claimQuestionSubmit(
      @RequestParam("submitId") long submitId,
      @RequestParam(value = "attempt", defaultValue = "0") int attempt);

  @PostMapping("/question_submit/complete")
  boolean completeQuestionSubmit(
      @RequestBody QuestionSubmit result,
      // 原参数列表以 accepted 结束；新增 attempt 防止旧轮次覆盖结果。
      @RequestParam("accepted") boolean accepted,
      @RequestParam(value = "attempt", defaultValue = "0") int attempt);

  @PostMapping("/question_submit/fail")
  // 原签名：boolean failQuestionSubmit(@RequestBody QuestionSubmit result);
  boolean failQuestionSubmit(
      @RequestBody QuestionSubmit result,
      @RequestParam(value = "attempt", defaultValue = "0") int attempt);

  /** 仅当前 RUNNING 轮次能准备下一轮；提交成功后才发布延迟消息。 */
  @PostMapping("/question_submit/prepare_retry")
  boolean prepareRetry(
      @RequestParam("submitId") long submitId, @RequestParam("attempt") int currentAttempt);
}
