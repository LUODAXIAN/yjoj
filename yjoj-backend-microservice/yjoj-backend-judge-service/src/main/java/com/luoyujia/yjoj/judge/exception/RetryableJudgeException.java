package com.luoyujia.yjoj.judge.exception;

/** 临时依赖故障，可进入有限延迟重试。 */
public class RetryableJudgeException extends RuntimeException {
  public RetryableJudgeException(String message) {
    super(message);
  }

  public RetryableJudgeException(String message, Throwable cause) {
    super(message, cause);
  }
}
