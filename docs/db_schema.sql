-- =====================================================================
-- Learning Buddy · 完整关系库 Schema(交付用)
-- 包含三套方言注释:H2(默认开发库)/ MySQL 8 / PostgreSQL 15
-- 使用说明:本仓库默认 H2 文件库,首次启动自动建表(见 application.yml
-- 的 spring.jpa.hibernate.ddl-auto=update)。本脚本用于:
--   1) 交付物备查
--   2) 想用 MySQL/PG 时的初始建表参考
--   3) 评审现场一键初始化
-- =====================================================================

-- ---------------------------------------------------------------------
-- 用户与认证
-- ---------------------------------------------------------------------
CREATE TABLE app_user (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,           -- PG: BIGSERIAL
    username        VARCHAR(64)  NOT NULL UNIQUE,
    email           VARCHAR(128) UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,                             -- bcrypt
    display_name    VARCHAR(64),
    role            VARCHAR(16)  NOT NULL DEFAULT 'STUDENT',          -- STUDENT | ADMIN
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- H2: IDENTITY / MySQL: AUTO_INCREMENT / PG: BIGSERIAL
-- 索引:username/email 已在 UNIQUE 约束上

-- ---------------------------------------------------------------------
-- 学科与知识树(可切换 Python / 高数 / 英语 等)
-- ---------------------------------------------------------------------
CREATE TABLE subject (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    code            VARCHAR(32)  NOT NULL UNIQUE,                       -- 'python' / 'math' / 'english'
    name            VARCHAR(64)  NOT NULL,
    description     TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE knowledge_node (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    subject_id      BIGINT       NOT NULL,
    parent_id       BIGINT,                                            -- 自引用,组成树
    code            VARCHAR(64)  NOT NULL,                             -- 在 subject 内唯一
    title           VARCHAR(128) NOT NULL,
    description     TEXT,
    difficulty      INT          NOT NULL DEFAULT 1,                   -- 1-5
    sort_order      INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_knowledge_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
    CONSTRAINT fk_knowledge_parent  FOREIGN KEY (parent_id)  REFERENCES knowledge_node(id),
    UNIQUE (subject_id, code)
);
CREATE INDEX idx_knowledge_subject ON knowledge_node(subject_id);
CREATE INDEX idx_knowledge_parent  ON knowledge_node(parent_id);

-- ---------------------------------------------------------------------
-- 学习目标(用户选了哪门学科 / 哪些知识点)
-- ---------------------------------------------------------------------
CREATE TABLE learning_goal (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    subject_id      BIGINT       NOT NULL,
    target_node_id  BIGINT,                                            -- 目标知识点(叶子)
    self_level      VARCHAR(16)  NOT NULL DEFAULT 'BEGINNER',           -- BEGINNER/INTERMEDIATE/ADVANCED
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',            -- ACTIVE/COMPLETED/ABANDONED
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_user    FOREIGN KEY (user_id)        REFERENCES app_user(id),
    CONSTRAINT fk_goal_subject FOREIGN KEY (subject_id)     REFERENCES subject(id),
    CONSTRAINT fk_goal_node    FOREIGN KEY (target_node_id) REFERENCES knowledge_node(id)
);
CREATE INDEX idx_goal_user ON learning_goal(user_id);

-- ---------------------------------------------------------------------
-- 学习路径(由 Planner Agent 生成,可分阶段)
-- ---------------------------------------------------------------------
CREATE TABLE learning_plan (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    goal_id         BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(255) NOT NULL,
    summary         TEXT,
    plan_json       TEXT         NOT NULL,                              -- 完整结构化路径(阶段/知识点/预估时长)
    status          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_plan_goal FOREIGN KEY (goal_id) REFERENCES learning_goal(id),
    CONSTRAINT fk_plan_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_plan_user ON learning_plan(user_id);

-- ---------------------------------------------------------------------
-- 练习题(由 Quiz Agent 生成,按知识点关联)
-- ---------------------------------------------------------------------
CREATE TABLE quiz (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    plan_id         BIGINT,
    knowledge_node_id BIGINT,
    quiz_type       VARCHAR(16)  NOT NULL,                             -- PRE_TEST / PRACTICE / REVIEW
    title           VARCHAR(255) NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_quiz_user  FOREIGN KEY (user_id)           REFERENCES app_user(id),
    CONSTRAINT fk_quiz_plan  FOREIGN KEY (plan_id)           REFERENCES learning_plan(id),
    CONSTRAINT fk_quiz_node  FOREIGN KEY (knowledge_node_id) REFERENCES knowledge_node(id)
);
CREATE INDEX idx_quiz_user ON quiz(user_id);

CREATE TABLE question (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    quiz_id         BIGINT       NOT NULL,
    q_type          VARCHAR(16)  NOT NULL,                             -- CHOICE / FILL / SHORT / CODE
    difficulty      INT          NOT NULL DEFAULT 2,
    stem            TEXT         NOT NULL,                             -- 题干
    options_json    TEXT,                                               -- CHOICE 时为 JSON 数组
    answer_json     TEXT         NOT NULL,                              -- 标准答案(JSON)
    analysis        TEXT,                                               -- 解析
    score           INT          NOT NULL DEFAULT 10,
    sort_order      INT          NOT NULL DEFAULT 0,
    CONSTRAINT fk_question_quiz FOREIGN KEY (quiz_id) REFERENCES quiz(id) ON DELETE CASCADE
);
CREATE INDEX idx_question_quiz ON question(quiz_id);

-- ---------------------------------------------------------------------
-- 作答记录 + 错题本
-- ---------------------------------------------------------------------
CREATE TABLE attempt (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    quiz_id         BIGINT       NOT NULL,
    question_id     BIGINT       NOT NULL,
    user_answer     TEXT,
    is_correct      BOOLEAN      NOT NULL,
    score           INT          NOT NULL DEFAULT 0,
    elapsed_ms      INT,
    submitted_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempt_user     FOREIGN KEY (user_id)     REFERENCES app_user(id),
    CONSTRAINT fk_attempt_quiz     FOREIGN KEY (quiz_id)     REFERENCES quiz(id),
    CONSTRAINT fk_attempt_question FOREIGN KEY (question_id) REFERENCES question(id)
);
CREATE INDEX idx_attempt_user     ON attempt(user_id);
CREATE INDEX idx_attempt_question ON attempt(question_id);
CREATE INDEX idx_attempt_wrong    ON attempt(user_id, is_correct);

CREATE TABLE wrong_book (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    question_id     BIGINT       NOT NULL,
    master_level    INT          NOT NULL DEFAULT 0,                   -- 0=未掌握, 1=部分, 2=掌握
    last_attempt_id BIGINT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wrong_user     FOREIGN KEY (user_id)     REFERENCES app_user(id),
    CONSTRAINT fk_wrong_question FOREIGN KEY (question_id) REFERENCES question(id),
    UNIQUE (user_id, question_id)
);

-- ---------------------------------------------------------------------
-- 知识掌握度(供雷达图)
-- ---------------------------------------------------------------------
CREATE TABLE knowledge_progress (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    knowledge_node_id BIGINT       NOT NULL,
    mastery         INT          NOT NULL DEFAULT 0,                    -- 0-100
    attempt_count   INT          NOT NULL DEFAULT 0,
    correct_count   INT          NOT NULL DEFAULT 0,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_progress_user FOREIGN KEY (user_id)           REFERENCES app_user(id),
    CONSTRAINT fk_progress_node FOREIGN KEY (knowledge_node_id) REFERENCES knowledge_node(id),
    UNIQUE (user_id, knowledge_node_id)
);

-- ---------------------------------------------------------------------
-- 资源推荐(由 Recommender Agent 生成)
-- ---------------------------------------------------------------------
CREATE TABLE resource (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    knowledge_node_id BIGINT,
    title           VARCHAR(255) NOT NULL,
    url             VARCHAR(512),
    resource_type   VARCHAR(16)  NOT NULL,                              -- DOC / VIDEO / TUTORIAL / PROJECT
    description     TEXT,
    difficulty      INT          DEFAULT 2,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_resource_node FOREIGN KEY (knowledge_node_id) REFERENCES knowledge_node(id)
);

CREATE TABLE resource_recommendation (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    plan_id         BIGINT,
    resource_id     BIGINT       NOT NULL,
    reason          TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rec_user     FOREIGN KEY (user_id)   REFERENCES app_user(id),
    CONSTRAINT fk_rec_resource FOREIGN KEY (resource_id) REFERENCES resource(id)
);

-- ---------------------------------------------------------------------
-- RAG:用户上传的文档
-- ---------------------------------------------------------------------
CREATE TABLE rag_document (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    content_type    VARCHAR(64),
    size_bytes      BIGINT,
    storage_path    VARCHAR(512) NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',            -- PENDING / INGESTED / FAILED
    chunk_count     INT          DEFAULT 0,
    error_message   TEXT,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rag_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_rag_user ON rag_document(user_id);

-- ---------------------------------------------------------------------
-- 聊天会话与消息(Tutor)
-- ---------------------------------------------------------------------
CREATE TABLE chat_session (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(255) DEFAULT '新会话',
    agent_kind      VARCHAR(32)  DEFAULT 'TUTOR',                       -- 当前主要 Agent
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chat_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);
CREATE INDEX idx_chat_user ON chat_session(user_id);

CREATE TABLE chat_message (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    session_id      BIGINT       NOT NULL,
    role            VARCHAR(16)  NOT NULL,                              -- USER / ASSISTANT / SYSTEM
    content         TEXT         NOT NULL,
    agent_kind      VARCHAR(32),                                         -- 哪条消息由哪个 Agent 产出
    meta_json       TEXT,                                                -- token 数 / 调用耗时 / RAG 命中数 等
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
);
CREATE INDEX idx_msg_session ON chat_message(session_id);

-- ---------------------------------------------------------------------
-- Agent 调用链日志(评分核心:多 Agent 协作可视化)
-- ---------------------------------------------------------------------
CREATE TABLE agent_call_log (
    id              BIGINT       PRIMARY KEY AUTO_INCREMENT,
    user_id         BIGINT,
    session_id      BIGINT,
    request_id      VARCHAR(64)  NOT NULL,                              -- 同一次用户请求的所有调用共享
    agent_name      VARCHAR(32)  NOT NULL,                              -- Orchestrator/Planner/Quiz/...
    parent_call_id  BIGINT,                                              -- 父调用
    input_summary   TEXT,
    output_summary  TEXT,
    status          VARCHAR(16)  NOT NULL,                              -- RUNNING / SUCCESS / FAILED
    error_message   TEXT,
    started_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at     TIMESTAMP,
    duration_ms     INT,
    CONSTRAINT fk_call_user    FOREIGN KEY (user_id)    REFERENCES app_user(id),
    CONSTRAINT fk_call_session FOREIGN KEY (session_id) REFERENCES chat_session(id),
    CONSTRAINT fk_call_parent  FOREIGN KEY (parent_call_id) REFERENCES agent_call_log(id)
);
CREATE INDEX idx_call_request ON agent_call_log(request_id);
CREATE INDEX idx_call_user    ON agent_call_log(user_id);

-- =====================================================================
-- 种子数据(开发用):3 个学科 + 简化知识树
-- 评审现场可手动 INSERT,或在 Java 侧用 CommandLineRunner 注入
-- =====================================================================

INSERT INTO subject (code, name, description) VALUES
    ('python',  'Python 编程', '面向初学者的 Python 基础与进阶'),
    ('math',    '高等数学',   '微积分、线性代数基础'),
    ('english', '英语',       'CET-4/6 词汇、阅读、写作');

-- Python 知识树(部分)
INSERT INTO knowledge_node (subject_id, parent_id, code, title, difficulty, sort_order) VALUES
    (1, NULL, 'py-basics',  'Python 基础',  1, 0),
    (1, 1,    'py-syntax',  '语法与变量',   1, 0),
    (1, 1,    'py-func',    '函数',         2, 1),
    (1, 1,    'py-deco',    '装饰器',       3, 2),
    (1, 1,    'py-oop',     '面向对象',     3, 3);

-- 高数 知识树(部分)
INSERT INTO knowledge_node (subject_id, parent_id, code, title, difficulty, sort_order) VALUES
    (2, NULL, 'math-calc',  '微积分',       2, 0),
    (2, 3,    'math-limit', '极限',         2, 0),
    (2, 3,    'math-deriv', '导数',         3, 1),
    (2, 3,    'math-integ', '积分',         3, 2);

-- 英语 知识树(部分)
INSERT INTO knowledge_node (subject_id, parent_id, code, title, difficulty, sort_order) VALUES
    (3, NULL, 'en-vocab',  '词汇',         1, 0),
    (3, 5,    'en-cet4',   'CET-4 词汇',   1, 0),
    (3, 5,    'en-cet6',   'CET-6 词汇',   2, 1);
