package com.luoyujia.yjoj.gateway.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.util.*;
import java.util.concurrent.TimeoutException;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import reactor.core.publisher.Mono;

/** 只处理网关转发故障，业务异常和 Sentinel 拒绝交还各自处理器。 */
@Component
@Order(-3)
public class GatewayUnavailableHandler implements ErrorWebExceptionHandler {
  private final ObjectMapper mapper;

  public GatewayUnavailableHandler(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
    if (exchange.getResponse().isCommitted() || !isUnavailable(error)) return Mono.error(error);
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", 50300);
    body.put("data", null);
    body.put("message", "服务暂不可用，请稍后重试");
    exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
    try {
      byte[] bytes = mapper.writeValueAsBytes(body);
      return exchange
          .getResponse()
          .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    } catch (Exception encodingError) {
      return Mono.error(encodingError);
    }
  }

  private boolean isUnavailable(Throwable error) {
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof ConnectException || cause instanceof TimeoutException) return true;
      if (cause instanceof ResponseStatusException
          && ((ResponseStatusException) cause).getStatus() == HttpStatus.SERVICE_UNAVAILABLE)
        return true;
      if (cause.getClass().getSimpleName().contains("ConnectTimeout")) return true;
    }
    return false;
  }
}
