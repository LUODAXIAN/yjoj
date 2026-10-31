package com.luoyujia.yjoj.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.*;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.*;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.luoyujia.yjoj.gateway.handler.GatewayBlockResponseHandler;
import java.util.*;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

/** 只保护明确列出的业务 API；不改变现有路由，不对文档施加业务限流。 */
@Configuration
@EnableConfigurationProperties(GatewayFlowProperties.class)
@ConditionalOnProperty(name = "yjoj.protection.gateway-flow-enabled", havingValue = "true")
public class SentinelGatewayConfig {
  private final GatewayFlowProperties properties;

  public SentinelGatewayConfig(GatewayFlowProperties properties) {
    this.properties = properties;
  }

  @PostConstruct
  public void loadRules() {
    Set<ApiDefinition> apis = new HashSet<>();
    apis.add(api("yjoj.login", "/api/user/login"));
    apis.add(api("yjoj.register", "/api/user/register"));
    apis.add(api("yjoj.question.read", "/api/question/get/vo", "/api/question/list/page/vo"));
    apis.add(api("yjoj.submit", "/api/question/question_submit/do"));
    GatewayApiDefinitionManager.loadApiDefinitions(apis);
    Set<GatewayFlowRule> rules = new HashSet<>();
    rules.add(rule("yjoj.login", properties.getLoginQps(), 1, true));
    rules.add(rule("yjoj.register", properties.getRegisterPerMinute(), 60, true));
    rules.add(rule("yjoj.question.read", properties.getQueryQps(), 1, false));
    rules.add(rule("yjoj.submit", properties.getSubmitQps(), 1, false));
    GatewayRuleManager.loadRules(rules);
    GatewayCallbackManager.setBlockHandler(new GatewayBlockResponseHandler());
  }

  private ApiDefinition api(String name, String... paths) {
    Set<ApiPredicateItem> items = new HashSet<>();
    for (String path : paths) items.add(new ApiPathPredicateItem().setPattern(path));
    return new ApiDefinition(name).setPredicateItems(items);
  }

  private GatewayFlowRule rule(String resource, double count, long seconds, boolean byIp) {
    if (count <= 0) throw new IllegalArgumentException("限流阈值必须大于 0：" + resource);
    GatewayFlowRule rule =
        new GatewayFlowRule(resource)
            .setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME)
            .setCount(count)
            .setIntervalSec(seconds);
    if (byIp) {
      // CLIENT_IP 使用实际远端连接地址，不读取用户自行传入的用户 ID。
      rule.setParamItem(
          new GatewayParamFlowItem()
              .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP));
    }
    return rule;
  }

  @Bean
  @Order(-1)
  public SentinelGatewayFilter sentinelGatewayFilter() {
    return new SentinelGatewayFilter();
  }

  @Bean
  @Order(-2)
  public SentinelGatewayBlockExceptionHandler sentinelBlockExceptionHandler(
      ObjectProvider<List<ViewResolver>> resolvers, ServerCodecConfigurer codecs) {
    return new SentinelGatewayBlockExceptionHandler(
        resolvers.getIfAvailable(Collections::emptyList), codecs);
  }
}
