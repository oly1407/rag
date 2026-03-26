CREATE TABLE IF NOT EXISTS ai_chat_session (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL UNIQUE COMMENT '会话ID',
  user_no VARCHAR(64) NOT NULL COMMENT '用户标识',
  title VARCHAR(255) DEFAULT NULL COMMENT '会话标题',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1进行中 2已结束',
  round_count INT NOT NULL DEFAULT 0 COMMENT '轮次计数',
  last_message_at DATETIME DEFAULT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_no (user_no),
  KEY idx_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI会话表';

CREATE TABLE IF NOT EXISTS ai_chat_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(64) NOT NULL,
  role VARCHAR(16) NOT NULL COMMENT 'system/user/assistant',
  content MEDIUMTEXT NOT NULL,
  token_input INT DEFAULT 0,
  token_output INT DEFAULT 0,
  model_name VARCHAR(64) DEFAULT NULL,
  latency_ms INT DEFAULT NULL,
  msg_order INT NOT NULL,
  is_deleted TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_session_order (session_id, msg_order),
  KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI消息表';

CREATE TABLE IF NOT EXISTS ai_course (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  course_code VARCHAR(64) NOT NULL UNIQUE,
  course_name VARCHAR(255) NOT NULL,
  category VARCHAR(64) DEFAULT NULL,
  level_tag VARCHAR(32) DEFAULT NULL,
  keywords VARCHAR(1000) DEFAULT NULL,
  url VARCHAR(512) DEFAULT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程表';

INSERT INTO ai_course (course_code, course_name, category, level_tag, keywords, url, status)
VALUES
('JAVA_BASICS', 'Java基础到进阶（尚硅谷）', 'Java', '初级', 'java,基础,面向对象', 'https://www.bilibili.com/video/BV1Kb411W75N', 1),
('SPRING_BOOT', 'Spring Boot 3 实战（黑马程序员）', 'Spring', '中级', 'spring boot,web,接口', 'https://www.bilibili.com/video/BV1LQ4y1J77x', 1),
('MYSQL_TUNE', 'MySQL性能优化实战', 'Database', '中级', 'mysql,索引,优化', 'https://www.bilibili.com/video/BV1xW411u7ax', 1)
ON DUPLICATE KEY UPDATE course_name = VALUES(course_name);
