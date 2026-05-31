-- R__seed_schema_presets.sql: Repeatable migration that creates the schema_presets table and
-- seeds all eight predefined schema presets (PRD §8.4).
--
-- Re-runs on every deploy when this file's checksum changes (Flyway R__ semantics).
-- Idempotent: CREATE TABLE IF NOT EXISTS + INSERT ... ON CONFLICT DO UPDATE.
--
-- Table is NOT under RLS: it is global reference data read at tenant-provisioning time,
-- before any tenant GUC is set. SELECT is granted to teamhub_app.

CREATE TABLE IF NOT EXISTS schema_presets (
    preset_key        text  NOT NULL,
    schema_name       text  NOT NULL,
    field_definitions jsonb NOT NULL,
    CONSTRAINT pk_schema_presets PRIMARY KEY (preset_key)
);

GRANT SELECT ON schema_presets TO teamhub_app;

-- ─── Appliance Repair ────────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'appliance',
    'Appliance Repair',
    '[{"key":"appliance_type","label":"Appliance Type","type":"dropdown","required":true,"order":1,"options":["Refrigerator","Washer","Dryer","Dishwasher","Oven / Range","Microwave","HVAC Unit","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"brand","label":"Brand","type":"text","required":false,"order":2,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"model_number","label":"Model Number","type":"text","required":false,"order":3,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"serial_number","label":"Serial Number","type":"text","required":false,"order":4,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"warranty_status","label":"Warranty Status","type":"dropdown","required":false,"order":5,"options":["In Warranty","Out of Warranty","Unknown"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":6,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":7,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── AC / HVAC Service ───────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'ac',
    'AC / HVAC Service',
    '[{"key":"unit_type","label":"Unit Type","type":"dropdown","required":true,"order":1,"options":["Central AC","Split System","Window Unit","Portable","Heat Pump","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"brand","label":"Brand","type":"text","required":false,"order":2,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"model_number","label":"Model Number","type":"text","required":false,"order":3,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"tonnage","label":"Tonnage","type":"dropdown","required":false,"order":4,"options":["1 ton","1.5 ton","2 ton","2.5 ton","3 ton","3.5 ton","4 ton","5 ton","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"refrigerant_type","label":"Refrigerant Type","type":"dropdown","required":false,"order":5,"options":["R-22","R-410A","R-32","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"last_service_date","label":"Last Service Date","type":"date","required":false,"order":6,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":7,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":8,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── Electrician Job ─────────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'electrician',
    'Electrician Job',
    '[{"key":"service_type","label":"Service Type","type":"dropdown","required":true,"order":1,"options":["Panel Upgrade","New Circuit","Outlet / Switch","Light Fixture","EV Charger","Generator","Inspection","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"panel_amperage","label":"Panel Amperage","type":"dropdown","required":false,"order":2,"options":["100A","150A","200A","400A","Unknown"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":3,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":4,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── Plumber Job ─────────────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'plumber',
    'Plumber Job',
    '[{"key":"service_type","label":"Service Type","type":"dropdown","required":true,"order":1,"options":["Leak Repair","Clog / Drain","Water Heater","Pipe Replacement","Faucet / Fixture","Sewer Line","Toilet","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"location_in_home","label":"Location in Home","type":"text","required":false,"order":2,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"pipe_material","label":"Pipe Material","type":"dropdown","required":false,"order":3,"options":["Copper","PVC","PEX","Cast Iron","Galvanized","Unknown"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":4,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":5,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── Handyman Job ────────────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'handyman',
    'Handyman Job',
    '[{"key":"service_type","label":"Service Type","type":"dropdown","required":true,"order":1,"options":["Assembly","Mounting / Hanging","Painting","Drywall","Flooring","Door / Window","Caulking / Sealing","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"estimated_hours","label":"Estimated Hours","type":"number","required":false,"order":2,"options":null,"depends_on":null,"options_by_parent":null,"validation":{"min":0,"max":100,"regex":null}},{"key":"problem_description","label":"Job Description","type":"text","required":true,"order":3,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":4,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── Roofing Job ─────────────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'roofing',
    'Roofing Job',
    '[{"key":"roof_type","label":"Roof Type","type":"dropdown","required":true,"order":1,"options":["Asphalt Shingle","Metal","Tile","Flat / TPO","Wood Shake","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"square_footage","label":"Square Footage","type":"number","required":false,"order":2,"options":null,"depends_on":null,"options_by_parent":null,"validation":{"min":0,"max":100000,"regex":null}},{"key":"damage_type","label":"Damage / Service Type","type":"dropdown","required":true,"order":3,"options":["Leak","Wind Damage","Hail Damage","Age / Wear","New Installation","Inspection","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":4,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":5,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── Solar Panel Job ─────────────────────────────────────────────────────────
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'solar',
    'Solar Panel Job',
    '[{"key":"panel_brand","label":"Panel Brand","type":"text","required":false,"order":1,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"number_of_panels","label":"Number of Panels","type":"number","required":false,"order":2,"options":null,"depends_on":null,"options_by_parent":null,"validation":{"min":1,"max":1000,"regex":null}},{"key":"inverter_type","label":"Inverter Type","type":"dropdown","required":false,"order":3,"options":["String Inverter","Microinverter","Power Optimizer","Battery Storage","Unknown"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"system_size_kw","label":"System Size (kW)","type":"number","required":false,"order":4,"options":null,"depends_on":null,"options_by_parent":null,"validation":{"min":0,"max":10000,"regex":null}},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":5,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"photos","label":"Photos","type":"image","required":false,"order":6,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;

-- ─── Locksmith Job (incl. automotive sub-template, PRD §8.5) ─────────────────
-- TO-DO (PRD §26.1): confirm final top-5 makes list before release.
-- Working list: Ford, Chevrolet, Toyota, Honda, Tesla.
INSERT INTO schema_presets (preset_key, schema_name, field_definitions)
VALUES (
    'locksmith',
    'Locksmith Job',
    '[{"key":"service_type","label":"Service Type","type":"dropdown","required":true,"order":1,"options":["Residential Lockout","Commercial Lockout","Lock Rekey","Lock Replacement","Key Duplication","Safe Opening","Door Hardware","Automotive Lockout","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"lock_type","label":"Lock Type","type":"dropdown","required":false,"order":2,"options":["Deadbolt","Knob","Lever","Padlock","Mortise","Smart Lock","Other"],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"problem_description","label":"Problem Description","type":"text","required":true,"order":3,"options":null,"depends_on":null,"options_by_parent":null,"validation":null},{"key":"make","label":"Vehicle Make","type":"dropdown","required":false,"order":4,"options":["Ford","Chevrolet","Toyota","Honda","Tesla","Other..."],"depends_on":null,"options_by_parent":null,"validation":null},{"key":"model","label":"Vehicle Model","type":"text","required":false,"order":5,"options":null,"depends_on":"make","options_by_parent":null,"validation":null},{"key":"year","label":"Vehicle Year","type":"text","required":false,"order":6,"options":null,"depends_on":"make","options_by_parent":null,"validation":{"min":null,"max":null,"regex":"^[0-9]{4}$"}},{"key":"photos","label":"Photos","type":"image","required":false,"order":7,"options":null,"depends_on":null,"options_by_parent":null,"validation":null}]'::jsonb
)
ON CONFLICT (preset_key) DO UPDATE
    SET schema_name       = EXCLUDED.schema_name,
        field_definitions = EXCLUDED.field_definitions;
