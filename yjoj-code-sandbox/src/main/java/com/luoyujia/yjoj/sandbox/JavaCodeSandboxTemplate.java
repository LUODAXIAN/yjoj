package com.luoyujia.yjoj.sandbox;

import cn.hutool.core.io.FileUtil;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeResponse;
import com.luoyujia.yjoj.sandbox.model.ExecuteMessage;
import com.luoyujia.yjoj.sandbox.model.JudgeInfo;
import com.luoyujia.yjoj.sandbox.utils.ProcessUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Java 沙箱模板：保存、编译、执行、汇总、清理。
 *
 * <p>当前接口约定：每个 input 字符串按空白拆分，并作为参数传给 {@code Main.main}。</p>
 */
@Slf4j
public abstract class JavaCodeSandboxTemplate implements CodeSandbox {

    private static final String JAVA_FILE_NAME = "Main.java";
    @Value("${sandbox.timeout-ms:5000}")
    protected long timeoutMillis = 5000L;
    private static final Path CODE_ROOT = Paths.get(
            System.getProperty("java.io.tmpdir"), "yjoj-code-sandbox");

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest request) {
        File codeFile = null;
        try {
            codeFile = saveCodeToFile(request.getCode());
            ExecuteMessage compile = compileFile(codeFile);
            if (!Integer.valueOf(0).equals(compile.getExitValue())) {
                return userError("Compile Error", compile.getErrorMessage(), compile);
            }
            List<ExecuteMessage> messages = runFile(
                    codeFile, request.getInputList() == null ? Collections.emptyList() : request.getInputList());
            return getOutputResponse(messages);
        } catch (Exception e) {
            log.error("代码沙箱执行失败", e);
            return systemError(e);
        } finally {
            if (codeFile != null && !deleteFile(codeFile)) {
                log.error("临时代码目录清理失败：{}", codeFile.getParent());
            }
        }
    }

    public File saveCodeToFile(String code) {
        File userDir = CODE_ROOT.resolve(UUID.randomUUID().toString()).toFile();
        FileUtil.mkdir(userDir);
        return FileUtil.writeString(code, new File(userDir, JAVA_FILE_NAME), StandardCharsets.UTF_8);
    }

    public ExecuteMessage compileFile(File codeFile) throws Exception {
        Process process = new ProcessBuilder(
                "javac", "-encoding", "UTF-8", codeFile.getAbsolutePath()).start();
        return ProcessUtils.runProcessAndGetMessage(process, "编译", timeoutMillis);
    }

    public List<ExecuteMessage> runFile(File codeFile, List<String> inputList) throws Exception {
        String classPath = codeFile.getParentFile().getAbsolutePath();
        List<ExecuteMessage> messages = new ArrayList<>();
        for (String input : inputList) {
            List<String> command = new ArrayList<>(
                    Arrays.asList("java", "-Xmx256m", "-Dfile.encoding=UTF-8", "-cp", classPath, "Main"));
            if (input != null && !input.trim().isEmpty()) {
                command.addAll(Arrays.asList(input.trim().split("\\s+")));
            }
            Process process = new ProcessBuilder(command).start();
            ExecuteMessage message = ProcessUtils.runProcessAndGetMessage(
                    process, "运行", timeoutMillis);
            messages.add(message);
            if (message.isTimeout() || !Integer.valueOf(0).equals(message.getExitValue())) {
                break;
            }
        }
        return messages;
    }

    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> messages) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        List<String> outputs = new ArrayList<>();
        long maxTime = 0L;
        long maxMemory = 0L;
        for (ExecuteMessage message : messages) {
            maxTime = Math.max(maxTime, (message.getTime() == null ? 0L : message.getTime()));
            maxMemory = Math.max(maxMemory, (message.getMemory() == null ? 0L : message.getMemory()));
            if (message.isTimeout()) {
                return userError("Time Limit Exceeded", "运行超时", message);
            }
            if (!Integer.valueOf(0).equals(message.getExitValue())) {
                return userError("Runtime Error", message.getErrorMessage(), message);
            }
            outputs.add((message.getMessage() == null ? "" : message.getMessage()));
        }
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        response.setOutputList(outputs);
        response.setStatus(1);
        response.setJudgeInfo(judgeInfo);
        return response;
    }

    public boolean deleteFile(File codeFile) {
        return codeFile.getParentFile() == null || FileUtil.del(codeFile.getParentFile());
    }

    private ExecuteCodeResponse userError(
            String judgeMessage, String detail, ExecuteMessage processMessage) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        response.setOutputList(Collections.emptyList());
        response.setMessage(detail);
        response.setStatus(3);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage(judgeMessage);
        judgeInfo.setTime((processMessage.getTime() == null ? 0L : processMessage.getTime()));
        judgeInfo.setMemory((processMessage.getMemory() == null ? 0L : processMessage.getMemory()));
        response.setJudgeInfo(judgeInfo);
        return response;
    }

    private ExecuteCodeResponse systemError(Exception exception) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        response.setOutputList(Collections.emptyList());
        response.setMessage(exception.getMessage());
        response.setStatus(2);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setMessage("System Error");
        judgeInfo.setMemory(0L);
        judgeInfo.setTime(0L);
        response.setJudgeInfo(judgeInfo);
        return response;
    }
}
