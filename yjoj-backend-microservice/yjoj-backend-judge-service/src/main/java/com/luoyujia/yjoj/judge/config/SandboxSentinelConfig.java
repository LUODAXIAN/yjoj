package com.luoyujia.yjoj.judge.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.degrade.*;
import com.luoyujia.yjoj.judge.judge.codesandbox.SentinelCodeSandboxDecorator;
import java.util.Collections;
import javax.annotation.PostConstruct;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 配置只在启动时加载；关闭开关后保护装饰器直接委托原沙箱。 */
@Configuration
@EnableConfigurationProperties({SandboxProtectionProperties.class, JudgeRetryProperties.class})
public class SandboxSentinelConfig {
  private final SandboxProtectionProperties properties;

  public SandboxSentinelConfig(SandboxProtectionProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void loadRules() {
    if (!properties.isSandboxCircuitEnabled()) return;
    DegradeRule rule =
        new DegradeRule(SentinelCodeSandboxDecorator.RESOURCE)
            .setGrade(RuleConstant.DEGRADE_GRADE_EXCEPTION_RATIO)
            .setCount(properties.getCircuitErrorRatio())
            .setTimeWindow(properties.getCircuitOpenSeconds())
            .setMinRequestAmount(properties.getCircuitMinRequests())
            .setStatIntervalMs(properties.getCircuitWindowMs());
    DegradeRuleManager.loadRules(Collections.singletonList(rule));
  }
}
