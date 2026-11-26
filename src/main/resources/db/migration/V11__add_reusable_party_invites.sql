ALTER TABLE party ADD (
    source_party_id NUMBER(19),
    invited_user_id NUMBER(19),
    reusable_invite_key    VARCHAR2(80 CHAR)
);

ALTER TABLE party ADD CONSTRAINT ck_party_invite_shape CHECK (
    (source_party_id IS NULL AND invited_user_id IS NULL)
    OR (source_party_id IS NOT NULL AND invited_user_id IS NOT NULL AND reusable_invite_key IS NULL)
);

CREATE UNIQUE INDEX uq_party_reusable_invite_key ON party (reusable_invite_key);
CREATE UNIQUE INDEX uq_party_invited_copy ON party (source_party_id, invited_user_id);
