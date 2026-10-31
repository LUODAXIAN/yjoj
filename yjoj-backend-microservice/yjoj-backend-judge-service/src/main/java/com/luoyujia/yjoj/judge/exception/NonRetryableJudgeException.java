package com.luoyujia.yjoj.judge.exception;

/** 参数、鉴权或协议错误，不应重复调用。 */
public class NonRetryableJudgeException extends RuntimeException {
  public NonRetryableJudgeException(String message) {
    super(message);
  }

  public NonRetryableJudgeException(String message, Throwable cause) {
    super(message, cause);
  }
}
