package com.luoyujia.yjoj.judge;

import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.common.ErrorCode;
import com.luoyujia.yjoj.exception.BusinessException;
import com.luoyujia.yjoj.judge.codesandbox.CodeSandbox;
import com.luoyujia.yjoj.judge.codesandbox.CodeSandboxFactory;
import com.luoyujia.yjoj.judge.codesandbox.CodeSandboxProxy;
import com.luoyujia.yjoj.judge.codesandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.judge.codesandbox.model.ExecuteCodeResponse;
import com.luoyujia.yjoj.judge.strategy.JudgeContext;
import com.luoyujia.yjoj.model.dto.question.JudgeCase;
import com.luoyujia.yjoj.judge.codesandbox.model.JudgeInfo;
import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.model.enums.QuestionSubmitStatusEnum;
import com.luoyujia.yjoj.service.QuestionService;
import com.luoyujia.yjoj.service.QuestionSubmitService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import com.luoyujia.yjoj.model.enums.JudgeInfoMessageEnum;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.util.stream.Collectors;

@Service
public class JudgeServiceImpl implements JudgeService {

    @Resource
    private QuestionService questionService;

    @Resource
    private QuestionSubmitService questionSubmitService;

    @Resource
    private JudgeManager judgeManager;

    @Value("${codesandbox.type:remote}")
    private String type;

    @Value("${codesandbox.url:http://localhost:8090}")
    private String sandboxUrl;
    @Value("${codesandbox.auth-header:auth}")
    private String authHeader;
    @Value("${codesandbox.auth-secret:change-me}")
    private String authSecret;


    @Override
    public QuestionSubmit doJudge(long questionSubmitId) {
        // 1）传入题目的提交 id，获取到对应的题目、提交信息（包含代码、编程语言等）
        QuestionSubmit questionSubmit = questionSubmitService.getById(questionSubmitId);
        if (questionSubmit == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "提交信息不存在");
        }
        Long questionId = questionSubmit.getQuestionId();
        // 2）如果题目提交状态不为等待中，就不用重复执行了
        if (!questionSubmit.getStatus().equals(QuestionSubmitStatusEnum.WAITING.getValue())) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }
        // 3）更改判题（题目提交）的状态为 “判题中”，防止重复执行
        boolean update = questionSubmitService.update(new LambdaUpdateWrapper<QuestionSubmit>()
                .eq(QuestionSubmit::getId, questionSubmitId)
                .eq(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.WAITING.getValue())
                .set(QuestionSubmit::getStatus, QuestionSubmitStatusEnum.RUNNING.getValue()));
        if (!update) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "题目正在判题中");
        }
        try {
            Question question = questionService.getById(questionId);
            if (question == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "题目不存在");
            }
            // 4）调用沙箱，获取到执行结果
            CodeSandbox codeSandbox = CodeSandboxFactory.newInstance(type, sandboxUrl, authHeader, authSecret);
            codeSandbox = new CodeSandboxProxy(codeSandbox);
            String language = questionSubmit.getLanguage();
            String code = questionSubmit.getCode();
            // 获取输入用例
            String judgeCaseStr = question.getJudgeCase();
            List<JudgeCase> judgeCaseList = JSONUtil.toList(judgeCaseStr, JudgeCase.class);
            List<String> inputList = judgeCaseList.stream().map(JudgeCase::getInput).collect(Collectors.toList());
            ExecuteCodeRequest executeCodeRequest = ExecuteCodeRequest.builder()
                    .code(code)
                    .language(language)
                    .inputList(inputList)
                    .build();
            ExecuteCodeResponse executeCodeResponse = codeSandbox.executeCode(executeCodeRequest);
            if (executeCodeResponse == null || Integer.valueOf(2).equals(executeCodeResponse.getStatus())) {
                throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "代码沙箱执行失败");
            }
            JudgeInfo sandboxInfo = executeCodeResponse.getJudgeInfo() == null
                    ? new JudgeInfo() : executeCodeResponse.getJudgeInfo();
            if (Integer.valueOf(3).equals(executeCodeResponse.getStatus())
                    && StringUtils.isBlank(sandboxInfo.getMessage())) {
                sandboxInfo.setMessage(StringUtils.defaultIfBlank(
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
            update = questionSubmitService.updateById(questionSubmitUpdate);
            if (!update) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "题目状态更新错误");
            }
            if (JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())) {
                questionService.update(new LambdaUpdateWrapper<Question>()
                        .eq(Question::getId, questionId)
                        .setSql("acceptedNum = acceptedNum + 1"));
            }
            QuestionSubmit questionSubmitResult = questionSubmitService.getById(questionSubmitId);
            return questionSubmitResult;
        } catch (Exception e) {
            // 异常也必须写回终态，避免提交一直停留在判题中。
            markFailed(questionSubmitId);
            throw e;
        }
    }

    private void markFailed(long submitId) {
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.SYSTEM_ERROR.getValue());
        QuestionSubmit failed = new QuestionSubmit();
        failed.setId(submitId);
        failed.setStatus(QuestionSubmitStatusEnum.FAILED.getValue());
        failed.setJudgeInfo(JSONUtil.toJsonStr(judgeInfo));
        questionSubmitService.updateById(failed);
    }
}
