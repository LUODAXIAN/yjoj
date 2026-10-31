package com.luoyujia.yjoj.judge.judge.codesandbox.impl;

import cn.hutool.http.*;
import cn.hutool.json.JSONUtil;
import com.luoyujia.yjoj.judge.exception.*;
import com.luoyujia.yjoj.judge.judge.codesandbox.CodeSandbox;
import com.luoyujia.yjoj.model.codesandbox.*;
import org.apache.commons.lang3.StringUtils;

/** HTTP 沙箱客户端：只按故障类别重试，不按错误文案猜测。 */
public class RemoteCodeSandbox implements CodeSandbox {
  private final String executeUrl, authHeader, authSecret;

  public RemoteCodeSandbox(String baseUrl, String authHeader, String authSecret) {
    this.executeUrl = StringUtils.removeEnd(baseUrl, "/") + "/executeCode";
    this.authHeader = authHeader;
    this.authSecret = authSecret;
  }

  @Override
  public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
    try (HttpResponse response =
        HttpUtil.createPost(executeUrl)
            .header(authHeader, authSecret)
            .body(JSONUtil.toJsonStr(request))
            .timeout(30000)
            .execute()) {
      int status = response.getStatus();
      if (status == 502 || status == 503 || status == 504)
        throw new RetryableJudgeException("沙箱临时不可用，HTTP " + status);
      if (!response.isOk()) throw new NonRetryableJudgeException("沙箱请求被拒绝，HTTP " + status);
      String body = response.body();
      if (StringUtils.isBlank(body)) throw new NonRetryableJudgeException("沙箱响应为空");
      try {
        ExecuteCodeResponse result = JSONUtil.toBean(body, ExecuteCodeResponse.class);
        if (result == null
            || result.getStatus() == null
            || result.getStatus() < 1
            || result.getStatus() > 3) throw new NonRetryableJudgeException("沙箱响应缺少有效执行状态");
        if (Integer.valueOf(2).equals(result.getStatus()))
          throw new RetryableJudgeException("沙箱返回系统错误");
        return result;
      } catch (RetryableJudgeException | NonRetryableJudgeException classified) {
        throw classified;
      } catch (Exception invalid) {
        throw new NonRetryableJudgeException("沙箱响应格式不合法", invalid);
      }
    } catch (RetryableJudgeException | NonRetryableJudgeException classified) {
      throw classified;
    } catch (Exception network) {
      throw new RetryableJudgeException("代码沙箱连接失败", network);
    }
  }

  // region 原客户端实现：保留对照；原来把全部异常合并为 API_REQUEST_ERROR。
  // @Override
  //     public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
  //         try (HttpResponse response = HttpUtil.createPost(executeUrl)
  //                 .header(authHeader, authSecret)
  //                 .body(JSONUtil.toJsonStr(request))
  //                 .timeout(30_000)
  //                 .execute()) {
  //             String body = response.body();
  //             if (!response.isOk() || StringUtils.isBlank(body)) {
  //                 throw new BusinessException(ErrorCode.API_REQUEST_ERROR,
  //                         "代码沙箱调用失败，HTTP " + response.getStatus());
  //             }
  //             return JSONUtil.toBean(body, ExecuteCodeResponse.class);
  //         } catch (BusinessException e) {
  //             throw e;
  //         } catch (Exception e) {
  //             throw new BusinessException(ErrorCode.API_REQUEST_ERROR, "代码沙箱不可用：" +
  // e.getMessage());
  //         }
  //     }
  // endregion
}
