package com.luoyujia.yjoj.judge.rabbitmq;

import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.stereotype.Component;

/** 基础设施操作失败时保留未确认消息并停止监听，避免立即重入队形成热循环。 */
@Component
@Slf4j
public class JudgeConsumerFailureHandler {
  public static final String LISTENER_ID = "yjojJudgeConsumer";
  private final RabbitListenerEndpointRegistry registry;
  private final ExecutorService control =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread thread = new Thread(r, "judge-consumer-control");
            thread.setDaemon(true);
            return thread;
          });

  public JudgeConsumerFailureHandler(RabbitListenerEndpointRegistry registry) {
    this.registry = registry;
  }

  public void stop(Exception error) {
    log.error("判题消费者将停止，原消息未 ACK；修复依赖后重启判题服务恢复消费", error);
    // stop 可能等待消费线程退出，不能在该消费线程上直接调用。
    control.execute(
        () -> {
          MessageListenerContainer container = registry.getListenerContainer(LISTENER_ID);
          if (container != null) container.stop();
        });
  }

  @PreDestroy
  public void close() {
    control.shutdown();
  }
}
