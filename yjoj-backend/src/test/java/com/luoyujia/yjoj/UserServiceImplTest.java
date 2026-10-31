package com.luoyujia.yjoj;

import com.luoyujia.yjoj.common.ErrorCode;
import com.luoyujia.yjoj.exception.BusinessException;
import com.luoyujia.yjoj.mapper.UserMapper;
import com.luoyujia.yjoj.model.entity.User;
import com.luoyujia.yjoj.model.enums.UserRoleEnum;
import com.luoyujia.yjoj.model.vo.LoginUserVO;
import com.luoyujia.yjoj.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static com.luoyujia.yjoj.constant.UserConstant.USER_LOGIN_STATE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceImplTest {

    private UserServiceImpl service;
    private UserMapper mapper;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl();
        mapper = mock(UserMapper.class);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void registersAndLogsInWithoutStoringPasswordInSession() {
        when(mapper.selectCount(any())).thenReturn(0L);
        when(mapper.insert(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(9L);
            return 1;
        });

        assertEquals(9L, service.userRegister("tester", "password123", "password123"));

        User stored = new User();
        stored.setId(9L);
        stored.setUserAccount("tester");
        stored.setUserPassword("hash");
        stored.setUserRole(UserRoleEnum.USER.getValue());
        when(mapper.selectOne(any())).thenReturn(stored);
        MockHttpServletRequest request = new MockHttpServletRequest();

        LoginUserVO result = service.userLogin("tester", "password123", request);

        assertEquals(9L, result.getId());
        User sessionUser = (User) request.getSession().getAttribute(USER_LOGIN_STATE);
        assertNull(sessionUser.getUserPassword());
        assertTrue(!service.isAdmin(sessionUser));
    }

    @Test
    void rejectsMissingLoginAndBannedUser() {
        BusinessException notLoggedIn = assertThrows(BusinessException.class,
                () -> service.getLoginUser(new MockHttpServletRequest()));
        assertEquals(ErrorCode.NOT_LOGIN_ERROR.getCode(), notLoggedIn.getCode());

        User banned = new User();
        banned.setId(10L);
        banned.setUserRole(UserRoleEnum.BAN.getValue());
        when(mapper.selectOne(any())).thenReturn(banned);
        BusinessException forbidden = assertThrows(BusinessException.class,
                () -> service.userLogin("banned", "password123", new MockHttpServletRequest()));
        assertEquals(ErrorCode.FORBIDDEN_ERROR.getCode(), forbidden.getCode());
    }
}
