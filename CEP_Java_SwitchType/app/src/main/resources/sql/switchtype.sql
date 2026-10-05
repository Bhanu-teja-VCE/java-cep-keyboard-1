-- SwitchType database (MySQL 8). Run by SchemaInstaller on first connect, or by hand:
--   mysql -u root -p < switchtype.sql
-- Privacy: typed text is never stored. typing_session holds only counts and timings.
-- selection_event (which key when) is filled only after explicit consent.

CREATE DATABASE IF NOT EXISTS switchtype;
USE switchtype;

CREATE TABLE IF NOT EXISTS user_profile (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(60) NOT NULL UNIQUE,
    scan_style      ENUM('LINEAR','ROW_COLUMN','GROUP_BINARY') NOT NULL DEFAULT 'ROW_COLUMN',
    layout          ENUM('ALPHABETICAL','QWERTY','FREQUENCY') NOT NULL DEFAULT 'FREQUENCY',
    prediction      ENUM('OFF','UNIGRAM','BIGRAM') NOT NULL DEFAULT 'BIGRAM',
    interval_ms     INT NOT NULL DEFAULT 1000,
    first_extra_pct INT NOT NULL DEFAULT 50,
    row_cycles      INT NOT NULL DEFAULT 2,
    switch_input    ENUM('SPACE','ENTER','MOUSE') NOT NULL DEFAULT 'SPACE',
    adaptive        BOOLEAN NOT NULL DEFAULT TRUE,
    high_contrast   BOOLEAN NOT NULL DEFAULT FALSE,
    key_size        INT NOT NULL DEFAULT 56,
    font_size       INT NOT NULL DEFAULT 20,
    audio           BOOLEAN NOT NULL DEFAULT FALSE,
    rest_minutes    INT NOT NULL DEFAULT 20,
    event_logging   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (interval_ms BETWEEN 200 AND 2000)
);

CREATE TABLE IF NOT EXISTS typing_session (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    profile_id       INT NOT NULL,
    mode             ENUM('FREE','PRACTICE') NOT NULL,
    started_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at      TIMESTAMP NULL,
    target_chars     INT NOT NULL DEFAULT 0,
    correct_chars    INT NOT NULL DEFAULT 0,
    presses          INT NOT NULL DEFAULT 0,
    selections       INT NOT NULL DEFAULT 0,
    corrections      INT NOT NULL DEFAULT 0,
    duration_ms      BIGINT NOT NULL DEFAULT 0,
    cpm              DECIMAL(8,2) NULL,
    error_rate       DECIMAL(6,4) NULL,
    presses_per_char DECIMAL(8,3) NULL,
    CONSTRAINT fk_session_profile FOREIGN KEY (profile_id)
        REFERENCES user_profile(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS selection_event (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id INT NOT NULL,
    t_ms       BIGINT NOT NULL,
    key_label  VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_session FOREIGN KEY (session_id)
        REFERENCES typing_session(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS experiment_run (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(80) NOT NULL,
    seeds      VARCHAR(40) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS experiment_result (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id           INT NOT NULL,
    profile          ENUM('quick','typical','slow','tremor','fatigue') NOT NULL,
    scan_style       ENUM('LINEAR','ROW_COLUMN','GROUP_BINARY') NOT NULL,
    layout           ENUM('ALPHABETICAL','QWERTY','FREQUENCY') NOT NULL,
    prediction       ENUM('OFF','UNIGRAM','BIGRAM') NOT NULL,
    interval_ms      INT NOT NULL,
    seed             INT NOT NULL,
    cpm              DECIMAL(8,2) NOT NULL,
    total_err        DECIMAL(6,4) NOT NULL,
    uncorrected_err  DECIMAL(6,4) NOT NULL,
    presses_per_char DECIMAL(8,3) NOT NULL,
    CONSTRAINT fk_result_run FOREIGN KEY (run_id)
        REFERENCES experiment_run(id) ON DELETE CASCADE
);

-- Best settings per simulated user type (from experiment 2). Filled by the Experiments tool.
CREATE TABLE IF NOT EXISTS recommended_settings (
    profile       ENUM('quick','typical','slow','tremor','fatigue') PRIMARY KEY,
    label         VARCHAR(30) NOT NULL,
    reaction_mean INT NOT NULL,
    reaction_sd   INT NOT NULL,
    scan_style    ENUM('LINEAR','ROW_COLUMN','GROUP_BINARY') NOT NULL,
    layout        ENUM('ALPHABETICAL','QWERTY','FREQUENCY') NOT NULL,
    prediction    ENUM('OFF','UNIGRAM','BIGRAM') NOT NULL,
    interval_ms   INT NOT NULL,
    cpm           DECIMAL(8,2) NOT NULL,
    total_err     DECIMAL(6,4) NOT NULL
);

DROP PROCEDURE IF EXISTS sp_start_session;
DELIMITER //
CREATE PROCEDURE sp_start_session(IN p_profile INT, IN p_mode VARCHAR(10), OUT p_id INT)
BEGIN
    INSERT INTO typing_session (profile_id, mode) VALUES (p_profile, p_mode);
    SET p_id = LAST_INSERT_ID();
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_finish_session;
DELIMITER //
CREATE PROCEDURE sp_finish_session(IN p_id INT, IN p_target INT, IN p_correct INT,
                                   IN p_presses INT, IN p_selections INT, IN p_corrections INT,
                                   IN p_duration BIGINT,
                                   OUT p_cpm DECIMAL(8,2), OUT p_err DECIMAL(6,4), OUT p_ppc DECIMAL(8,3))
BEGIN
    -- the metrics are computed here, inside MySQL
    SET p_cpm = IF(p_duration > 0, p_correct * 60000 / p_duration, 0);
    SET p_err = IF(p_selections > 0, p_corrections / p_selections, 0);
    SET p_ppc = IF(p_target > 0, p_presses / p_target, 0);
    UPDATE typing_session
       SET finished_at = CURRENT_TIMESTAMP, target_chars = p_target, correct_chars = p_correct,
           presses = p_presses, selections = p_selections, corrections = p_corrections,
           duration_ms = p_duration, cpm = p_cpm, error_rate = p_err, presses_per_char = p_ppc
     WHERE id = p_id;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_recommend_settings;
DELIMITER //
CREATE PROCEDURE sp_recommend_settings(IN p_mean INT, IN p_sd INT)
BEGIN
    -- closest simulated user type by reaction mean (scale 100 ms) and spread (scale 50 ms)
    SELECT profile, label, scan_style, layout, prediction, interval_ms, cpm, total_err
      FROM recommended_settings
     ORDER BY POW((reaction_mean - p_mean) / 100, 2) + POW((reaction_sd - p_sd) / 50, 2)
     LIMIT 1;
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_purge_events;
DELIMITER //
CREATE PROCEDURE sp_purge_events(IN p_days INT, OUT p_deleted INT)
BEGIN
    DELETE FROM selection_event WHERE created_at < NOW() - INTERVAL p_days DAY;
    SET p_deleted = ROW_COUNT();
END //
DELIMITER ;

DROP PROCEDURE IF EXISTS sp_delete_user_data;
DELIMITER //
CREATE PROCEDURE sp_delete_user_data(IN p_profile INT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;
    START TRANSACTION;
    DELETE e FROM selection_event e JOIN typing_session s ON e.session_id = s.id WHERE s.profile_id = p_profile;
    DELETE FROM typing_session WHERE profile_id = p_profile;
    DELETE FROM user_profile WHERE id = p_profile;
    COMMIT;
END //
DELIMITER ;
