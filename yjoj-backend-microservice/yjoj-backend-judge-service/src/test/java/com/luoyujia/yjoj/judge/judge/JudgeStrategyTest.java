package com.luoyujia.yjoj.judge.judge;

import com.luoyujia.yjoj.judge.judge.strategy.JavaLanguageJudgeStrategy;
import com.luoyujia.yjoj.judge.judge.strategy.JudgeContext;
import com.luoyujia.yjoj.model.codesandbox.JudgeInfo;
import com.luoyujia.yjoj.model.dto.question.JudgeCase;
import com.luoyujia.yjoj.model.entity.Question;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JudgeStrategyTest {

    @Test
    void handlesAcceptedWrongAnswerAndNullMemory() {
        JudgeInfo sandbox = new JudgeInfo();
        sandbox.setTime(5L);
        JudgeCase judgeCase = new JudgeCase();
        judgeCase.setOutput("3");
        Question question = new Question();
        question.setJudgeConfig("{\"timeLimit\":1000,\"memoryLimit\":262144}");
        JudgeContext context = new JudgeContext();
        context.setJudgeInfo(sandbox);
        context.setJudgeCaseList(Arrays.asList(judgeCase));
        context.setOutputList(Arrays.asList("3\n"));
        context.setQuestion(question);

        assertEquals("Accepted", new JavaLanguageJudgeStrategy().doJudge(context).getMessage());

        context.setOutputList(Arrays.asList("4"));
        assertEquals("Wrong Answer",
                new JavaLanguageJudgeStrategy().doJudge(context).getMessage());
    }

    @Test
    void preservesSandboxFailure() {
        JudgeInfo sandbox = new JudgeInfo();
        sandbox.setMessage("Compile Error");
        JudgeContext context = new JudgeContext();
        context.setJudgeInfo(sandbox);

        assertEquals("Compile Error",
                new JavaLanguageJudgeStrategy().doJudge(context).getMessage());
    }
}
