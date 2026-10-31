package com.luoyujia.yjoj.sandbox.controller;

import com.luoyujia.yjoj.sandbox.CodeSandbox;
import com.luoyujia.yjoj.sandbox.JavaDockerCodeSandbox;
import com.luoyujia.yjoj.sandbox.JavaNativeCodeSandbox;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;

/**
 * 沙箱 HTTP 入口。鉴权密钥和实现类型均可用环境变量覆盖。
 */
@RestController
public class MainController {

    private final JavaNativeCodeSandbox nativeSandbox;
    private final JavaDockerCodeSandbox dockerSandbox;

    @Value("${sandbox.type:native}")
    private String sandboxType;

    @Value("${sandbox.auth-header:auth}")
    private String authHeaderName;

    @Value("${sandbox.auth-secret:change-me}")
    private String authSecret;

    public MainController(JavaNativeCodeSandbox nativeSandbox, JavaDockerCodeSandbox dockerSandbox) {
        this.nativeSandbox = nativeSandbox;
        this.dockerSandbox = dockerSandbox;
    }

    @GetMapping("/health")
    public String healthCheck() {
        return "ok";
    }

    @PostMapping("/executeCode")
    public ExecuteCodeResponse executeCode(
            @RequestBody ExecuteCodeRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        if (!authSecret.equals(httpRequest.getHeader(authHeaderName))) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return error("Forbidden");
        }
        if (request == null || !StringUtils.hasText(request.getCode())
                || !"java".equalsIgnoreCase(request.getLanguage())
                || request.getInputList() == null) {
            httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return error("仅支持包含测试输入的 Java 代码");
        }
        return currentSandbox().executeCode(request);
    }

    private CodeSandbox currentSandbox() {
        if ("native".equalsIgnoreCase(sandboxType)) {
            return nativeSandbox;
        }
        if ("docker".equalsIgnoreCase(sandboxType)) {
            return dockerSandbox;
        }
        throw new IllegalStateException("不支持的 sandbox.type：" + sandboxType);
    }

    private ExecuteCodeResponse error(String message) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        response.setOutputList(Collections.emptyList());
        response.setMessage(message);
        response.setStatus(2);
        return response;
    }
}
