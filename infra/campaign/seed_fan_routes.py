"""Idempotently create the two fan campaign templates on an Oracle deployment host.

Requires DB_PASSWORD, WALLET_PASSWORD and an Oracle wallet at /opt/oracle/wallet.
Run only after Flyway V11 has been applied. Invite codes are generated at runtime.
"""

import os
import secrets
import string

import oracledb


CAMPAIGNS = (
    {
        "key": "wonhee-changwon-2026",
        "name": "원희 따라 걷는 창원 · 팬 코스",
        "artist": "아일릿 원희",
        "region_code": "38110",
        "stops": (
            ("창원레일", "창원역", "PUBLIC"),
            ("짬뽕메이트", "백년옛날짬뽕", "PUBLIC"),
            ("반송미식", "반송칼국수", "PUBLIC"),
            ("반송산책", "반송여자중학교", "REFERENCE_ONLY"),
            ("롤러별", "제이씨락롤러장", "PUBLIC"),
        ),
    },
    {
        "key": "seulgi-gangneung-2026",
        "name": "슬기 따라 걷는 강릉 · 팬 코스",
        "artist": "레드벨벳 슬기",
        "region_code": "32030",
        "stops": (
            ("강릉칼국", "형제칼국수", "PUBLIC"),
            ("마젤산책", "르봉마젤 소품샵", "PUBLIC"),
            ("감자메이트", "삶은감자", "PUBLIC"),
            ("강문파도", "강문해변", "PUBLIC"),
            ("회센타팬", "옛소라회센타", "PUBLIC"),
        ),
    },
)


def insert_with_id(cursor, sql, **params):
    out_id = cursor.var(oracledb.DB_TYPE_NUMBER)
    cursor.execute(sql + " RETURNING id INTO :out_id", {**params, "out_id": out_id})
    return int(out_id.getvalue()[0])


def new_invite_code(cursor):
    alphabet = string.ascii_lowercase + string.digits
    while True:
        code = "".join(secrets.choice(alphabet) for _ in range(6))
        if not any(char.isalpha() for char in code) or not any(char.isdigit() for char in code):
            continue
        cursor.execute("SELECT COUNT(*) FROM party WHERE invite_code = :code", code=code)
        if cursor.fetchone()[0] == 0:
            return code


def seed_campaign(cursor, campaign):
    cursor.execute(
        "SELECT party_id FROM campaign_party_template WHERE campaign_key = :key",
        key=campaign["key"],
    )
    existing = cursor.fetchone()
    if existing:
        party_id = int(existing[0])
        cursor.execute("SELECT invite_code FROM party WHERE id = :party_id", party_id=party_id)
        row = cursor.fetchone()
        if not row:
            raise RuntimeError(f"Campaign {campaign['key']} points to a missing party")
        return campaign["key"], party_id, row[0], "existing"

    cursor.execute(
        "SELECT COUNT(*) FROM region WHERE region_code = :region_code",
        region_code=campaign["region_code"],
    )
    if cursor.fetchone()[0] != 1:
        raise RuntimeError(f"Unknown region code: {campaign['region_code']}")

    stops = campaign["stops"]
    if len(stops) != 5 or any(len(nickname) > 6 for nickname, _, _ in stops):
        raise RuntimeError(f"Invalid personas in {campaign['key']}")

    persona_ids = [
        insert_with_id(
            cursor,
            "INSERT INTO service_user (nickname, profile_image) VALUES (:nickname, :profile_image)",
            nickname=nickname,
            profile_image=(index % 4) + 1,
        )
        for index, (nickname, _, _) in enumerate(stops)
    ]
    invite_code = new_invite_code(cursor)
    party_id = insert_with_id(
        cursor,
        "INSERT INTO party (party_name, invite_code, owner_id) VALUES (:name, :code, :owner_id)",
        name=campaign["name"],
        code=invite_code,
        owner_id=persona_ids[0],
    )
    for persona_id in persona_ids:
        cursor.execute(
            "INSERT INTO party_member (party_id, service_user_id) VALUES (:party_id, :persona_id)",
            party_id=party_id,
            persona_id=persona_id,
        )
    cursor.execute(
        """INSERT INTO campaign_party_template
           (party_id, campaign_key, artist_name, region_code)
           VALUES (:party_id, :key, :artist, :region_code)""",
        party_id=party_id,
        key=campaign["key"],
        artist=campaign["artist"],
        region_code=campaign["region_code"],
    )
    for index, ((_, place_name, visit_policy), persona_id) in enumerate(zip(stops, persona_ids), start=1):
        cursor.execute(
            """INSERT INTO campaign_place
               (template_party_id, place_order, place_name, persona_user_id, visit_policy)
               VALUES (:party_id, :place_order, :place_name, :persona_id, :visit_policy)""",
            party_id=party_id,
            place_order=index,
            place_name=place_name,
            persona_id=persona_id,
            visit_policy=visit_policy,
        )
    return campaign["key"], party_id, invite_code, "created"


def main():
    wallet_dir = os.environ.get("WALLET_DIR", "/opt/oracle/wallet")
    with oracledb.connect(
        user=os.environ.get("DB_USER", "APP_USER"),
        password=os.environ["DB_PASSWORD"],
        dsn=os.environ.get("DB_DSN", "udumeolidb2_tp"),
        config_dir=wallet_dir,
        wallet_location=wallet_dir,
        wallet_password=os.environ["WALLET_PASSWORD"],
    ) as connection:
        with connection.cursor() as cursor:
            results = [seed_campaign(cursor, campaign) for campaign in CAMPAIGNS]
        connection.commit()
    for key, party_id, invite_code, status in results:
        print(f"{key}: party_id={party_id} invite_code={invite_code} status={status}")


if __name__ == "__main__":
    main()
