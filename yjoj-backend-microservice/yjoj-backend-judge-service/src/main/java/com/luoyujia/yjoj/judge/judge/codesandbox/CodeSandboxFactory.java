package com.luoyujia.yjoj.judge.judge.codesandbox;

import com.luoyujia.yjoj.judge.judge.codesandbox.impl.ExampleCodeSandbox;
import com.luoyujia.yjoj.judge.judge.codesandbox.impl.RemoteCodeSandbox;
import com.luoyujia.yjoj.judge.judge.codesandbox.impl.ThirdPartyCodeSandbox;

/**
 * 代码沙箱工厂（根据字符串参数创建指定的代码沙箱实例）
 */
public class CodeSandboxFactory {

    /**
     * 创建代码沙箱示例
     *
     * @param type 沙箱类型
     * @return
     */
    public static CodeSandbox newInstance(String type) {
        return newInstance(type, "http://localhost:8090", "auth", "change-me");
    }

    /**
     * 保留原来的类型选择，同时允许部署时配置远程沙箱地址和鉴权参数。
     */
    public static CodeSandbox newInstance(String type, String url, String authHeader, String authSecret) {
        switch (type) {
            case "example":
                return new ExampleCodeSandbox();
            case "remote":
                return new RemoteCodeSandbox(url, authHeader, authSecret);
            case "thirdParty":
                return new ThirdPartyCodeSandbox();
            default:
                return new ExampleCodeSandbox();
        }
    }
}
