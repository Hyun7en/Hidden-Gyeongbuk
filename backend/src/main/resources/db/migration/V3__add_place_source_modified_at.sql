ALTER TABLE place
    ADD COLUMN source_modified_at DATETIME NULL AFTER updated_at;
