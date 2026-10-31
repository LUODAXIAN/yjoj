package com.luoyujia.yjoj;

import com.luoyujia.yjoj.judge.codesandbox.model.JudgeInfo;
import com.luoyujia.yjoj.judge.strategy.JavaLanguageJudgeStrategy;
import com.luoyujia.yjoj.judge.strategy.JudgeContext;
import com.luoyujia.yjoj.model.dto.question.JudgeCase;
import com.luoyujia.yjoj.model.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeStrategyTest {

    @Test
    void acceptsNullMemoryAndCorrectOutput() {
        JudgeInfo sandboxInfo = new JudgeInfo();
        sandboxInfo.setTime(10L);
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setInput("1 2");
        judgeCase.setOutput("3");
        Question question = new Question();
        question.setJudgeConfig("{\"timeLimit\":1000,\"memoryLimit\":262144}");
        JudgeContext context = new JudgeContext();
        context.setJudgeInfo(sandboxInfo);
        context.setOutputList(Arrays.asList("3\n"));
        context.setJudgeCaseList(Arrays.asList(judgeCase));
        context.setQuestion(question);

        JudgeInfo result = new JavaLanguageJudgeStrategy().doJudge(context);

        assertEquals("Accepted", result.getMessage());
        assertEquals(0L, result.getMemory());
    }

    @Test
    void reportsWrongAnswer() {
        JudgeInfo sandboxInfo = new JudgeInfo();
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setOutput("3");
        Question question = new Question();
        question.setJudgeConfig("{\"timeLimit\":1000,\"memoryLimit\":262144}");
        JudgeContext context = new JudgeContext();
        context.setJudgeInfo(sandboxInfo);
        context.setOutputList(Arrays.asList("4"));
        context.setJudgeCaseList(Arrays.asList(judgeCase));
        context.setQuestion(question);

        assertEquals("Wrong Answer",
                new JavaLanguageJudgeStrategy().doJudge(context).getMessage());
    }

    @Test
    void preservesSandboxErrorAndChecksResourceLimits() {
        JudgeInfo sandboxError = new JudgeInfo();
        sandboxError.setMessage("Compile Error");
        JudgeContext errorContext = context(sandboxError, 1000, 262144);
        assertEquals("Compile Error",
                new JavaLanguageJudgeStrategy().doJudge(errorContext).getMessage());

        JudgeInfo timeout = new JudgeInfo();
        // Java 策略沿用原实现的 10 秒额外执行时间。
        timeout.setTime(11001L);
        JudgeContext timeoutContext = context(timeout, 1000, 262144);
        assertEquals("Time Limit Exceeded",
                new JavaLanguageJudgeStrategy().doJudge(timeoutContext).getMessage());

        JudgeInfo memoryExceeded = new JudgeInfo();
        memoryExceeded.setMemory(262145L);
        JudgeContext memoryContext = context(memoryExceeded, 1000, 262144);
        assertEquals("Memory Limit Exceeded",
                new JavaLanguageJudgeStrategy().doJudge(memoryContext).getMessage());
    }

    private JudgeContext context(JudgeInfo judgeInfo, long timeLimit, long memoryLimit) {
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setOutput("3");
        Question question = new Question();
        question.setJudgeConfig("{\"timeLimit\":" + timeLimit
                + ",\"memoryLimit\":" + memoryLimit + "}");
        JudgeContext context = new JudgeContext();
        context.setJudgeInfo(judgeInfo);
        context.setOutputList(Arrays.asList("3"));
        context.setJudgeCaseList(Arrays.asList(judgeCase));
        context.setQuestion(question);
        return context;
    }
}
