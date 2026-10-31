package com.luoyujia.yjoj.user;

import com.luoyujia.yjoj.common.common.DeleteRequest;
import com.luoyujia.yjoj.common.exception.BusinessException;
import com.luoyujia.yjoj.model.dto.user.UserUpdateRequest;
import com.luoyujia.yjoj.model.entity.User;
import com.luoyujia.yjoj.user.controller.UserController;
import com.luoyujia.yjoj.user.mapper.UserMapper;
import com.luoyujia.yjoj.user.service.UserService;
import com.luoyujia.yjoj.user.service.UserSessionManager;
import com.luoyujia.yjoj.user.service.impl.UserServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.MapSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.luoyujia.yjoj.common.constant.UserConstant.USER_LOGIN_STATE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserSessionRevocationTest {

    private final UserService userService = mock(UserService.class);
    private final UserSessionManager sessionManager = mock(UserSessionManager.class);
    private final UserController controller = new UserController();

    UserSessionRevocationTest() {
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "userSessionManager", sessionManager);
    }

    @Test
    void loginIndexesSessionByUserId() {
        UserMapper mapper = mock(UserMapper.class);
        User user = user("user");
        user.setUserPassword("encrypted-password");
        when(mapper.selectOne(any())).thenReturn(user);
        UserServiceImpl service = new UserServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        MockHttpServletRequest request = new MockHttpServletRequest();

        service.userLogin("test-account", "Test123456", request);

        assertEquals("42", request.getSession().getAttribute(
                FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
        assertEquals(user, request.getSession().getAttribute(USER_LOGIN_STATE));
        assertNull(user.getUserPassword());
    }

    @Test
    void logoutInvalidatesCurrentSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute(USER_LOGIN_STATE, user("user"));
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "42");

        assertTrue(new UserServiceImpl().userLogout(request));
        assertTrue(session.isInvalid());
    }

    @Test
    void banAndAdminRevocationInvalidateSessions() {
        when(userService.getById(42L)).thenReturn(user("user"), user("admin"));
        when(userService.updateById(any(User.class))).thenReturn(true);

        controller.updateUser(update("ban"), new MockHttpServletRequest());
        controller.updateUser(update("user"), new MockHttpServletRequest());

        verify(sessionManager, times(2)).invalidateUserSessions(42L);
    }

    @Test
    void profileAndUnchangedRoleDoNotInvalidateSessions() {
        when(userService.getById(42L)).thenReturn(user("user"));
        when(userService.updateById(any(User.class))).thenReturn(true);
        UserUpdateRequest profile = update(null);
        profile.setUserName("new-name");

        controller.updateUser(profile, new MockHttpServletRequest());
        controller.updateUser(update("user"), new MockHttpServletRequest());

        verifyNoInteractions(sessionManager);
    }

    @Test
    void invalidRolesAreRejectedBeforeDatabaseWrite() {
        for (String role : new String[]{"super_admin", "", " "}) {
            assertThrows(BusinessException.class,
                    () -> controller.updateUser(update(role), new MockHttpServletRequest()));
        }
        verifyNoInteractions(userService, sessionManager);
    }

    @Test
    void failedUpdateDoesNotInvalidateSessions() {
        when(userService.getById(42L)).thenReturn(user("user"));
        when(userService.updateById(any(User.class))).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> controller.updateUser(update("ban"), new MockHttpServletRequest()));
        verifyNoInteractions(sessionManager);
    }

    @Test
    void deletionInvalidatesSessionsOnlyAfterSuccess() {
        DeleteRequest request = new DeleteRequest();
        request.setId(42L);
        when(userService.removeById(42L)).thenReturn(false, true);

        controller.deleteUser(request, new MockHttpServletRequest());
        verifyNoInteractions(sessionManager);
        controller.deleteUser(request, new MockHttpServletRequest());
        verify(sessionManager).invalidateUserSessions(42L);
    }

    @Test
    void allIndexedDevicesAreRevoked() {
        FindByIndexNameSessionRepository<MapSession> repository = repository();
        when(repository.findByPrincipalName("42")).thenReturn(devices());

        new UserSessionManager(repository).invalidateUserSessions(42L);

        verify(repository).deleteById("device-a");
        verify(repository).deleteById("device-b");
    }

    @Test
    void failedRevocationIsReportedAndOtherDevicesAreStillAttempted() {
        FindByIndexNameSessionRepository<MapSession> repository = repository();
        when(repository.findByPrincipalName("42")).thenReturn(devices());
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(repository).deleteById("device-a");

        BusinessException error = assertThrows(BusinessException.class,
                () -> new UserSessionManager(repository).invalidateUserSessions(42L));

        assertEquals("用户数据已更新，但登录会话撤销失败", error.getMessage());
        verify(repository).deleteById("device-b");
    }

    private static User user(String role) {
        User user = new User();
        user.setId(42L);
        user.setUserRole(role);
        return user;
    }

    private static UserUpdateRequest update(String role) {
        UserUpdateRequest request = new UserUpdateRequest();
        request.setId(42L);
        request.setUserRole(role);
        return request;
    }

    @SuppressWarnings("unchecked")
    private static FindByIndexNameSessionRepository<MapSession> repository() {
        return mock(FindByIndexNameSessionRepository.class);
    }

    private static Map<String, MapSession> devices() {
        Map<String, MapSession> sessions = new LinkedHashMap<>();
        sessions.put("device-a", new MapSession());
        sessions.put("device-b", new MapSession());
        return sessions;
    }
}
