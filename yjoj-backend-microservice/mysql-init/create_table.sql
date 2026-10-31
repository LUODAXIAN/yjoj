CREATE DATABASE IF NOT EXISTS yjoj
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE yjoj;

CREATE TABLE IF NOT EXISTS user
(
    id           BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    userAccount  VARCHAR(256)                           NOT NULL COMMENT '账号',
    userPassword VARCHAR(512)                           NOT NULL COMMENT '密码',
    userName     VARCHAR(256)                           NULL COMMENT '用户昵称',
    userAvatar   VARCHAR(1024)                          NULL COMMENT '用户头像',
    userProfile  VARCHAR(512)                           NULL COMMENT '用户简介',
    userRole     VARCHAR(256) DEFAULT 'user'             NOT NULL COMMENT '角色：user/admin/ban',
    createTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime   DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete     TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    UNIQUE KEY uk_userAccount (userAccount)
) COMMENT '用户' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question
(
    id          BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    title       VARCHAR(512)                           NULL COMMENT '标题',
    content     TEXT                                   NULL COMMENT '内容',
    tags        VARCHAR(1024)                          NULL COMMENT '标签列表（JSON 数组）',
    answer      TEXT                                   NULL COMMENT '题目答案',
    submitNum   INT          DEFAULT 0                 NOT NULL COMMENT '提交数',
    acceptedNum INT          DEFAULT 0                 NOT NULL COMMENT '通过数',
    judgeCase   TEXT                                   NOT NULL COMMENT '判题用例（JSON 数组）',
    judgeConfig TEXT                                   NOT NULL COMMENT '判题配置（JSON 对象）',
    userId      BIGINT                                 NOT NULL COMMENT '创建用户 id',
    createTime  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime  DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete    TINYINT      DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_userId (userId)
) COMMENT '题目' COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS question_submit
(
    id         BIGINT AUTO_INCREMENT COMMENT 'id' PRIMARY KEY,
    language   VARCHAR(128)                         NOT NULL COMMENT '编程语言',
    code       TEXT                                 NOT NULL COMMENT '用户代码',
    judgeInfo  TEXT                                 NULL COMMENT '判题信息（JSON 对象）',
    status     INT        DEFAULT 0                 NOT NULL COMMENT '判题状态：0等待、1运行、2成功、3失败',
    judgeAttempt INT DEFAULT 0 NOT NULL COMMENT '当前判题执行轮次，初次为0',
    questionId BIGINT                               NOT NULL COMMENT '题目 id',
    userId     BIGINT                               NOT NULL COMMENT '提交用户 id',
    createTime DATETIME   DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updateTime DATETIME   DEFAULT CURRENT_TIMESTAMP NOT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    isDelete   TINYINT    DEFAULT 0                 NOT NULL COMMENT '是否删除',
    INDEX idx_questionId (questionId),
    INDEX idx_userId (userId),
    INDEX idx_status (status)
) COMMENT '题目提交' COLLATE = utf8mb4_unicode_ci;
