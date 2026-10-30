CREATE TABLE IF NOT EXISTS freports_staff_ratings (
    staff_uuid  CHAR(36) CHARACTER SET ascii NOT NULL,
    staff_name  VARCHAR(16) NOT NULL,
    rater_uuid  CHAR(36) CHARACTER SET ascii NOT NULL,
    rater_name  VARCHAR(16) NOT NULL,
    score       TINYINT NOT NULL,
    created_at  BIGINT NOT NULL,
    updated_at  BIGINT NOT NULL,
    PRIMARY KEY (staff_uuid, rater_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
