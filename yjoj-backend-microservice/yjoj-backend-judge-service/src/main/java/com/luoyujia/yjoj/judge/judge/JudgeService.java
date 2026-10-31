package com.luoyujia.yjoj.judge.judge;

import com.luoyujia.yjoj.model.entity.QuestionSubmit;

/** 判题服务 */
public interface JudgeService {

  /**
   * 判题
   *
   * @param questionSubmitId
   * @return
   */
  // 原签名：QuestionSubmit doJudge(long questionSubmitId);
  default QuestionSubmit doJudge(long questionSubmitId) {
    return doJudge(questionSubmitId, 0);
  }

  /** 仅执行消息指定轮次；数据库条件更新是最终并发保护。 */
  QuestionSubmit doJudge(long questionSubmitId, int attempt);
}
