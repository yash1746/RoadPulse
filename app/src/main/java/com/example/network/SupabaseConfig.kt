package com.example.network

/**
 * Supabase & HTTP Routing API Configuration.
 * 
 * Paste your Supabase credentials here, or specify your custom HTTP routing API URL.
 */
object SupabaseConfig {

    /**
     * Replace with your Supabase Project URL.
     * Example: "https://your-project-ref.supabase.co"
     */
    var SUPABASE_URL: String = ""

    /**
     * Replace with your Supabase Anon Public Key (from Project Settings -> API).
     * Example: "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
     */
    var SUPABASE_ANON_KEY: String = ""

    /**
     * Optional custom HTTP backend routing API URL.
     * Example: "http://10.0.2.2:5000/api/hazards" (Android emulator localhost) or "https://api.yourdomain.com/api/hazards"
     * 
     * If left blank, HazardSyncManager will send data directly to the Supabase REST endpoint:
     * "${SUPABASE_URL}/rest/v1/hazards"
     */
    var CUSTOM_API_URL: String = ""

    /**
     * Checks whether valid credentials or a custom API endpoint have been provided.
     */
    val isConfigured: Boolean
        get() = CUSTOM_API_URL.isNotBlank() || (SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank())
}
