-- V1's merged municipality boundaries have no English name in the source GeoJSON.
-- Fill those eleven names without changing existing region codes or Korean names.
UPDATE region SET name_eng = 'Suwon-si' WHERE region_code = '31010' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Seongnam-si' WHERE region_code = '31020' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Anyang-si' WHERE region_code = '31040' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Ansan-si' WHERE region_code = '31090' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Goyang-si' WHERE region_code = '31100' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Yongin-si' WHERE region_code = '31190' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Cheongju-si' WHERE region_code = '33040' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Cheonan-si' WHERE region_code = '34010' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Jeonju-si' WHERE region_code = '35010' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Pohang-si' WHERE region_code = '37010' AND name_eng IS NULL;
UPDATE region SET name_eng = 'Changwon-si' WHERE region_code = '38110' AND name_eng IS NULL;
