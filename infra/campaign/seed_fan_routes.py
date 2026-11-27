"""Create the two fan route parties and five fictional members per party.

Run after the copyParty API is deployed. Requires DB_PASSWORD, WALLET_PASSWORD, and
an Oracle wallet at WALLET_DIR (default /opt/oracle/wallet). The transaction
commits only when both parties are valid, and reruns reuse matching parties.
"""

import os
import secrets
import string

import oracledb


CAMPAIGNS = (
    {
        "name": "원희 따라 걷는 창원 · 팬 코스",
        "region_code": "38110",
        "stops": (
            ("창원레일", "창원역"),
            ("짬뽕메이트", "백년옛날짬뽕"),
            ("반송미식", "반송칼국수"),
            ("반송산책", "반송여자중학교"),
            ("롤러별", "제이씨락롤러장"),
        ),
    },
    {
        "name": "슬기 따라 걷는 강릉 · 팬 코스",
        "region_code": "32030",
        "stops": (
            ("강릉칼국", "형제칼국수"),
            ("마젤산책", "르봉마젤 소품샵"),
            ("감자메이트", "삶은감자"),
            ("강문파도", "강문해변"),
            ("회센타팬", "옛소라회센타"),
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
        cursor.execute("SELECT COUNT(*) FROM party WHERE invite_code = :code", code=code)
        if cursor.fetchone()[0] == 0:
            return code


def seed_campaign(cursor, campaign):
    name = campaign["name"]
    stops = campaign["stops"]
    if len(stops) != 5 or any(len(nickname) > 6 for nickname, _ in stops):
        raise ValueError(f"Invalid fictional members for {name}")

    cursor.execute("SELECT COUNT(*) FROM region WHERE region_code = :code", code=campaign["region_code"])
    if cursor.fetchone()[0] != 1:
        raise ValueError(f"Unknown region: {campaign['region_code']}")

    cursor.execute(
        "SELECT id, invite_code FROM party WHERE party_name = :name ORDER BY id",
        name=name,
    )
    existing = cursor.fetchall()
    if existing:
        # The original is created before its copies; never mistake a newer copy for it.
        party_id, invite_code = existing[0]
        cursor.execute(
            """SELECT u.nickname FROM party_member m
               JOIN service_user u ON u.id = m.service_user_id
               WHERE m.party_id = :party_id""",
            party_id=party_id,
        )
        actual_names = sorted(row[0] for row in cursor.fetchall())
        expected_names = sorted(nickname for nickname, _ in stops)
        if actual_names != expected_names:
            raise RuntimeError(f"Party name already used with different members: {name}")
        return name, int(party_id), invite_code, "existing"

    persona_ids = [
        insert_with_id(
            cursor,
            "INSERT INTO service_user (nickname, profile_image) VALUES (:nickname, :profile_image)",
            nickname=nickname,
            profile_image=(index % 4) + 1,
        )
        for index, (nickname, _) in enumerate(stops)
    ]
    invite_code = new_invite_code(cursor)
    party_id = insert_with_id(
        cursor,
        "INSERT INTO party (party_name, invite_code, owner_id) VALUES (:name, :code, :owner_id)",
        name=name,
        code=invite_code,
        owner_id=persona_ids[0],
    )
    for persona_id in persona_ids:
        cursor.execute(
            "INSERT INTO party_member (party_id, service_user_id) VALUES (:party_id, :persona_id)",
            party_id=party_id,
            persona_id=persona_id,
        )
    return name, party_id, invite_code, "created"


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
    for name, party_id, invite_code, status in results:
        print(f"{name}: party_id={party_id} invite_code={invite_code} status={status}")


if __name__ == "__main__":
    main()
