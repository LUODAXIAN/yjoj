package com.luoyujia.yjoj.judge.judge.codesandbox;

import com.luoyujia.yjoj.model.codesandbox.ExecuteCodeRequest;
import com.luoyujia.yjoj.model.codesandbox.ExecuteCodeResponse;

/**
 * 代码沙箱接口定义
 */
public interface CodeSandbox {

    /**
     * 执行代码
     *
     * @param executeCodeRequest
     * @return
     */
    ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest);
}
