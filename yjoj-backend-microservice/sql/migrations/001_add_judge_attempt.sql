-- 增量迁移：不重建表、不删除数据；字段已存在时安全跳过。
USE yjoj;
SET @column_count = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_submit' AND COLUMN_NAME = 'judgeAttempt');
SET @migration_sql = IF(@column_count = 0,
  'ALTER TABLE question_submit ADD COLUMN judgeAttempt INT NOT NULL DEFAULT 0 COMMENT ''当前判题执行轮次，初次执行为0''',
  'SELECT ''judgeAttempt already exists'' AS migration_status');
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
