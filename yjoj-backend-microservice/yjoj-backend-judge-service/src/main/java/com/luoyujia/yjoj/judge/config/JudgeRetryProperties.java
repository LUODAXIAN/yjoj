package com.luoyujia.yjoj.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 轮次校验始终启用；关闭重试只是不再安排新的尝试。 */
@Data
@ConfigurationProperties(prefix = "yjoj.judge.retry")
public class JudgeRetryProperties {
  private boolean enabled = false;
  private int maxRetries = 3;
  private long confirmTimeoutMs = 5000;
}
