package com.luoyujia.yjoj.aop;

import com.luoyujia.yjoj.annotation.AuthCheck;
import com.luoyujia.yjoj.common.ErrorCode;
import com.luoyujia.yjoj.exception.BusinessException;
import com.luoyujia.yjoj.model.entity.User;
import com.luoyujia.yjoj.model.enums.UserRoleEnum;
import com.luoyujia.yjoj.service.UserService;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 在进入标记了 {@link AuthCheck} 的接口前校验登录状态和用户角色。
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);

        // 角色值异常或账号已封禁时，不允许访问任何受保护接口。
        UserRoleEnum currentRole = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (currentRole == null || UserRoleEnum.BAN.equals(currentRole)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        String requiredRoleValue = authCheck.mustRole();
        if (StringUtils.isNotBlank(requiredRoleValue)) {
            UserRoleEnum requiredRole = UserRoleEnum.getEnumByValue(requiredRoleValue);
            if (requiredRole == null || !requiredRole.equals(currentRole)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
        return joinPoint.proceed();
    }
}


