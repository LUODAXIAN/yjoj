package com.luoyujia.yjoj.common.constant;

/** 判题消息拓扑名称。 */
public final class JudgeMqConstants {

  public static final String EXCHANGE = "yjoj.judge.exchange";
  public static final String QUEUE = "yjoj.judge.queue";
  public static final String ROUTING_KEY = "yjoj.judge.routing";

  public static final String RETRY_EXCHANGE = "yjoj.judge.retry.exchange";
  public static final String DEAD_EXCHANGE = "yjoj.judge.dead.exchange";
  public static final String DEAD_QUEUE = "yjoj.judge.dead.queue";
  public static final String DEAD_ROUTING_KEY = "yjoj.judge.dead.routing";
  public static final String ATTEMPT_HEADER = "x-yjoj-attempt";
  public static final int MAX_ATTEMPT = 3;
  private static final int[] DELAY_SECONDS = {5, 30, 120};

  /** 固定 TTL 每队列一个级别，避免混合消息 TTL 带来的队头阻塞。 */
  public static int retryDelaySeconds(int attempt) {
    if (attempt < 1 || attempt > MAX_ATTEMPT) throw new IllegalArgumentException("无效重试轮次");
    return DELAY_SECONDS[attempt - 1];
  }

  public static String retryQueue(int attempt) {
    return "yjoj.judge.retry." + retryDelaySeconds(attempt) + "s.queue";
  }

  public static String retryRoutingKey(int attempt) {
    return "yjoj.judge.retry." + retryDelaySeconds(attempt) + "s";
  }

  private JudgeMqConstants() {}
}
