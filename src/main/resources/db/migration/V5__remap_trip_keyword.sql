-- 여행 키워드를 기획 확정본 5종(맛집·디저트·힐링·액티비티·사진)으로 좁힌다.
-- FOOD/HEALING/ACTIVITY는 그대로 남고 DESSERT/PHOTO가 새로 들어오며, NATURE/CITY/CULTURE는 사라진다.
-- 사라지는 세 값으로 저장된 기존 여행은 지우지 않고 의미가 가장 가까운 키워드로 흡수한다.
-- keyword는 VARCHAR2(20)이고 CHECK 제약이 없어서, 컬럼 변경 없이 값만 갈아끼우면 된다.

-- 자연 → 힐링: 둘 다 '쉬러 간 여행'이라 스티커 성격이 가장 가깝다.
UPDATE trip SET keyword = 'HEALING' WHERE keyword = 'NATURE';

-- 도시·문화 → 사진: 남은 키워드 중 장소성이 옅고 무엇이든 담을 수 있는 값이 사진뿐이다.
UPDATE trip SET keyword = 'PHOTO' WHERE keyword IN ('CITY', 'CULTURE');
