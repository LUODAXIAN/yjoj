package com.luoyujia.yjoj.judge.judge.strategy;

import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.model.codesandbox.JudgeInfo;
import com.luoyujia.yjoj.model.dto.question.JudgeCase;
import com.luoyujia.yjoj.model.dto.question.JudgeConfig;
import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.enums.JudgeInfoMessageEnum;

import java.util.List;
import org.apache.commons.lang3.StringUtils;
import java.util.Optional;

/**
 * Java 程序的判题策略
 */
public class JavaLanguageJudgeStrategy implements JudgeStrategy {

    /**
     * 执行判题
     * @param judgeContext
     * @return
     */
    @Override
    public JudgeInfo doJudge(JudgeContext judgeContext) {
        JudgeInfo judgeInfo = Optional.ofNullable(judgeContext.getJudgeInfo()).orElse(new JudgeInfo());
        Long memory = Optional.ofNullable(judgeInfo.getMemory()).orElse(0L);
        Long time = Optional.ofNullable(judgeInfo.getTime()).orElse(0L);
        List<String> inputList = judgeContext.getInputList();
        List<String> outputList = judgeContext.getOutputList();
        Question question = judgeContext.getQuestion();
        List<JudgeCase> judgeCaseList = judgeContext.getJudgeCaseList();
        JudgeInfoMessageEnum judgeInfoMessageEnum = JudgeInfoMessageEnum.ACCEPTED;
        JudgeInfo judgeInfoResponse = new JudgeInfo();
        judgeInfoResponse.setMemory(memory);
        judgeInfoResponse.setTime(time);
        // 编译、运行、超时等错误由沙箱给出，不能再覆盖成答案错误。
        if (StringUtils.isNotBlank(judgeInfo.getMessage())
                && !JudgeInfoMessageEnum.ACCEPTED.getValue().equals(judgeInfo.getMessage())) {
            judgeInfoResponse.setMessage(judgeInfo.getMessage());
            return judgeInfoResponse;
        }
        // 先判断沙箱执行的结果输出数量是否和预期输出数量相等
        if (outputList == null || outputList.size() != judgeCaseList.size()) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // 依次判断每一项输出和预期输出是否相等
        for (int i = 0; i < judgeCaseList.size(); i++) {
            JudgeCase judgeCase = judgeCaseList.get(i);
            if (!StringUtils.equals(StringUtils.stripEnd(judgeCase.getOutput(), null),
                    StringUtils.stripEnd(outputList.get(i), null))) {
                judgeInfoMessageEnum = JudgeInfoMessageEnum.WRONG_ANSWER;
                judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
                return judgeInfoResponse;
            }
        }
        // 判断题目限制
        String judgeConfigStr = question.getJudgeConfig();
        JudgeConfig judgeConfig = JSONUtil.toBean(judgeConfigStr, JudgeConfig.class);
        Long needMemoryLimit = judgeConfig.getMemoryLimit();
        Long needTimeLimit = judgeConfig.getTimeLimit();
        if (memory > needMemoryLimit) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.MEMORY_LIMIT_EXCEEDED;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        // 原方案：为 Java 执行固定扣除 10 秒。保留供对照，当前不启用。
        // long JAVA_PROGRAM_TIME_COST = 10000L;
        // if ((time - JAVA_PROGRAM_TIME_COST) > needTimeLimit) {
        // 沙箱返回单用例最大执行耗时（毫秒），不包含编译和容器创建时间，直接与题目限制比较。
        if (time > needTimeLimit) {
            judgeInfoMessageEnum = JudgeInfoMessageEnum.TIME_LIMIT_EXCEEDED;
            judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
            return judgeInfoResponse;
        }
        judgeInfoResponse.setMessage(judgeInfoMessageEnum.getValue());
        return judgeInfoResponse;
    }
}
