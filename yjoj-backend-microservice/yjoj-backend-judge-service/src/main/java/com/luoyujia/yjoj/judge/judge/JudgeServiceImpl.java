package com.luoyujia.yjoj.judge.judge;

import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.client.service.QuestionFeignClient;
import com.luoyujia.yjoj.common.common.ErrorCode;
import com.luoyujia.yjoj.common.exception.BusinessException;
import com.luoyujia.yjoj.judge.config.SandboxProtectionProperties;
import com.luoyujia.yjoj.judge.exception.NonRetryableJudgeException;
import com.luoyujia.yjoj.judge.judge.codesandbox.CodeSandbox;
import com.luoyujia.yjoj.judge.judge.codesandbox.CodeSandboxFactory;
import com.luoyujia.yjoj.judge.judge.codesandbox.CodeSandboxProxy;
import com.luoyujia.yjoj.judge.judge.codesandbox.SentinelCodeSandboxDecorator;
import com.luoyujia.yjoj.judge.judge.strategy.JudgeContext;
import com.luoyujia.yjoj.model.codesandbox.ExecuteCodeRequest;
import com.luoyujia.yjoj.model.codesandbox.ExecuteCodeResponse;
import com.luoyujia.yjoj.model.codesandbox.JudgeInfo;
import com.luoyujia.yjoj.model.dto.question.JudgeCase;
import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.model.enums.JudgeInfoMessageEnum;
import com.luoyujia.yjoj.model.enums.QuestionSubmitStatusEnum;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JudgeServiceImpl implements JudgeService {

  @Resource private QuestionFeignClient questionFeignClient;

  @Resource private JudgeManager judgeManager;

  @Resource private SandboxProtectionProperties protection;

  @Value("${codesandbox.type:remote}")
  private String type;

  @Value("${codesandbox.url:http://localhost:8090}")
  private String sandboxUrl;

  @Value("${codesandbox.auth-header:auth}")
  private String authHeader;

  @Value("${codesandbox.auth-secret:change-me}")
  private String authSecret;

  @Override
  // 原入口：public QuestionSubmit doJudge(long questionSubmitId)
  public QuestionSubmit doJudge(long questionSubmitId, int attempt) {
    // 1）传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
    QuestionSubmit questionSubmit = questionFeignClient.getQuestionSubmitById(questionSubmitId);
    if (questionSubmit == null) {
      throw new NonRetryableJudgeException("提交信息不存在");
    }
    Long questionId = questionSubmit.getQuestionId();
    // 2）如果题目提交状态不为等待中，就不用重复执行了
    // 原实现保留供对照；新逻辑增加执行轮次及可靠确认，避免重复生效。
    // if (!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())) {
    //             throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
    //         }
    if (!Integer.valueOf(0).equals(questionSubmit.getStatus())
        || !Integer.valueOf(attempt).equals(questionSubmit.getJudgeAttempt()))
      return questionSubmit;
    // 3）更改判题（题目提交）的状态为 “判题中”，防止重复执行
    // 原调用：claimQuestionSubmit(questionSubmitId);
    boolean update = questionFeignClient.claimQuestionSubmit(questionSubmitId, attempt);
    // 原实现抢占失败抛异常；现在重复消息正常结束，不能把他人的 RUNNING 写成 FAILED。
    if (!update) return questionFeignClient.getQuestionSubmitById(questionSubmitId);
    try {
      Question question = questionFeignClient.getQuestionById(questionId);
      if (question == null) {
        throw new NonRetryableJudgeException("题目不存在");
      }
      // 4）调用沙箱，获取到执行结果
      CodeSandbox codeSandbox =
          CodeSandboxFactory.newInstance(type, sandboxUrl, authHeader, authSecret);
      codeSandbox = new CodeSandboxProxy(codeSandbox);
      codeSandbox = new SentinelCodeSandboxDecorator(codeSandbox, protection);
      String language = questionSubmit.getLanguage();
      String code = questionSubmit.getCode();
      // 获取输入用例
      String judgeCaseStr = question.getJudgeCase();
      List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
      List<String> inputList =
          judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
      ExecuteCodeRequest executeCodeRequest =
          ExecuteCodeRequest.builder().code(code).language(language).inputList(inputList).build();
      ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
      if (executeCodeResponse == null
          || Integer.valueOf(2).equals(executeCodeResponse.getStatus())) {
        throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "代码沙箱执行失败");
      }
      JudgeInfo sandboxInfo =
          executeCodeResponse.getJudgeInfo() == null
              ? new JudgeInfo()
              : executeCodeResponse.getJudgeInfo();
      if (Integer.valueOf(3).equals(executeCodeResponse.getStatus())
          && StringUtils.isBlank(sandboxInfo.getMessage())) {
        sandboxInfo.setMessage(
            StringUtils.defaultIfBlank(
                executeCodeResponse.getMessage(), JudgeInfoMessageEnum.RUNTIME_ERROR.getValue()));
      }
      List<String> outputList = executeCodeResponse.getOutputList();
      // 5）根据沙箱的执行结果，设置题目的判题状态和信息
      JudgeContext judgeContext = new JudgeContext();
      judgeContext.setJudgeInfo(sandboxInfo);
      judgeContext.setInputList(inputList);
      judgeContext.setOutputList(outputList);
      judgeContext.setJudgeCaseList(judgeCaseList);
      judgeContext.setQuestion(question);
      judgeContext.setQuestionSubmit(questionSubmit);
      JudgeInfo judgeInfo = judgeManager.doJudge(judgeContext);
      // 6）修改数据库中的判题结果
      QuestionSubmit questionSubmitUpdate = new QuestionSubmit();
      questionSubmitUpdate.setId(questionSubmitId);
      questionSubmitUpdate.setStatus(QuestionSubmitStatusEnum.SUCCEED.getValue());
      questionSubmitUpdate.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
      questionSubmitUpdate.setQuestionId(questionId);
      update =
          questionFeignClient.completeQuestionSubmit(
              questionSubmitUpdate,
              JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage()),
              attempt);
      // 原实现：更新失败直接抛出系统错误。现在可能是旧轮次回写，被拒绝不能覆盖新结果。
      // if (!update) { throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误"); }
      if (!update) return questionFeignClient.getQuestionSubmitById(questionSubmitId);
      QuestionSubmit questionSubmitResult =
          questionFeignClient.getQuestionSubmitById(questionSubmitId);
      return questionSubmitResult;
    } catch (Exception e) {
      // 异常也必须写回终态，避免提交一直停留在判题中。
      // 原实现：fail(questionSubmit);
      // 重试或最终失败由消费者统一决定，不能在这里过早写成 FAILED。
      throw e;
    }
  }

  // 原实现保留供对照；新逻辑增加执行轮次及可靠确认，避免重复生效。
  // private void fail(QuestionSubmit submit) {
  //         JudgeInfo info = new JudgeInfo();
  //         info.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
  //         QuestionSubmit failed = new QuestionSubmit();
  //         failed.setId(submit.getId());
  //         failed.setQuestionId(submit.getQuestionId());
  //         failed.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
  //         failed.setJudgeInfo(JSONUtil.toJsonStr(info));
  //         questionFeignClient.failQuestionSubmit(failed);
  //     }
}
