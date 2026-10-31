package com.luoyujia.yjoj.judge.codesandbox.impl;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.common.ErrorCode;
import com.luoyujia.yjoj.exception.BusinessException;
import com.luoyujia.yjoj.judge.codesandbox.CodeSandbox;
import com.luoyujia.yjoj.judge.codesandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.judge.codesandbox.model.ExecuteCodeResponse;
import org.apache.commons.lang3.StringUtils;

/**
 * 调用独立部署的代码沙箱。
 */
public class RemoteCodeSandbox implements CodeSandbox {

    private final String executeUrl;
    private final String authHeader;
    private final String authSecret;

    public RemoteCodeSandbox(String baseUrl, String authHeader, String authSecret) {
        this.executeUrl = StringUtils.removeEnd(baseUrl, "/") + "/executeCode";
        this.authHeader = authHeader;
        this.authSecret = authSecret;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        try (HttpResponse response = HttpUtil.createPost(executeUrl)
                .header(authHeader, authSecret)
                .body(JSONUtil.toJsonStr(request))
                .timeout(30_000)
                .execute()) {
            String body = response.body();
            if (!response.isOk() || StringUtils.isBlank(body)) {
                throw new BusinessException(ErrorCode.API_REQUEST_ERROR,
                        "代码沙箱调用失败，HTTP " + response.getStatus());
            }
            return JSONUtil.toBean(body, ExecuteCodeResponse.class);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "代码沙箱不可用：" + e.getMessage());
        }
    }
}
