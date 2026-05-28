ALTER TABLE annotations ADD COLUMN external_id VARCHAR(255) NULL;
ALTER TABLE annotations ADD COLUMN source VARCHAR(50) NULL;

CREATE UNIQUE INDEX uq_annotation_external_id_user ON annotations (external_id, user_id);
