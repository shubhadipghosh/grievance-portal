-- =====================================================================
-- V9__seed_categories.sql
-- Starter taxonomy: departments, top-level categories, leaf
-- sub-categories, with English and Bengali names. A starting point for
-- a municipality to edit, not a complete taxonomy.
--
-- Idempotent: rows whose code already exists are left alone, and
-- translations are added only for categories this script inserts, so a
-- taxonomy that has already been customized is not disturbed.
--
-- Citizens file only against leaves. A parent's sla_hours is never used
-- for a grievance, but the column is NOT NULL.
-- =====================================================================

INSERT INTO department (code, name) VALUES
    ('SANITATION',      'Sanitation'),
    ('WATER_SUPPLY',    'Water Supply'),
    ('ROADS',           'Roads'),
    ('STREET_LIGHTING', 'Street Lighting'),
    ('DRAINAGE',        'Drainage'),
    ('PARKS',           'Parks'),
    ('HEALTH',          'Health'),
    ('BUILDING',        'Building')
ON CONFLICT (code) DO NOTHING;

CREATE TEMPORARY TABLE seed_category (
    code            VARCHAR(50)  NOT NULL,
    parent_code     VARCHAR(50),
    department_code VARCHAR(30)  NOT NULL,
    sla_hours       INTEGER      NOT NULL,
    name_en         VARCHAR(150) NOT NULL,
    name_bn         VARCHAR(150) NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_category VALUES
    -- Top-level categories
    ('SANITATION',      NULL, 'SANITATION',      72,  'Sanitation',      'পরিচ্ছন্নতা'),
    ('WATER_SUPPLY',    NULL, 'WATER_SUPPLY',    72,  'Water Supply',    'জল সরবরাহ'),
    ('ROADS',           NULL, 'ROADS',           168, 'Roads',           'রাস্তা'),
    ('STREET_LIGHTING', NULL, 'STREET_LIGHTING', 72,  'Street Lighting', 'রাস্তার আলো'),
    ('DRAINAGE',        NULL, 'DRAINAGE',        48,  'Drainage',        'নিকাশি'),
    ('PARKS',           NULL, 'PARKS',           168, 'Parks',           'পার্ক ও উদ্যান'),
    ('PUBLIC_HEALTH',   NULL, 'HEALTH',          72,  'Public Health',   'জনস্বাস্থ্য'),
    ('BUILDING',        NULL, 'BUILDING',        336, 'Building',        'ভবন ও নির্মাণ'),

    -- Sanitation
    ('GARBAGE_NOT_COLLECTED', 'SANITATION', 'SANITATION', 24, 'Garbage not collected',  'আবর্জনা সংগ্রহ করা হয়নি'),
    ('DUSTBIN_OVERFLOWING',   'SANITATION', 'SANITATION', 24, 'Overflowing dustbin',    'ডাস্টবিন উপচে পড়ছে'),
    ('DEAD_ANIMAL',           'SANITATION', 'SANITATION', 12, 'Dead animal removal',    'মৃত পশু অপসারণ'),
    ('STREET_NOT_SWEPT',      'SANITATION', 'SANITATION', 48, 'Street not swept',       'রাস্তা ঝাঁট দেওয়া হয়নি'),
    ('PUBLIC_TOILET_UNCLEAN', 'SANITATION', 'SANITATION', 48, 'Public toilet unclean',  'সর্বজনীন শৌচাগার অপরিষ্কার'),

    -- Water supply
    ('WATER_NO_SUPPLY',     'WATER_SUPPLY', 'WATER_SUPPLY', 24, 'No water supply',             'জল সরবরাহ নেই'),
    ('WATER_LOW_PRESSURE',  'WATER_SUPPLY', 'WATER_SUPPLY', 72, 'Low water pressure',          'জলের চাপ কম'),
    ('WATER_CONTAMINATED',  'WATER_SUPPLY', 'WATER_SUPPLY', 24, 'Dirty or contaminated water', 'নোংরা বা দূষিত জল'),
    ('WATER_PIPE_LEAK',     'WATER_SUPPLY', 'WATER_SUPPLY', 48, 'Leaking water pipe',          'জলের পাইপ ফুটো'),

    -- Roads
    ('ROAD_POTHOLE',      'ROADS', 'ROADS', 168, 'Pothole',                          'রাস্তায় গর্ত'),
    ('ROAD_DAMAGED',      'ROADS', 'ROADS', 336, 'Damaged road surface',             'ভাঙা রাস্তা'),
    ('FOOTPATH_DAMAGED',  'ROADS', 'ROADS', 168, 'Broken footpath',                  'ভাঙা ফুটপাত'),
    ('ROAD_ENCROACHMENT', 'ROADS', 'ROADS', 168, 'Encroachment on road or footpath', 'রাস্তা বা ফুটপাত দখল'),

    -- Street lighting
    ('STREETLIGHT_NOT_WORKING', 'STREET_LIGHTING', 'STREET_LIGHTING', 48, 'Streetlight not working',            'রাস্তার আলো জ্বলছে না'),
    ('STREETLIGHT_ON_BY_DAY',   'STREET_LIGHTING', 'STREET_LIGHTING', 72, 'Streetlight on during the day',      'দিনের বেলায় রাস্তার আলো জ্বলছে'),
    ('LAMP_POST_DAMAGED',       'STREET_LIGHTING', 'STREET_LIGHTING', 24, 'Damaged lamp post or exposed wires', 'ক্ষতিগ্রস্ত বাতিস্তম্ভ বা খোলা তার'),

    -- Drainage
    ('DRAIN_BLOCKED',   'DRAINAGE', 'DRAINAGE', 48, 'Blocked drain',          'নর্দমা আটকে গেছে'),
    ('WATERLOGGING',    'DRAINAGE', 'DRAINAGE', 24, 'Waterlogging',           'জল জমে আছে'),
    ('MANHOLE_OPEN',    'DRAINAGE', 'DRAINAGE', 12, 'Open or broken manhole', 'খোলা বা ভাঙা ম্যানহোল'),
    ('SEWAGE_OVERFLOW', 'DRAINAGE', 'DRAINAGE', 24, 'Sewage overflow',        'নর্দমার নোংরা জল উপচে পড়ছে'),

    -- Parks
    ('PARK_NOT_MAINTAINED',  'PARKS', 'PARKS', 168, 'Park not maintained',      'পার্কের রক্ষণাবেক্ষণ হচ্ছে না'),
    ('TREE_FALLEN',          'PARKS', 'PARKS', 24,  'Fallen or dangerous tree', 'পড়ে যাওয়া বা বিপজ্জনক গাছ'),
    ('PARK_EQUIPMENT_BROKEN','PARKS', 'PARKS', 168, 'Broken park equipment',    'পার্কের সরঞ্জাম ভাঙা'),

    -- Public health
    ('MOSQUITO_BREEDING', 'PUBLIC_HEALTH', 'HEALTH', 72, 'Mosquito breeding',      'মশার বংশবৃদ্ধি'),
    ('STRAY_DOGS',        'PUBLIC_HEALTH', 'HEALTH', 72, 'Stray dog menace',       'পথকুকুরের উপদ্রব'),
    ('FOOD_UNHYGIENIC',   'PUBLIC_HEALTH', 'HEALTH', 72, 'Unhygienic food stall',  'অস্বাস্থ্যকর খাবারের দোকান'),

    -- Building
    ('ILLEGAL_CONSTRUCTION', 'BUILDING', 'BUILDING', 336, 'Illegal construction',        'বেআইনি নির্মাণ'),
    ('DANGEROUS_BUILDING',   'BUILDING', 'BUILDING', 48,  'Dangerous building',          'বিপজ্জনক বাড়ি'),
    ('CONSTRUCTION_DEBRIS',  'BUILDING', 'BUILDING', 72,  'Construction debris on road', 'রাস্তায় নির্মাণের ধ্বংসাবশেষ');

-- Parents first, so leaves can find them by code. Only rows actually
-- inserted (RETURNING) get translations.
WITH inserted AS (
    INSERT INTO category (code, parent_id, default_department_id, sla_hours)
    SELECT s.code, NULL, d.id, s.sla_hours
    FROM seed_category s
    JOIN department d ON d.code = s.department_code
    WHERE s.parent_code IS NULL
    ON CONFLICT (code) DO NOTHING
    RETURNING id, code
)
INSERT INTO category_translation (category_id, locale, name)
SELECT i.id, t.locale, t.name
FROM inserted i
JOIN seed_category s ON s.code = i.code
CROSS JOIN LATERAL (VALUES ('en', s.name_en), ('bn', s.name_bn)) AS t (locale, name);

WITH inserted AS (
    INSERT INTO category (code, parent_id, default_department_id, sla_hours)
    SELECT s.code, p.id, d.id, s.sla_hours
    FROM seed_category s
    JOIN category p ON p.code = s.parent_code
    JOIN department d ON d.code = s.department_code
    ON CONFLICT (code) DO NOTHING
    RETURNING id, code
)
INSERT INTO category_translation (category_id, locale, name)
SELECT i.id, t.locale, t.name
FROM inserted i
JOIN seed_category s ON s.code = i.code
CROSS JOIN LATERAL (VALUES ('en', s.name_en), ('bn', s.name_bn)) AS t (locale, name);
