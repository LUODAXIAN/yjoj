package com.luoyujia.yjoj.judge;

import com.luoyujia.yjoj.judge.strategy.DefaultJudgeStrategy;
import com.luoyujia.yjoj.judge.strategy.JavaLanguageJudgeStrategy;
import com.luoyujia.yjoj.judge.strategy.JudgeContext;
import com.luoyujia.yjoj.judge.strategy.JudgeStrategy;
import com.luoyujia.yjoj.judge.codesandbox.model.JudgeInfo;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import org.springframework.stereotype.Service;

/**
 * 判题管理（简化调用）
 */
@Service
public class JudgeManager {

    /**
     * 执行判题
     *
     * @param judgeContext
     * @return
     */
    JudgeInfo doJudge(JudgeContext judgeContext) {
        QuestionSubmit questionSubmit = judgeContext.getQuestionSubmit();
        String language = questionSubmit.getLanguage();
        JudgeStrategy judgeStrategy = new DefaultJudgeStrategy();
        if ("java".equals(language)) {
            judgeStrategy = new JavaLanguageJudgeStrategy();
        }
        return judgeStrategy.doJudge(judgeContext);
    }

}
