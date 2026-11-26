package com.example.network

/**
 * Supabase & HTTP Routing API Configuration.
 */
object SupabaseConfig {

    /**
     * Your Supabase Project URL.
     */
    var SUPABASE_URL: String = "https://xqhqyfmuytyhiqtggwsc.supabase.co"

    /**
     * Your Supabase Anon / Publishable API Key.
     */
    var SUPABASE_ANON_KEY: String = "sb_publishable_mpZgh9p5ibiY0lyOrI21ng_YK7shZJg"

    /**
     * Optional custom HTTP backend routing API URL.
     * Example: "http://10.0.2.2:5000/api/hazards" (Android emulator localhost) or "https://api.yourdomain.com/api/hazards"
     * 
     * If left blank, HazardSyncManager will send data directly to your Supabase REST endpoint:
     * "${SUPABASE_URL}/rest/v1/hazards"
     */
    var CUSTOM_API_URL: String = ""

    /**
     * Checks whether valid credentials or a custom API endpoint have been provided.
     */
    val isConfigured: Boolean
        get() = CUSTOM_API_URL.isNotBlank() || (SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank())
}
