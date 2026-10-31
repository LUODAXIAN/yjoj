package com.luoyujia.yjoj.sandbox;

import com.luoyujia.yjoj.sandbox.model.ExecuteCodeRequest;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeResponse;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaNativeCodeSandboxTest {

    private final JavaNativeCodeSandbox sandbox = new JavaNativeCodeSandbox();

    @Test
    void returnsAcceptedExecutionDataAndCleansTemporaryDirectory() throws Exception {
        Path root = Paths.get(System.getProperty("java.io.tmpdir"), "yjoj-code-sandbox");
        long before = countChildren(root);
        ExecuteCodeResponse response = sandbox.executeCode(request(
                "public class Main { public static void main(String[] a) { "
                        + "System.out.print(Integer.parseInt(a[0]) + Integer.parseInt(a[1])); } }"));

        assertEquals(1, response.getStatus());
        assertEquals(Arrays.asList("3"), response.getOutputList());
        assertEquals(before, countChildren(root));
    }

    @Test
    void distinguishesCompileRuntimeAndTimeout() {
        ExecuteCodeResponse compile = sandbox.executeCode(request(
                "public class Main { public static void main(String[] a) { broken } }"));
        assertEquals("Compile Error", compile.getJudgeInfo().getMessage());

        ExecuteCodeResponse runtime = sandbox.executeCode(request(
                "public class Main { public static void main(String[] a) { throw new RuntimeException(); } }"));
        assertEquals("Runtime Error", runtime.getJudgeInfo().getMessage());

        ExecuteCodeResponse timeout = sandbox.executeCode(request(
                "public class Main { public static void main(String[] a) throws Exception { Thread.sleep(10000); } }"));
        assertEquals("Time Limit Exceeded", timeout.getJudgeInfo().getMessage());
    }

    private ExecuteCodeRequest request(String code) {
        return ExecuteCodeRequest.builder()
                .language("java")
                .code(code)
                .inputList(Arrays.asList("1 2"))
                .build();
    }

    private long countChildren(Path root) throws Exception {
        if (!Files.exists(root)) {
            return 0L;
        }
        try (java.util.stream.Stream<Path> paths = Files.list(root)) {
            return paths.count();
        }
    }
}
