package com.luoyujia.yjoj.common.aop;

import com.luoyujia.yjoj.common.annotation.AuthCheck;
import com.luoyujia.yjoj.common.auth.SessionUser;
import com.luoyujia.yjoj.common.common.ErrorCode;
import com.luoyujia.yjoj.common.constant.UserConstant;
import com.luoyujia.yjoj.common.exception.BusinessException;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static com.luoyujia.yjoj.common.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户服务和题目服务共用的 Session 权限切面。
 */
@Aspect
@Component
public class AuthInterceptor {

    @Around("@annotation(authCheck)")
    public Object check(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attributes.getRequest();
        Object value = request.getSession().getAttribute(USER_LOGIN_STATE);
        // Session 只接受实现 SessionUser 的对象，公共模块无需依赖具体 User 实体。
        if (!(value instanceof SessionUser) || ((SessionUser) value).getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        SessionUser user = (SessionUser) value;
        // 封禁用户不能访问任何带 AuthCheck 的接口。
        if (UserConstant.BAN_ROLE.equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        String requiredRole = authCheck.mustRole();
        if (StringUtils.isNotBlank(requiredRole) && !requiredRole.equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
