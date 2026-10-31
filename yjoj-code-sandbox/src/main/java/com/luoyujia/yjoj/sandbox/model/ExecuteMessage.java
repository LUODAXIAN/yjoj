package com.luoyujia.yjoj.sandbox.model;

import lombok.Data;

/**
 * 单个操作系统进程的执行结果。
 */
@Data
public class ExecuteMessage {

    private Integer exitValue;
    private String message;
    private String errorMessage;
    private Long time;
    private Long memory;
    private boolean timeout;
}
