-- V7: 여행 모델을 "지역 핀 1개 + 멤버별 기록"으로 좁힌다.
--
-- 기획 최종본에서 여행 기간 입력이 사라지고 키워드를 올리는 사람이 각자 고르게 바뀌면서,
-- 같은 지역을 여러 번 다녀오는 개념(재방문·회차)이 없어졌다. trip은 이제 "팟이 이 지역에 찍은 핀"이고
-- 키워드는 그 핀에 사진을 올린 멤버마다 따로 갖는다.
--
-- 1) 키워드를 trip → trip_record로 옮긴다
-- 2) 같은 팟·지역에 흩어져 있던 핀을 가장 오래된 핀 하나로 합친다
-- 3) trip에서 키워드·기간을 걷어내고 (party_id, region_code)를 유일키로 세운다

-- 1) 키워드 이관 ------------------------------------------------------------
ALTER TABLE trip_record ADD keyword VARCHAR2(20);

UPDATE trip_record r
   SET keyword = (SELECT t.keyword FROM trip t WHERE t.id = r.trip_id);

-- 핀이 사라진 뒤 남은 고아 기록이 있다면 여기서 정리된다 (keyword를 채울 근거가 없다).
DELETE FROM trip_record WHERE keyword IS NULL;

ALTER TABLE trip_record MODIFY keyword VARCHAR2(20) NOT NULL;

-- 2) 같은 팟·지역의 핀 병합 --------------------------------------------------
-- 대표 핀 = (party_id, region_code)마다 id가 가장 작은 것.
--
-- 합치기 전에 (팟·지역·멤버)마다 기록을 하나로 줄여야 한다. uq_trip_record(trip_id, service_user_id)가
-- 있어서, 한 사람이 같은 지역의 여러 핀에 기록을 남겼다면 그대로 옮길 수 없기 때문이다.
-- 대표 핀에 있는지만 보고 걸러서는 부족하다 — 비대표 핀 두 곳에만 기록이 있는 경우를 놓친다.
-- 남길 기록은 가장 나중 것(MAX(id))으로 잡는다. 지역 카드가 어차피 "그 멤버의 가장 최근 사진"을
-- 보여주고 있어, 유저가 자기 사진으로 알고 있던 그 장이 그대로 남는다.
DELETE FROM trip_image
 WHERE trip_record_id IN (
     SELECT r.id
       FROM trip_record r
       JOIN trip t ON t.id = r.trip_id
      WHERE r.id <> (SELECT MAX(r2.id)
                       FROM trip_record r2
                       JOIN trip t2 ON t2.id = r2.trip_id
                      WHERE t2.party_id = t.party_id
                        AND t2.region_code = t.region_code
                        AND r2.service_user_id = r.service_user_id)
 );

DELETE FROM trip_record
 WHERE id IN (
     SELECT r.id
       FROM trip_record r
       JOIN trip t ON t.id = r.trip_id
      WHERE r.id <> (SELECT MAX(r2.id)
                       FROM trip_record r2
                       JOIN trip t2 ON t2.id = r2.trip_id
                      WHERE t2.party_id = t.party_id
                        AND t2.region_code = t.region_code
                        AND r2.service_user_id = r.service_user_id)
 );

-- 살아남은 기록을 대표 핀으로 옮긴다. 멤버마다 한 건뿐이라 유일키와 부딪히지 않는다.
UPDATE trip_record r
   SET trip_id = (SELECT MIN(t2.id)
                    FROM trip t2
                    JOIN trip t ON t.id = r.trip_id
                   WHERE t2.party_id = t.party_id
                     AND t2.region_code = t.region_code)
 WHERE r.trip_id NOT IN (SELECT MIN(id) FROM trip GROUP BY party_id, region_code);

DELETE FROM trip
 WHERE id NOT IN (SELECT MIN(id) FROM trip GROUP BY party_id, region_code);

-- 3) trip 정리 ---------------------------------------------------------------
ALTER TABLE trip DROP CONSTRAINT ck_trip_period;
ALTER TABLE trip DROP COLUMN keyword;
ALTER TABLE trip DROP COLUMN start_date;
ALTER TABLE trip DROP COLUMN end_date;

ALTER TABLE trip ADD CONSTRAINT uq_trip_party_region UNIQUE (party_id, region_code);
