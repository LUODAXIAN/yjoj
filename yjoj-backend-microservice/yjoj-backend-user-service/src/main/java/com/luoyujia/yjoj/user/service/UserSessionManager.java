package com.luoyujia.yjoj.user.service;

import com.luoyujia.yjoj.common.common.ErrorCode;
import com.luoyujia.yjoj.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/**
 * 根据登录时建立的用户索引，撤销用户在所有设备上的 Redis 会话。
 */
@Component
@Slf4j
public class UserSessionManager {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public UserSessionManager(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    /**
     * 撤销全部会话。调用方应在数据库更新提交成功后调用。
     * Redis 操作失败不会回滚已经提交的数据库更新，必须向调用方明确反馈。
     *
     * @param userId 目标用户 ID
     */
    public void invalidateUserSessions(Long userId) {
        try {
            RuntimeException deletionError = null;
            for (String sessionId : sessionRepository.findByPrincipalName(userId.toString()).keySet()) {
                try {
                    sessionRepository.deleteById(sessionId);
                } catch (RuntimeException e) {
                    // 一个设备的会话删除失败时，仍尝试撤销其他设备的会话。
                    log.error("用户会话撤销失败，userId={}，sessionId={}", userId, sessionId, e);
                    deletionError = e;
                }
            }
            if (deletionError != null) {
                throw deletionError;
            }
        } catch (RuntimeException e) {
            log.error("用户数据已更新，但登录会话未全部撤销，userId={}", userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "用户数据已更新，但登录会话撤销失败");
        }
    }
}
