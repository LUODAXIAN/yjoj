package com.luoyujia.yjoj.sandbox.controller;

import com.luoyujia.yjoj.sandbox.JavaDockerCodeSandbox;
import com.luoyujia.yjoj.sandbox.JavaNativeCodeSandbox;
import com.luoyujia.yjoj.sandbox.model.ExecuteCodeRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class MainControllerTest {

    @Test
    void rejectsInvalidSecret() {
        MainController controller = new MainController(
                mock(JavaNativeCodeSandbox.class), mock(JavaDockerCodeSandbox.class));
        ReflectionTestUtils.setField(controller, "sandboxType", "native");
        ReflectionTestUtils.setField(controller, "authHeaderName", "auth");
        ReflectionTestUtils.setField(controller, "authSecret", "secret");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("auth", "wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.executeCode(ExecuteCodeRequest.builder()
                .language("java").code("class Main {}").inputList(Arrays.asList()).build(),
                request, response);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }
}
