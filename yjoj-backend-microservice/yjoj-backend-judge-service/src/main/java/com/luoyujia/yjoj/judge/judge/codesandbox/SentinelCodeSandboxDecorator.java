package com.luoyujia.yjoj.judge.judge.codesandbox;

import com.alibaba.csp.sentinel.*;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.luoyujia.yjoj.judge.config.SandboxProtectionProperties;
import com.luoyujia.yjoj.judge.exception.RetryableJudgeException;
import com.luoyujia.yjoj.model.codesandbox.*;

/** 工厂创建的对象不是 Spring 代理：在真正的调用边界手动埋点。 */
public class SentinelCodeSandboxDecorator implements CodeSandbox {
  public static final String RESOURCE = "yjoj.sandbox.execute";
  private final CodeSandbox delegate;
  private final SandboxProtectionProperties properties;

  public SentinelCodeSandboxDecorator(
      CodeSandbox delegate, SandboxProtectionProperties properties) {
    this.delegate = delegate;
    this.properties = properties;
  }

  @Override
  public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
    if (!properties.isSandboxCircuitEnabled()) return execute(request);
    Entry entry = null;
    try {
      entry = SphU.entry(RESOURCE);
      return execute(request);
    } catch (BlockException blocked) {
      throw new RetryableJudgeException("沙箱调用已熔断，等待恢复", blocked);
    } catch (RetryableJudgeException temporary) {
      // 只记录系统依赖故障；WA、CE、RE、TLE、MLE 是正常结果。
      if (entry != null) Tracer.traceEntry(temporary, entry);
      throw temporary;
    } finally {
      if (entry != null) entry.exit();
    }
  }

  private ExecuteCodeResponse execute(ExecuteCodeRequest request) {
    ExecuteCodeResponse response = delegate.executeCode(request);
    if (response == null || Integer.valueOf(2).equals(response.getStatus())) {
      throw new RetryableJudgeException("代码沙箱返回系统错误");
    }
    return response;
  }
}
