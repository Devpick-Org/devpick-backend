-- Devpick PostgreSQL DDL for ERDCloud Import (parser-friendly)
-- Notes:
-- 1) UUID -> VARCHAR(36)
-- 2) JSONB -> TEXT
-- 3) PRIMARY KEY declared as table-level constraints
-- 4) DynamoDB tables excluded

CREATE TABLE users (
    id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    nickname VARCHAR(50) NOT NULL,
    profile_image VARCHAR(500),
    job VARCHAR(50) NOT NULL,
    level VARCHAR(20) NOT NULL,
    total_points INTEGER NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT uq_users_nickname UNIQUE (nickname)
);

CREATE TABLE tags (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_tags PRIMARY KEY (id),
    CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE content_sources (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    url VARCHAR(500) NOT NULL,
    collect_method VARCHAR(20) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_content_sources PRIMARY KEY (id),
    CONSTRAINT uq_content_sources_name UNIQUE (name)
);

CREATE TABLE badges (
    id VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    sort_order INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_badges PRIMARY KEY (id)
);

CREATE TABLE email_verifications (
    id VARCHAR(36) NOT NULL,
    email VARCHAR(255) NOT NULL,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_email_verifications PRIMARY KEY (id)
);

CREATE TABLE user_tags (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    tag_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_user_tags PRIMARY KEY (id),
    CONSTRAINT fk_user_tags_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
);

CREATE TABLE user_consents (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    consent_type VARCHAR(30) NOT NULL,
    agreed_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_user_consents PRIMARY KEY (id),
    CONSTRAINT uq_user_consents_user_type UNIQUE (user_id, consent_type),
    CONSTRAINT fk_user_consents_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE refresh_tokens (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    token VARCHAR(500) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE social_accounts (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_social_accounts PRIMARY KEY (id),
    CONSTRAINT uq_social_accounts_provider UNIQUE (provider, provider_id),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE contents (
    id VARCHAR(36) NOT NULL,
    source_id VARCHAR(36) NOT NULL,
    title VARCHAR(500) NOT NULL,
    author VARCHAR(100),
    canonical_url VARCHAR(1000) NOT NULL,
    preview TEXT,
    thumbnail_url TEXT,
    is_original_visible BOOLEAN NOT NULL DEFAULT FALSE,
    license_type VARCHAR(50),
    original_content TEXT,
    published_at TIMESTAMP,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    takedown_requested_at TIMESTAMP,
    score INTEGER,
    view_count INTEGER,
    is_answered BOOLEAN,
    question_content TEXT,
    accepted_answer TEXT,
    top_answers TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_contents PRIMARY KEY (id),
    CONSTRAINT uq_contents_canonical_url UNIQUE (canonical_url),
    CONSTRAINT fk_contents_source FOREIGN KEY (source_id) REFERENCES content_sources(id)
);

CREATE TABLE content_tags (
    id VARCHAR(36) NOT NULL,
    content_id VARCHAR(36) NOT NULL,
    tag_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_content_tags PRIMARY KEY (id),
    CONSTRAINT fk_content_tags_content FOREIGN KEY (content_id) REFERENCES contents(id),
    CONSTRAINT fk_content_tags_tag FOREIGN KEY (tag_id) REFERENCES tags(id)
);

CREATE TABLE scraps (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_scraps PRIMARY KEY (id),
    CONSTRAINT uq_scraps_user_content UNIQUE (user_id, content_id),
    CONSTRAINT fk_scraps_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_scraps_content FOREIGN KEY (content_id) REFERENCES contents(id)
);

CREATE TABLE likes (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_likes PRIMARY KEY (id),
    CONSTRAINT uq_likes_user_content UNIQUE (user_id, content_id),
    CONSTRAINT fk_likes_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_likes_content FOREIGN KEY (content_id) REFERENCES contents(id)
);

CREATE TABLE quiz_attempts (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content_id VARCHAR(36) NOT NULL,
    level VARCHAR(20) NOT NULL,
    score INTEGER NOT NULL,
    total_questions INTEGER NOT NULL,
    passed BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_quiz_attempts PRIMARY KEY (id),
    CONSTRAINT fk_quiz_attempts_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_quiz_attempts_content FOREIGN KEY (content_id) REFERENCES contents(id)
);

CREATE TABLE posts (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(500) NOT NULL,
    content TEXT NOT NULL,
    level VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_posts PRIMARY KEY (id),
    CONSTRAINT fk_posts_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE answers (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    is_adopted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_answers PRIMARY KEY (id),
    CONSTRAINT fk_answers_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_answers_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE comments (
    id VARCHAR(36) NOT NULL,
    answer_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_comments PRIMARY KEY (id),
    CONSTRAINT fk_comments_answer FOREIGN KEY (answer_id) REFERENCES answers(id),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE post_likes (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_post_likes PRIMARY KEY (id),
    CONSTRAINT uq_post_likes_post_user UNIQUE (post_id, user_id),
    CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE answer_likes (
    id VARCHAR(36) NOT NULL,
    answer_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_answer_likes PRIMARY KEY (id),
    CONSTRAINT uq_answer_likes_answer_user UNIQUE (answer_id, user_id),
    CONSTRAINT fk_answer_likes_answer FOREIGN KEY (answer_id) REFERENCES answers(id),
    CONSTRAINT fk_answer_likes_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE ai_questions (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    original_title VARCHAR(500) NOT NULL,
    refined_title VARCHAR(500) NOT NULL,
    refined_content TEXT,
    suggestions TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_ai_questions PRIMARY KEY (id),
    CONSTRAINT uq_ai_questions_post UNIQUE (post_id),
    CONSTRAINT fk_ai_questions_post FOREIGN KEY (post_id) REFERENCES posts(id)
);

CREATE TABLE ai_answers (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    is_adopted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_ai_answers PRIMARY KEY (id),
    CONSTRAINT uq_ai_answers_post UNIQUE (post_id),
    CONSTRAINT fk_ai_answers_post FOREIGN KEY (post_id) REFERENCES posts(id)
);

CREATE TABLE similar_questions (
    id VARCHAR(36) NOT NULL,
    post_id VARCHAR(36) NOT NULL,
    similar_id VARCHAR(36) NOT NULL,
    score REAL NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_similar_questions PRIMARY KEY (id),
    CONSTRAINT fk_similar_questions_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_similar_questions_similar FOREIGN KEY (similar_id) REFERENCES posts(id)
);

CREATE TABLE weekly_reports (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    week_start DATE NOT NULL,
    week_end DATE NOT NULL,
    share_token VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'generated',
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_weekly_reports PRIMARY KEY (id),
    CONSTRAINT uq_weekly_reports_user_week UNIQUE (user_id, week_start),
    CONSTRAINT uq_weekly_reports_share_token UNIQUE (share_token),
    CONSTRAINT fk_weekly_reports_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE report_activities (
    id VARCHAR(36) NOT NULL,
    report_id VARCHAR(36) NOT NULL,
    contents_read INTEGER NOT NULL DEFAULT 0,
    questions_created INTEGER NOT NULL DEFAULT 0,
    scraps_count INTEGER NOT NULL DEFAULT 0,
    top_tags TEXT,
    prev_week_comparison TEXT,
    daily_activities TEXT,
    tag_activities TEXT,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_report_activities PRIMARY KEY (id),
    CONSTRAINT fk_report_activities_report FOREIGN KEY (report_id) REFERENCES weekly_reports(id)
);

CREATE TABLE history (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    content_id VARCHAR(36),
    post_id VARCHAR(36),
    answer_id VARCHAR(36),
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_history PRIMARY KEY (id),
    CONSTRAINT fk_history_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_history_content FOREIGN KEY (content_id) REFERENCES contents(id),
    CONSTRAINT fk_history_post FOREIGN KEY (post_id) REFERENCES posts(id),
    CONSTRAINT fk_history_answer FOREIGN KEY (answer_id) REFERENCES answers(id)
);

CREATE TABLE user_badges (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    badge_id VARCHAR(50) NOT NULL,
    acquired_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_user_badges PRIMARY KEY (id),
    CONSTRAINT uq_user_badges_user_badge UNIQUE (user_id, badge_id),
    CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_badges_badge FOREIGN KEY (badge_id) REFERENCES badges(id)
);

CREATE TABLE point_logs (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    points INTEGER NOT NULL,
    reference_id VARCHAR(36),
    earned_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_point_logs PRIMARY KEY (id),
    CONSTRAINT fk_point_logs_user FOREIGN KEY (user_id) REFERENCES users(id)
);
