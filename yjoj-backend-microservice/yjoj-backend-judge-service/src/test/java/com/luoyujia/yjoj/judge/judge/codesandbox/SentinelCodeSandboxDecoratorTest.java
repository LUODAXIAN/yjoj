package com.luoyujia.yjoj.judge.judge.codesandbox;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.*;
import com.luoyujia.yjoj.judge.config.SandboxProtectionProperties;
import com.luoyujia.yjoj.judge.exception.*;
import com.luoyujia.yjoj.model.codesandbox.*;
import java.util.Collections;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SentinelCodeSandboxDecoratorTest {
  @AfterEach
  void clear() {
    DegradeRuleManager.loadRules(Collections.emptyList());
  }

  @Test
  void disabledCallsOriginal() {
    CodeSandbox delegate = mock(CodeSandbox.class);
    ExecuteCodeResponse result = new ExecuteCodeResponse();
    result.setStatus(1);
    when(delegate.executeCode(any())).thenReturn(result);
    assertSame(
        result,
        new SentinelCodeSandboxDecorator(delegate, new SandboxProtectionProperties())
            .executeCode(new ExecuteCodeRequest()));
  }

  @Test
  void compileErrorIsNormalResult() {
    CodeSandbox delegate = mock(CodeSandbox.class);
    ExecuteCodeResponse result = new ExecuteCodeResponse();
    result.setStatus(3);
    when(delegate.executeCode(any())).thenReturn(result);
    SandboxProtectionProperties p = new SandboxProtectionProperties();
    p.setSandboxCircuitEnabled(true);
    assertSame(
        result,
        new SentinelCodeSandboxDecorator(delegate, p).executeCode(new ExecuteCodeRequest()));
  }

  @Test
  void repeatedSystemFailureOpensCircuitAndHalfOpenRecovers() throws Exception {
    DegradeRule rule =
        new DegradeRule(SentinelCodeSandboxDecorator.RESOURCE)
            .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
            .setCount(0.1)
            .setTimeWindow(1)
            .setMinRequestAmount(1)
            .setStatIntervalMs(1000);
    DegradeRuleManager.loadRules(Collections.singletonList(rule));
    CodeSandbox delegate = mock(CodeSandbox.class);
    when(delegate.executeCode(any())).thenThrow(new RetryableJudgeException("offline"));
    SandboxProtectionProperties p = new SandboxProtectionProperties();
    p.setSandboxCircuitEnabled(true);
    SentinelCodeSandboxDecorator sandbox = new SentinelCodeSandboxDecorator(delegate, p);
    assertThrows(
        RetryableJudgeException.class, () -> sandbox.executeCode(new ExecuteCodeRequest()));
    assertThrows(
        RetryableJudgeException.class, () -> sandbox.executeCode(new ExecuteCodeRequest()));
    verify(delegate, times(1)).executeCode(any());
    Thread.sleep(1200);
    ExecuteCodeResponse success = new ExecuteCodeResponse();
    success.setStatus(1);
    doReturn(success).when(delegate).executeCode(any());
    assertSame(success, sandbox.executeCode(new ExecuteCodeRequest()));
  }
}
