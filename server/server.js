/**
 * RoadPulse HTTP Routing API & Supabase Gateway
 * Provides REST endpoints for:
 * 1. RoadPulse Android App (uploading detected potholes & speedbreakers)
 * 2. Municipal Authority Web Dashboard (listing, filtering, updating tickets, and live stats)
 */

const express = require('express');
const cors = require('cors');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 5000;

app.use(cors());
app.use(express.json());

// Initialize Supabase Client if credentials are provided
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_ANON_KEY = process.env.SUPABASE_ANON_KEY;
let supabase = null;

if (SUPABASE_URL && SUPABASE_ANON_KEY && SUPABASE_URL.startsWith('http')) {
    try {
        const { createClient } = require('@supabase/supabase-js');
        supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
        console.log('Connected to Supabase project:', SUPABASE_URL);
    } catch (e) {
        console.warn('Could not initialize Supabase SDK:', e.message);
    }
} else {
    console.log('Supabase credentials not set in .env. Running in standalone in-memory mode.');
}

// In-memory fallback dataset for instant out-of-the-box local testing
let inMemoryHazards = [
    {
        id: 'HAZ-101',
        latitude: 28.6139,
        longitude: 77.2090,
        road_name: 'Outer Ring Road, Lajpat Nagar Flyover',
        type: 'POTHOLE',
        severity: 'CRITICAL',
        peak_force: 24.8,
        confidence: 0.96,
        consensus_count: 4,
        status: 'UNASSIGNED',
        assigned_contractor: null,
        priority: 'P1',
        reported_at: new Date(Date.now() - 2 * 3600000).toISOString(),
        updated_at: new Date(Date.now() - 2 * 3600000).toISOString()
    },
    {
        id: 'HAZ-102',
        latitude: 28.6289,
        longitude: 77.2065,
        road_name: 'Connaught Circus, Outer Circle Inner Lane',
        type: 'POTHOLE',
        severity: 'HIGH',
        peak_force: 18.5,
        confidence: 0.91,
        consensus_count: 2,
        status: 'IN_REPAIR',
        assigned_contractor: 'Apex Infra Corp',
        priority: 'P2',
        reported_at: new Date(Date.now() - 5 * 3600000).toISOString(),
        updated_at: new Date(Date.now() - 3600000).toISOString()
    },
    {
        id: 'HAZ-103',
        latitude: 28.5921,
        longitude: 77.2280,
        road_name: 'Mathura Road, Near Ashram Chowk',
        type: 'SPEED_BREAKER',
        severity: 'MEDIUM',
        peak_force: 14.2,
        confidence: 0.88,
        consensus_count: 3,
        status: 'UNASSIGNED',
        assigned_contractor: null,
        priority: 'P3',
        reported_at: new Date(Date.now() - 8 * 3600000).toISOString(),
        updated_at: new Date(Date.now() - 8 * 3600000).toISOString()
    },
    {
        id: 'HAZ-104',
        latitude: 28.6353,
        longitude: 77.2249,
        road_name: 'Barakhamba Road, Near Metro Gate 2',
        type: 'POTHOLE',
        severity: 'CRITICAL',
        peak_force: 26.1,
        confidence: 0.98,
        consensus_count: 5,
        status: 'IN_REPAIR',
        assigned_contractor: 'Delhi PWD Division 4',
        priority: 'P1',
        reported_at: new Date(Date.now() - 12 * 3600000).toISOString(),
        updated_at: new Date(Date.now() - 1800000).toISOString()
    },
    {
        id: 'HAZ-105',
        latitude: 28.5700,
        longitude: 77.2150,
        road_name: 'Ring Road, AIIMS Crossroad Approach',
        type: 'POTHOLE',
        severity: 'HIGH',
        peak_force: 19.3,
        confidence: 0.93,
        consensus_count: 3,
        status: 'FIXED',
        assigned_contractor: 'BuildTech Roadways',
        priority: 'P2',
        reported_at: new Date(Date.now() - 24 * 3600000).toISOString(),
        updated_at: new Date(Date.now() - 7200000).toISOString()
    }
];

// Helper: Calculate distance between two lat/lng in meters (Haversine formula)
function getDistanceMeters(lat1, lon1, lat2, lon2) {
    const R = 6371e3; // metres
    const φ1 = lat1 * Math.PI / 180;
    const φ2 = lat2 * Math.PI / 180;
    const Δφ = (lat2 - lat1) * Math.PI / 180;
    const Δλ = (lon2 - lon1) * Math.PI / 180;

    const a = Math.sin(Δφ / 2) * Math.sin(Δφ / 2) +
              Math.cos(φ1) * Math.cos(φ2) *
              Math.sin(Δλ / 2) * Math.sin(Δλ / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return R * c;
}

// -----------------------------------------------------------------------------
// ROUTES
// -----------------------------------------------------------------------------

// 1. Health check
app.get('/api/health', (req, res) => {
    res.json({
        status: 'OK',
        mode: supabase ? 'SUPABASE_CONNECTED' : 'IN_MEMORY_STANDALONE',
        timestamp: new Date().toISOString()
    });
});

// 2. Summary stats for the 5 dashboard KPI cards
app.get('/api/stats', async (req, res) => {
    try {
        let hazards = inMemoryHazards;

        if (supabase) {
            const { data, error } = await supabase.from('hazards').select('*');
            if (error) throw error;
            hazards = data || [];
        }

        const stats = {
            verifiedPotholes: hazards.filter(h => h.type === 'POTHOLE').length,
            criticalHazards: hazards.filter(h => h.severity === 'CRITICAL' && h.status !== 'FIXED').length,
            unassigned: hazards.filter(h => h.status === 'UNASSIGNED').length,
            inRepair: hazards.filter(h => h.status === 'IN_REPAIR').length,
            fixed: hazards.filter(h => h.status === 'FIXED').length,
            totalHazards: hazards.length
        };

        res.json(stats);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 3. List & Filter Hazards
app.get('/api/hazards', async (req, res) => {
    try {
        const { status, severity, search, type } = req.query;

        let records = [];

        if (supabase) {
            let query = supabase.from('hazards').select('*').order('reported_at', { ascending: false });

            if (status && status !== 'All Statuses') query = query.eq('status', status.toUpperCase());
            if (severity && severity !== 'All Severities') query = query.eq('severity', severity.toUpperCase());
            if (type) query = query.eq('type', type.toUpperCase());
            if (search) query = query.or(`road_name.ilike.%${search}%,id.ilike.%${search}%`);

            const { data, error } = await query;
            if (error) throw error;
            records = data || [];
        } else {
            records = [...inMemoryHazards];

            if (status && status !== 'All Statuses') {
                records = records.filter(h => h.status.toUpperCase() === status.toUpperCase());
            }
            if (severity && severity !== 'All Severities') {
                records = records.filter(h => h.severity.toUpperCase() === severity.toUpperCase());
            }
            if (type) {
                records = records.filter(h => h.type.toUpperCase() === type.toUpperCase());
            }
            if (search) {
                const q = search.toLowerCase();
                records = records.filter(h =>
                    (h.road_name && h.road_name.toLowerCase().includes(q)) ||
                    (h.id && h.id.toLowerCase().includes(q)) ||
                    (h.assigned_contractor && h.assigned_contractor.toLowerCase().includes(q))
                );
            }

            records.sort((a, b) => new Date(b.reported_at) - new Date(a.reported_at));
        }

        res.json(records);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 4. Ingest Hazard (Called by RoadPulse Android App)
app.post('/api/hazards', async (req, res) => {
    try {
        const body = req.body;

        if (!body.latitude || !body.longitude) {
            return res.status(400).json({ error: 'latitude and longitude are required' });
        }

        const nowIso = new Date().toISOString();
        const id = body.id || `HAZ-${Date.now().toString().slice(-5)}`;
        const type = (body.type || 'POTHOLE').toUpperCase();
        const peakForce = Number(body.peak_force || body.peakMagnitude || 15.0);
        const confidence = Number(body.confidence || body.classificationConfidence || 0.9);

        const severity = body.severity || (
            peakForce >= 22.0 ? 'CRITICAL' :
            peakForce >= 16.0 ? 'HIGH' :
            peakForce >= 12.0 ? 'MEDIUM' : 'LOW'
        );

        const priority = body.priority || (
            severity === 'CRITICAL' ? 'P1' :
            severity === 'HIGH' ? 'P2' : 'P3'
        );

        const newRecord = {
            id,
            latitude: Number(body.latitude),
            longitude: Number(body.longitude),
            road_name: body.road_name || `RoadPulse GPS (${body.latitude.toFixed(4)}, ${body.longitude.toFixed(4)})`,
            type: (type.includes('SPEED') ? 'SPEED_BREAKER' : 'POTHOLE'),
            severity,
            peak_force: peakForce,
            confidence,
            consensus_count: 1,
            status: 'UNASSIGNED',
            assigned_contractor: null,
            priority,
            reported_at: body.reported_at || nowIso,
            updated_at: nowIso
        };

        if (supabase) {
            // Check for nearby hazard within 20m for multi-vehicle consensus
            const { data: nearby } = await supabase.from('hazards').select('*');
            const match = (nearby || []).find(h =>
                h.type === newRecord.type &&
                h.status !== 'FIXED' &&
                getDistanceMeters(h.latitude, h.longitude, newRecord.latitude, newRecord.longitude) <= 20
            );

            if (match) {
                // Increment consensus count
                const updatedCount = (match.consensus_count || 1) + 1;
                const { data: updated, error } = await supabase
                    .from('hazards')
                    .update({
                        consensus_count: updatedCount,
                        peak_force: Math.max(match.peak_force, newRecord.peak_force),
                        updated_at: nowIso
                    })
                    .eq('id', match.id)
                    .select()
                    .single();

                if (error) throw error;
                return res.status(200).json({ message: 'Consensus updated', hazard: updated });
            }

            const { data, error } = await supabase.from('hazards').insert([newRecord]).select().single();
            if (error) throw error;
            return res.status(201).json({ message: 'Hazard created', hazard: data });
        } else {
            // In-Memory consensus logic
            const match = inMemoryHazards.find(h =>
                h.type === newRecord.type &&
                h.status !== 'FIXED' &&
                getDistanceMeters(h.latitude, h.longitude, newRecord.latitude, newRecord.longitude) <= 20
            );

            if (match) {
                match.consensus_count = (match.consensus_count || 1) + 1;
                match.peak_force = Math.max(match.peak_force, newRecord.peak_force);
                match.updated_at = nowIso;
                return res.status(200).json({ message: 'Consensus updated', hazard: match });
            }

            inMemoryHazards.unshift(newRecord);
            return res.status(201).json({ message: 'Hazard created', hazard: newRecord });
        }
    } catch (e) {
        console.error('Error in POST /api/hazards:', e);
        res.status(500).json({ error: e.message });
    }
});

// 5. Update Hazard Status or Assigned Contractor (Dashboard Action)
app.patch('/api/hazards/:id', async (req, res) => {
    try {
        const { id } = req.params;
        const { status, assigned_contractor, priority } = req.body;
        const nowIso = new Date().toISOString();

        const updates = { updated_at: nowIso };
        if (status) updates.status = status.toUpperCase();
        if (assigned_contractor !== undefined) updates.assigned_contractor = assigned_contractor;
        if (priority) updates.priority = priority.toUpperCase();

        if (supabase) {
            const { data, error } = await supabase
                .from('hazards')
                .update(updates)
                .eq('id', id)
                .select()
                .single();

            if (error) throw error;
            return res.json({ message: 'Updated', hazard: data });
        } else {
            const item = inMemoryHazards.find(h => h.id === id);
            if (!item) return res.status(404).json({ error: 'Hazard not found' });

            Object.assign(item, updates);
            return res.json({ message: 'Updated', hazard: item });
        }
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// 6. Delete a Hazard
app.delete('/api/hazards/:id', async (req, res) => {
    try {
        const { id } = req.params;

        if (supabase) {
            const { error } = await supabase.from('hazards').delete().eq('id', id);
            if (error) throw error;
        } else {
            inMemoryHazards = inMemoryHazards.filter(h => h.id !== id);
        }

        res.json({ message: 'Deleted', id });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// Start Server
app.listen(PORT, () => {
    console.log(`\n======================================================`);
    console.log(`🚀 RoadPulse Routing API is running on port ${PORT}`);
    console.log(`📡 Endpoints:`);
    console.log(`   - GET   /api/health`);
    console.log(`   - GET   /api/stats`);
    console.log(`   - GET   /api/hazards`);
    console.log(`   - POST  /api/hazards`);
    console.log(`   - PATCH /api/hazards/:id`);
    console.log(`======================================================\n`);
});
