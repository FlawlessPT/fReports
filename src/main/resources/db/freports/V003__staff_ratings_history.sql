ALTER TABLE freports_staff_ratings
    DROP PRIMARY KEY,
    ADD COLUMN id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY FIRST,
    ADD INDEX (staff_uuid, rater_uuid);
