package com.luoyujia.yjoj.sandbox.utils;

import com.luoyujia.yjoj.sandbox.model.ExecuteMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 进程执行工具：并行消费 stdout/stderr，限制输出体积，并负责超时终止。
 */
public final class ProcessUtils {

    private static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    private ProcessUtils() {
    }

    public static ExecuteMessage runProcessAndGetMessage(
            Process process, String operation, long timeoutMillis) {
        long startedAt = System.nanoTime();
        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        ExecuteMessage message = new ExecuteMessage();
        try {
            boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                message.setTimeout(true);
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
            message.setExitValue(finished ? process.exitValue() : -1);
            message.setMessage(stdout.get(2, TimeUnit.SECONDS));
            message.setErrorMessage(stderr.get(2, TimeUnit.SECONDS));
        } catch (Exception e) {
            process.destroyForcibly();
            message.setExitValue(-1);
            message.setErrorMessage(operation + "失败：" + e.getMessage());
        } finally {
            message.setTime(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        }
        return message;
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int total = 0;
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    int writable = Math.min(length, DEFAULT_MAX_OUTPUT_BYTES - total);
                    if (writable > 0) {
                        output.write(buffer, 0, writable);
                        total += writable;
                    }
                    if (total >= DEFAULT_MAX_OUTPUT_BYTES) {
                        break;
                    }
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }
}
