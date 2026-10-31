package com.luoyujia.yjoj.sandbox;


import com.luoyujia.yjoj.sandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeResponse;

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
