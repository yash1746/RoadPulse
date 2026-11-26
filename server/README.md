# RoadPulse HTTP Routing API & Supabase Server

This server connects:
1. **The RoadPulse Android App**: Ingests real-time detected potholes and speed breakers via `POST /api/hazards`.
2. **The Municipal Authority Web Dashboard**: Serves filtered hazard listings, GIS heatmap data, and ticket status updates (`PATCH /api/hazards/:id`).
3. **Supabase PostgreSQL**: Persistent cloud database with Row Level Security (RLS).

---

### Quick Start (Standalone / In-Memory Mode)
If you don't have Supabase credentials set up yet, the server will automatically run with pre-populated in-memory demo data!

```bash
cd server
npm install
npm start
```

---

### Connecting to Supabase
1. Run `supabase_schema.sql` in your Supabase SQL Editor.
2. Copy your Project URL & Anon Public Key from **Project Settings -> API**.
3. Create a `.env` file in this folder:
```env
PORT=5000
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key-here
```
4. Run `npm start`.

---

### REST API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/api/health` | Healthcheck and Supabase connection status |
| `GET` | `/api/stats` | Returns the 5 KPI summary stats for the dashboard |
| `GET` | `/api/hazards` | Returns hazards (filters: `?status=...&severity=...&search=...`) |
| `POST` | `/api/hazards` | Ingests a new hazard (handles 20m multi-vehicle consensus) |
| `PATCH` | `/api/hazards/:id` | Updates status (`UNASSIGNED`, `IN_REPAIR`, `FIXED`) or contractor |
| `DELETE` | `/api/hazards/:id` | Deletes a hazard record |
