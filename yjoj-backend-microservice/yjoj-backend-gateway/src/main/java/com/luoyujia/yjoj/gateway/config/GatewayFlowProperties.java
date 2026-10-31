package com.luoyujia.yjoj.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本地网关保护配置；开关关闭时不安装 Sentinel 业务限流过滤器。 */
@ConfigurationProperties(prefix = "yjoj.protection")
public class GatewayFlowProperties {
  private boolean gatewayFlowEnabled = false;
  private double loginQps = 2;
  private double registerPerMinute = 5;
  private double queryQps = 20;
  private double submitQps = 2;

  public boolean isGatewayFlowEnabled() {
    return gatewayFlowEnabled;
  }

  public void setGatewayFlowEnabled(boolean value) {
    gatewayFlowEnabled = value;
  }

  public double getLoginQps() {
    return loginQps;
  }

  public void setLoginQps(double value) {
    loginQps = value;
  }

  public double getRegisterPerMinute() {
    return registerPerMinute;
  }

  public void setRegisterPerMinute(double value) {
    registerPerMinute = value;
  }

  public double getQueryQps() {
    return queryQps;
  }

  public void setQueryQps(double value) {
    queryQps = value;
  }

  public double getSubmitQps() {
    return submitQps;
  }

  public void setSubmitQps(double value) {
    submitQps = value;
  }
}
