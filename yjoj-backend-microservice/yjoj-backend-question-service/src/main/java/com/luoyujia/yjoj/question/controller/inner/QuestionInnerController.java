package com.luoyujia.yjoj.question.controller.inner;

import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.question.service.QuestionService;
import com.luoyujia.yjoj.question.service.QuestionSubmitService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅供服务间调用；Gateway 会拒绝所有 /inner/ 路径。 */
@RestController
@RequestMapping("/inner")
public class QuestionInnerController implements QuestionFeignClient {

  private final QuestionService questionService;
  private final QuestionSubmitService submitService;

  public QuestionInnerController(
      QuestionService questionService, QuestionSubmitService submitService) {
    this.questionService = questionService;
    this.submitService = submitService;
  }

  @Override
  public Question getQuestionById(long questionId) {
    return questionService.getById(questionId);
  }

  @Override
  public QuestionSubmit getQuestionSubmitById(long submitId) {
    return submitService.getById(submitId);
  }

  @Override
  // 原签名：claimQuestionSubmit(long submitId)
  public boolean claimQuestionSubmit(long submitId, int attempt) {
    return submitService.claimWaitingSubmit(submitId, attempt);
  }

  @Override
  // 原签名：completeQuestionSubmit(QuestionSubmit result, boolean accepted)
  public boolean completeQuestionSubmit(QuestionSubmit result, boolean accepted, int attempt) {
    return submitService.completeJudge(result, accepted, attempt);
  }

  @Override
  // 原签名：failQuestionSubmit(QuestionSubmit result)
  public boolean failQuestionSubmit(QuestionSubmit result, int attempt) {
    return submitService.failJudge(result, attempt);
  }

  @Override
  public boolean prepareRetry(long submitId, int currentAttempt) {
    return submitService.prepareRetry(submitId, currentAttempt);
  }
}
