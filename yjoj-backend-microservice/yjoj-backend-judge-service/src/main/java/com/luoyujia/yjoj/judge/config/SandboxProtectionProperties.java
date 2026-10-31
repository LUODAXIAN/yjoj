package com.luoyujia.yjoj.judge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "yjoj.protection")
public class SandboxProtectionProperties {
  private boolean sandboxCircuitEnabled = false;
  private int circuitWindowMs = 10000;
  private int circuitMinRequests = 5;
  private double circuitErrorRatio = 0.5;
  private int circuitOpenSeconds = 10;
}
