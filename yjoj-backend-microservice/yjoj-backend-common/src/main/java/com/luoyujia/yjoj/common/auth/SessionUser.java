package com.luoyujia.yjoj.common.auth;

import java.io.Serializable;

/**
 * Session 中登录用户的最小契约，避免公共模块反向依赖业务 model 模块。
 */
public interface SessionUser extends Serializable {

    Long getId();

    String getUserRole();
}
