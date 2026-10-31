package com.luoyujia.yjoj.sandbox;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.luoyujia.yjoj.sandbox.model.ExecuteMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Docker Java 沙箱：禁网、只读根文件系统、限制 CPU/内存，并在 finally 中删除容器。
 */
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {

    private static final AtomicBoolean IMAGE_READY = new AtomicBoolean(false);

    @Value("${sandbox.docker.image:eclipse-temurin:8-jre-alpine}")
    private String image = "eclipse-temurin:8-jre-alpine";

    @Override
    public List<ExecuteMessage> runFile(File codeFile, List<String> inputList) {
        String containerId = null;
        try (DockerClient docker = createDockerClient()) {
            ensureImage(docker);
            HostConfig hostConfig = HostConfig.newHostConfig()
                    // JVM 堆限制为 256 MB，容器需为元空间和本地内存预留余量。
                    .withMemory(512L * 1024 * 1024)
                    .withMemorySwap(512L * 1024 * 1024)
                    .withCpuCount(1L)
                    .withNetworkMode("none")
                    .withReadonlyRootfs(true)
                    .withCapDrop(Capability.ALL)
                    .withBinds(new Bind(codeFile.getParentFile().getAbsolutePath(),
                            new Volume("/app"), AccessMode.ro));

            CreateContainerResponse created = docker.createContainerCmd(image)
                    .withHostConfig(hostConfig)
                    .withCmd("sh", "-c", "while true; do sleep 1; done")
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            containerId = created.getId();
            docker.startContainerCmd(containerId).exec();

            List<ExecuteMessage> results = new ArrayList<>();
            for (String input : inputList) {
                List<String> command = new ArrayList<>(
                        Arrays.asList("java", "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", "/app", "Main"));
                if (input != null && !input.trim().isEmpty()) {
                    command.addAll(Arrays.asList(input.trim().split("\\s+")));
                }
                ExecuteMessage message = exec(docker, containerId, command);
                results.add(message);
                if (message.isTimeout() || !Integer.valueOf(0).equals(message.getExitValue())) {
                    break;
                }
            }
            return results;
        } catch (Exception e) {
            throw new IllegalStateException("Docker 沙箱执行失败：" + e.getMessage(), e);
        } finally {
            if (containerId != null) {
                try (DockerClient cleanup = createDockerClient()) {
                    cleanup.removeContainerCmd(containerId).withForce(true).exec();
                } catch (Exception ignored) {
                    // 容器可能已被 Docker 守护进程删除；不覆盖原始执行异常。
                }
            }
        }
    }

    /**
     * 显式使用 HttpClient5，支持 Windows 命名管道及 Linux Unix Socket。
     * 执行与清理共用此创建方式，避免默认传输实现无法连接 Docker Desktop。
     */
    private DockerClient createDockerClient() {
        DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }

    private void ensureImage(DockerClient docker) throws InterruptedException {
        if (IMAGE_READY.get()) {
            return;
        }
        synchronized (IMAGE_READY) {
            if (!IMAGE_READY.get()) {
                // 优先复用本地镜像，避免每次重启后依赖镜像仓库网络。
                try {
                    docker.inspectImageCmd(image).exec();
                } catch (NotFoundException e) {
                    boolean completed = docker.pullImageCmd(image)
                            .exec(new PullImageResultCallback())
                            .awaitCompletion(5, TimeUnit.MINUTES);
                    if (!completed) {
                        throw new IllegalStateException("Docker 镜像拉取超时：" + image);
                    }
                    // 确认镜像存在后才标记就绪，避免后续创建容器时使用无效缓存。
                    docker.inspectImageCmd(image).exec();
                }
                IMAGE_READY.set(true);
            }
        }
    }

    private ExecuteMessage exec(
            DockerClient docker, String containerId, List<String> command) throws Exception {
        ExecCreateCmdResponse created = docker.execCreateCmd(containerId)
                .withCmd(command.toArray(new String[0]))
                .withAttachStdout(true)
                .withAttachStderr(true)
                .exec();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        long[] maxMemory = {0L};
        ResultCallback<Statistics> stats = docker.statsCmd(containerId)
                .exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onNext(Statistics value) {
                        if (value.getMemoryStats() != null && value.getMemoryStats().getUsage() != null) {
                            maxMemory[0] = Math.max(maxMemory[0], value.getMemoryStats().getUsage());
                        }
                    }
                    @Override public void onStart(Closeable closeable) { }
                    @Override public void onError(Throwable throwable) { }
                    @Override public void onComplete() { }
                    @Override public void close() throws IOException { }
                });

        long startedAt = System.nanoTime();
        ExecStartResultCallback callback = new ExecStartResultCallback() {
            @Override
            public void onNext(Frame frame) {
                String value = new String(frame.getPayload(), StandardCharsets.UTF_8);
                if (frame.getStreamType() == StreamType.STDERR) {
                    stderr.append(value);
                } else {
                    stdout.append(value);
                }
                super.onNext(frame);
            }

        };
        boolean completed;
        try {
            // 在关闭回调前记录等待结果，避免超时后的流关闭被误认为执行完成。
            completed = docker.execStartCmd(created.getId()).exec(callback)
                    .awaitCompletion(timeoutMillis, TimeUnit.MILLISECONDS);
        } finally {
            stats.close();
            callback.close();
        }

        ExecuteMessage message = new ExecuteMessage();
        message.setTime(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
        message.setMemory(maxMemory[0] / 1024);
        message.setMessage(stdout.toString());
        message.setErrorMessage(stderr.toString());
        message.setTimeout(!completed);
        if (completed) {
            InspectExecResponse inspected = docker.inspectExecCmd(created.getId()).exec();
            message.setExitValue(inspected.getExitCodeLong() == null
                    ? -1 : inspected.getExitCodeLong().intValue());
        } else {
            message.setExitValue(-1);
        }
        return message;
    }
}
