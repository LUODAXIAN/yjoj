package com.luoyujia.yjoj.judge.codesandbox.impl;

import com.luoyujia.yjoj.judge.codesandbox.CodeSandbox;
import com.luoyujia.yjoj.judge.codesandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.judge.codesandbox.model.ExecuteCodeResponse;
import com.luoyujia.yjoj.judge.codesandbox.model.JudgeInfo;
import com.luoyujia.yjoj.model.enums.JudgeInfoMessageEnum;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 示例代码沙箱（仅为了跑通业务流程）
 */
@Slf4j
public class ExampleCodeSandbox implements CodeSandbox {
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        List<String> inputList = executeCodeRequest.getInputList();
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(inputList);
        executeCodeResponse.setMessage("测试执行成功");
        executeCodeResponse.setStatus(1); // 沙箱成功状态，与提交记录的状态枚举不同。
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(JudgeInfoMessageEnum.ACCEPTED.getValue());
        judgeInfo.setMemory(100L);
        judgeInfo.setTime(100L);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }
}

