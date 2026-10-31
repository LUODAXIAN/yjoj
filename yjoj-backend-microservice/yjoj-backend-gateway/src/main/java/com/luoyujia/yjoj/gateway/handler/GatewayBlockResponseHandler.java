package com.luoyujia.yjoj.gateway.handler;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 限流是明确拒绝，不能返回 HTTP 200 或虚构成功结果。 */
public class GatewayBlockResponseHandler implements BlockRequestHandler {
  @Override
  public Mono<ServerResponse> handleRequest(ServerWebExchange exchange, Throwable ex) {
    Map<String, Object> body = new LinkedHashMap<>();
    // 与公共 ErrorCode 的契约一致；网关不引入包含 Servlet 的公共模块。
    body.put("code", 42900);
    body.put("data", null);
    body.put("message", "请求过于频繁，请稍后重试");
    return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(body);
  }
}
