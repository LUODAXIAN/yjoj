package com.luoyujia.yjoj.gateway.filter;

import java.nio.charset.StandardCharsets;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 阻止外部请求通过网关访问仅供 OpenFeign 使用的 /inner/ 接口。 */
@Component
public class GlobalAuthFilter implements GlobalFilter, Ordered {

  private final AntPathMatcher antPathMatcher = new AntPathMatcher();

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest serverHttpRequest = exchange.getRequest();
    String path = serverHttpRequest.getURI().getPath();
    // 内部接口只能通过服务发现直连，不能从网关公开。
    if (antPathMatcher.match("/**/inner/**", path)) {
      ServerHttpResponse response = exchange.getResponse();
      response.setStatusCode(HttpStatus.FORBIDDEN);
      response
          .getHeaders()
          .setContentType(
              new org.springframework.http.MediaType("text", "plain", StandardCharsets.UTF_8));
      DataBufferFactory dataBufferFactory = response.bufferFactory();
      DataBuffer dataBuffer = dataBufferFactory.wrap("无权限".getBytes(StandardCharsets.UTF_8));
      return response.writeWith(Mono.just(dataBuffer));
    }
    return chain.filter(exchange);
  }

  @Override
  public int getOrder() {
    // 在路由转发前优先拦截内部接口。
    // 原顺序：return 0; 内部路径必须先于 Sentinel (-1) 执行。
    return -100;
  }
}
