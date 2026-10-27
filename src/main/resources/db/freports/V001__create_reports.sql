CREATE TABLE IF NOT EXISTS freports_reports (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reporter_uuid   CHAR(36) CHARACTER SET ascii NOT NULL,
    reporter_name   VARCHAR(16) NOT NULL,
    target_uuid     CHAR(36) CHARACTER SET ascii NOT NULL,
    target_name     VARCHAR(16) NOT NULL,
    category        VARCHAR(16) NOT NULL,
    reason          VARCHAR(256) NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    claimed_by_uuid CHAR(36) CHARACTER SET ascii NULL,
    claimed_by_name VARCHAR(16) NULL,
    verdict         VARCHAR(16) NULL,
    notified        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      BIGINT NOT NULL,
    updated_at      BIGINT NOT NULL,
    resolved_at     BIGINT NULL,
    INDEX (status),
    INDEX (reporter_uuid),
    INDEX (target_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS freports_notes (
    id          BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id   BIGINT NOT NULL,
    author_uuid CHAR(36) CHARACTER SET ascii NOT NULL,
    author_name VARCHAR(16) NOT NULL,
    text        VARCHAR(512) NOT NULL,
    created_at  BIGINT NOT NULL,
    INDEX (report_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS freports_bugs (
    id              BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reporter_uuid   CHAR(36) CHARACTER SET ascii NOT NULL,
    reporter_name   VARCHAR(16) NOT NULL,
    description     VARCHAR(512) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at      BIGINT NOT NULL,
    handled_by_uuid CHAR(36) CHARACTER SET ascii NULL,
    handled_by_name VARCHAR(16) NULL,
    handled_at      BIGINT NULL,
    INDEX (status),
    INDEX (reporter_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
