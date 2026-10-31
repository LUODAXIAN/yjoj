package com.luoyujia.yjoj;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.luoyujia.yjoj.exception.BusinessException;
import com.luoyujia.yjoj.model.entity.Question;
import com.luoyujia.yjoj.model.entity.QuestionSubmit;
import com.luoyujia.yjoj.model.entity.User;
import com.luoyujia.yjoj.model.vo.QuestionSubmitVO;
import com.luoyujia.yjoj.model.vo.QuestionVO;
import com.luoyujia.yjoj.service.UserService;
import com.luoyujia.yjoj.service.impl.QuestionServiceImpl;
import com.luoyujia.yjoj.service.impl.QuestionSubmitServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class QuestionPrivacyAndValidationTest {

    @Test
    void publicQuestionViewDoesNotExposeAnswerOrJudgeCases() {
        assertFalse(Arrays.stream(QuestionVO.class.getDeclaredFields())
                .anyMatch(field -> "answer".equals(field.getName())
                        || "judgeCase".equals(field.getName())));
        try {
            assertNotNull(User.class.getDeclaredField("userPassword")
                    .getAnnotation(JsonIgnore.class));
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void validatesCasesAndPositiveResourceLimits() {
        QuestionServiceImpl service = new QuestionServiceImpl();
        Question valid = question("[{\"input\":\"1 2\",\"output\":\"3\"}]",
                "{\"timeLimit\":1000,\"memoryLimit\":262144}");
        service.validQuestion(valid, true);

        assertThrows(BusinessException.class,
                () -> service.validQuestion(question("[]",
                        "{\"timeLimit\":1000,\"memoryLimit\":262144}"), true));
        assertThrows(BusinessException.class,
                () -> service.validQuestion(question("[{\"input\":\"\",\"output\":\"\"}]",
                        "{\"timeLimit\":0,\"memoryLimit\":262144}"), true));
    }

    @Test
    void hidesOtherUsersCodeButKeepsOwnersCode() {
        QuestionSubmitServiceImpl service = new QuestionSubmitServiceImpl();
        UserService userService = mock(UserService.class);
        ReflectionTestUtils.setField(service, "userService", userService);
        QuestionSubmit submit = new QuestionSubmit();
        submit.setId(1L);
        submit.setUserId(7L);
        submit.setCode("public class Main {}");
        submit.setJudgeInfo("{}");

        User other = new User();
        other.setId(8L);
        assertNull(service.getQuestionSubmitVO(submit, other).getCode());

        User owner = new User();
        owner.setId(7L);
        QuestionSubmitVO ownerView = service.getQuestionSubmitVO(submit, owner);
        assertEquals("public class Main {}", ownerView.getCode());
    }

    private Question question(String cases, String config) {
        Question question = new Question();
        question.setTitle("A + B");
        question.setContent("计算两数之和");
        question.setTags("[\"入门\"]");
        question.setJudgeCase(cases);
        question.setJudgeConfig(config);
        return question;
    }
}
