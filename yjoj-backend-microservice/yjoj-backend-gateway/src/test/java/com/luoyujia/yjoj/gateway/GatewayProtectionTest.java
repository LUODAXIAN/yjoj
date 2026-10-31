package com.luoyujia.yjoj.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.luoyujia.yjoj.gateway.config.*;
import com.luoyujia.yjoj.gateway.filter.GlobalAuthFilter;
import com.luoyujia.yjoj.gateway.handler.*;
import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class GatewayProtectionTest {
  @Test
  void internalPathsAlwaysDeniedBeforeSentinel() {
    MockServerWebExchange e =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/user/inner/get/id?userId=1"));
    GatewayFilterChain chain = mock(GatewayFilterChain.class);
    GlobalAuthFilter filter = new GlobalAuthFilter();
    filter.filter(e, chain).block();
    assertEquals(HttpStatus.FORBIDDEN, e.getResponse().getStatusCode());
    assertTrue(filter.getOrder() < -1);
    verifyNoInteractions(chain);
  }

  @Test
  void exactBusinessGroupsProduceFourRules() {
    GatewayFlowProperties p = new GatewayFlowProperties();
    p.setGatewayFlowEnabled(true);
    new SentinelGatewayConfig(p).loadRules();
    assertEquals(4, GatewayRuleManager.getRules().size());
  }

  @Test
  void blockResponseIs429() {
    MockServerWebExchange e =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/question/get/vo"));
    assertEquals(
        429,
        new GatewayBlockResponseHandler()
            .handleRequest(e, new RuntimeException())
            .block()
            .rawStatusCode());
  }

  @Test
  void connectionFailureUses503AndJson() {
    MockServerWebExchange e =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/question/get/vo"));
    new GatewayUnavailableHandler(new ObjectMapper()).handle(e, new ConnectException()).block();
    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getResponse().getStatusCode());
    assertTrue(e.getResponse().getBodyAsString().block().contains("50300"));
  }
}
