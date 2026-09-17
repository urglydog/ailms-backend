DELIMITER $$
CREATE PROCEDURE AddProfileFieldsIfNotExists()
BEGIN
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'headline'
    ) THEN
        ALTER TABLE users ADD COLUMN headline VARCHAR(255) NULL;
    END IF;
    
    IF NOT EXISTS (
        SELECT * FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'bio'
    ) THEN
        ALTER TABLE users ADD COLUMN bio TEXT NULL;
    END IF;
END $$
DELIMITER ;

CALL AddProfileFieldsIfNotExists();
DROP PROCEDURE AddProfileFieldsIfNotExists;
