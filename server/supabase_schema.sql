-- ==============================================================================
-- RoadPulse Municipal Road Authority - Supabase PostgreSQL Schema
-- Run this script in your Supabase Dashboard: SQL Editor -> New Query -> Run
-- ==============================================================================

-- 1. Create the hazards table
CREATE TABLE IF NOT EXISTS public.hazards (
    id TEXT PRIMARY KEY,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    road_name TEXT DEFAULT 'Reported via RoadPulse',
    type TEXT NOT NULL CHECK (type IN ('POTHOLE', 'SPEED_BREAKER')),
    severity TEXT NOT NULL CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    peak_force DOUBLE PRECISION NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    consensus_count INTEGER DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'UNASSIGNED' CHECK (status IN ('UNASSIGNED', 'ASSIGNED', 'IN_REPAIR', 'FIXED')),
    assigned_contractor TEXT DEFAULT NULL,
    priority TEXT NOT NULL DEFAULT 'P2' CHECK (priority IN ('P1', 'P2', 'P3')),
    reported_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- 2. Create indexes for high-speed queries on status, severity, and coordinates
CREATE INDEX IF NOT EXISTS idx_hazards_status ON public.hazards (status);
CREATE INDEX IF NOT EXISTS idx_hazards_severity ON public.hazards (severity);
CREATE INDEX IF NOT EXISTS idx_hazards_coords ON public.hazards (latitude, longitude);

-- 3. Enable Row Level Security (RLS)
ALTER TABLE public.hazards ENABLE ROW LEVEL SECURITY;

-- 4. Create open access policies for the mobile app and dashboard (using anon key)
DROP POLICY IF EXISTS "Allow anon read all hazards" ON public.hazards;
CREATE POLICY "Allow anon read all hazards"
    ON public.hazards
    FOR SELECT
    TO anon, authenticated
    USING (true);

DROP POLICY IF EXISTS "Allow anon insert hazards" ON public.hazards;
CREATE POLICY "Allow anon insert hazards"
    ON public.hazards
    FOR INSERT
    TO anon, authenticated
    WITH CHECK (true);

DROP POLICY IF EXISTS "Allow anon update hazards" ON public.hazards;
CREATE POLICY "Allow anon update hazards"
    ON public.hazards
    FOR UPDATE
    TO anon, authenticated
    USING (true)
    WITH CHECK (true);

-- 5. Seed initial realistic demo hazards (Delhi PWD region)
INSERT INTO public.hazards (id, latitude, longitude, road_name, type, severity, peak_force, confidence, consensus_count, status, assigned_contractor, priority, reported_at, updated_at)
VALUES
    ('HAZ-101', 28.6139, 77.2090, 'Outer Ring Road, Lajpat Nagar Flyover', 'POTHOLE', 'CRITICAL', 24.8, 0.96, 4, 'UNASSIGNED', NULL, 'P1', NOW() - INTERVAL '2 hours', NOW() - INTERVAL '2 hours'),
    ('HAZ-102', 28.6289, 77.2065, 'Connaught Circus, Outer Circle Inner Lane', 'POTHOLE', 'HIGH', 18.5, 0.91, 2, 'IN_REPAIR', 'Apex Infra Corp', 'P2', NOW() - INTERVAL '5 hours', NOW() - INTERVAL '1 hour'),
    ('HAZ-103', 28.5921, 77.2280, 'Mathura Road, Near Ashram Chowk', 'SPEED_BREAKER', 'MEDIUM', 14.2, 0.88, 3, 'UNASSIGNED', NULL, 'P3', NOW() - INTERVAL '8 hours', NOW() - INTERVAL '8 hours'),
    ('HAZ-104', 28.6353, 77.2249, 'Barakhamba Road, Near Metro Gate 2', 'POTHOLE', 'CRITICAL', 26.1, 0.98, 5, 'IN_REPAIR', 'Delhi PWD Division 4', 'P1', NOW() - INTERVAL '12 hours', NOW() - INTERVAL '30 minutes'),
    ('HAZ-105', 28.5700, 77.2150, 'Ring Road, AIIMS Crossroad Approach', 'POTHOLE', 'HIGH', 19.3, 0.93, 3, 'FIXED', 'BuildTech Roadways', 'P2', NOW() - INTERVAL '1 day', NOW() - INTERVAL '2 hours')
ON CONFLICT (id) DO NOTHING;
