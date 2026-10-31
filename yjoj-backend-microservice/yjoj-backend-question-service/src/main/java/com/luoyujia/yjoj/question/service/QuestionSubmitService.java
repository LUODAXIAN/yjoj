package com.luoyujia.yjoj.question.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.luoyujia.yjoj.model.dto.questionsubmit.QuestionSubmitAddRequest;
import com.luoyujia.yjoj.model.dto.questionsubmit.QuestionSubmitQueryRequest;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.model.entity.User;
import com.luoyujia.yjoj.model.vo.QuestionSubmitVO;

/** 提交记录业务接口，负责提交创建、查询脱敏以及判题状态的条件更新。 */
public interface QuestionSubmitService extends IService<QuestionSubmit> {

  long doQuestionSubmit(QuestionSubmitAddRequest request, User loginUser);

  QueryWrapper<QuestionSubmit> getQueryWrapper(QuestionSubmitQueryRequest request);

  QuestionSubmitVO getQuestionSubmitVO(QuestionSubmit submit, User loginUser);

  Page<QuestionSubmitVO> getQuestionSubmitVOPage(Page<QuestionSubmit> page, User loginUser);

  /** 仅在 WAITING 状态时抢占提交。 */
  // 原签名：boolean claimWaitingSubmit(long submitId);
  boolean claimWaitingSubmit(long submitId, int attempt);

  /** 完成 RUNNING 提交，并在 Accepted 时维护题目通过数。 */
  // 原签名：boolean completeJudge(QuestionSubmit result, boolean accepted);
  boolean completeJudge(QuestionSubmit result, boolean accepted, int attempt);

  /** 将尚未结束的提交标记为 FAILED。 */
  // 原签名：boolean failJudge(QuestionSubmit result);
  boolean failJudge(QuestionSubmit result, int attempt);

  /** 原子进入下一轮 WAITING，旧消息不能再次递增轮次。 */
  boolean prepareRetry(long submitId, int currentAttempt);
}
