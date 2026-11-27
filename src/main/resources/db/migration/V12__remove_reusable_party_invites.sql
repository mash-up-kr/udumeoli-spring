DROP INDEX uq_party_invited_copy;
DROP INDEX uq_party_reusable_invite_key;

ALTER TABLE party DROP CONSTRAINT ck_party_invite_shape;

ALTER TABLE party DROP COLUMN source_party_id;
ALTER TABLE party DROP COLUMN invited_user_id;
ALTER TABLE party DROP COLUMN reusable_invite_key;
