package com.example.websocket

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.util.Collections

class SensorWebSocketServer(
    port: Int,
    private val onSensorDataReceived: (accelX: Float, accelY: Float, accelZ: Float, gyroX: Float, gyroY: Float, gyroZ: Float) -> Unit,
    private val onStatusChanged: (status: String) -> Unit,
    private val onLocationReceived: ((lat: Double, lng: Double) -> Unit)? = null
) : WebSocketServer(InetSocketAddress(port)) {

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress
                            if (!host.isNullOrEmpty()) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SensorWebSocketServer", "Error getting local IP address", e)
            }
            return "127.0.0.1"
        }
    }

    private var packetCount = 0

    override fun onOpen(conn: WebSocket?, handshake: ClientHandshake?) {
        Log.d("SensorWebSocketServer", "New connection from ${conn?.remoteSocketAddress}")
        packetCount = 0
        onStatusChanged("Connected: ${conn?.remoteSocketAddress?.hostString ?: "Client"}")
        conn?.send("Connected to Android Sensor Viewer Simulator successfully.")
    }

    override fun onClose(conn: WebSocket?, code: Int, reason: String?, remote: Boolean) {
        Log.d("SensorWebSocketServer", "Connection closed: $reason")
        onStatusChanged("Waiting for simulation...")
    }

    override fun onMessage(conn: WebSocket?, message: String?) {
        if (message.isNullOrBlank()) return
        try {
            var ax = 0f
            var ay = 9.80665f
            var az = 0f
            var gx = 0f
            var gy = 0f
            var gz = 0f
            var parsed = false

            val trimmed = message.trim()

            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)

                // 1. Check nested objects: "accel", "accelerometer", "acc"
                val accelObj = when {
                    json.has("accel") -> json.optJSONObject("accel")
                    json.has("accelerometer") -> json.optJSONObject("accelerometer")
                    json.has("acc") -> json.optJSONObject("acc")
                    else -> null
                }

                if (accelObj != null) {
                    ax = parseNumber(accelObj, "x", "ax", "accel_x").toFloat()
                    ay = parseNumber(accelObj, "y", "ay", "accel_y").toFloat()
                    az = parseNumber(accelObj, "z", "az", "accel_z").toFloat()
                    parsed = true
                }

                // Check nested gyro objects: "gyro", "gyroscope"
                val gyroObj = when {
                    json.has("gyro") -> json.optJSONObject("gyro")
                    json.has("gyroscope") -> json.optJSONObject("gyroscope")
                    else -> null
                }

                if (gyroObj != null) {
                    gx = parseNumber(gyroObj, "x", "gx", "gyro_x").toFloat()
                    gy = parseNumber(gyroObj, "y", "gy", "gyro_y").toFloat()
                    gz = parseNumber(gyroObj, "z", "gz", "gyro_z").toFloat()
                    parsed = true
                }

                // If not parsed from nested objects, check root fields
                if (!parsed || accelObj == null) {
                    val rootAx = parseNumber(json, "x", "ax", "accel_x", "accelX")
                    val rootAy = parseNumber(json, "y", "ay", "accel_y", "accelY")
                    val rootAz = parseNumber(json, "z", "az", "accel_z", "accelZ")
                    if (!rootAx.isNaN()) ax = rootAx.toFloat()
                    if (!rootAy.isNaN()) ay = rootAy.toFloat()
                    if (!rootAz.isNaN()) az = rootAz.toFloat()
                    parsed = true
                }

                if (gyroObj == null) {
                    val rootGx = parseNumber(json, "gx", "gyro_x", "gyroX")
                    val rootGy = parseNumber(json, "gy", "gyro_y", "gyroY")
                    val rootGz = parseNumber(json, "gz", "gyro_z", "gyroZ")
                    if (!rootGx.isNaN()) gx = rootGx.toFloat()
                    if (!rootGy.isNaN()) gy = rootGy.toFloat()
                    if (!rootGz.isNaN()) gz = rootGz.toFloat()
                }

                // Check optional simulated GPS latitude & longitude
                val rootLat = parseNumber(json, "lat", "latitude")
                val rootLng = parseNumber(json, "lng", "lon", "longitude")
                if (!rootLat.isNaN() && !rootLng.isNaN()) {
                    onLocationReceived?.invoke(rootLat, rootLng)
                }

            } else if (trimmed.startsWith("[")) {
                val jsonArray = org.json.JSONArray(trimmed)
                if (jsonArray.length() >= 3) {
                    ax = jsonArray.optDouble(0, 0.0).toFloat()
                    ay = jsonArray.optDouble(1, 9.80665).toFloat()
                    az = jsonArray.optDouble(2, 0.0).toFloat()
                    if (jsonArray.length() >= 6) {
                        gx = jsonArray.optDouble(3, 0.0).toFloat()
                        gy = jsonArray.optDouble(4, 0.0).toFloat()
                        gz = jsonArray.optDouble(5, 0.0).toFloat()
                    }
                    parsed = true
                }
            } else if (trimmed.contains(",")) {
                val parts = trimmed.split(",").map { it.trim().toFloatOrNull() }
                if (parts.size >= 3 && parts[0] != null) {
                    ax = parts[0] ?: 0f
                    ay = parts[1] ?: 9.80665f
                    az = parts[2] ?: 0f
                    if (parts.size >= 6) {
                        gx = parts[3] ?: 0f
                        gy = parts[4] ?: 0f
                        gz = parts[5] ?: 0f
                    }
                    parsed = true
                }
            }

            if (parsed) {
                packetCount++
                val clientHost = conn?.remoteSocketAddress?.hostString ?: "Simulator"
                onStatusChanged("Streaming: $clientHost ($packetCount pkts)")
                onSensorDataReceived(ax, ay, az, gx, gy, gz)
            }
        } catch (e: Exception) {
            Log.e("SensorWebSocketServer", "Error parsing simulation message: ${e.message}")
        }
    }

    private fun parseNumber(json: JSONObject, vararg keys: String): Double {
        for (key in keys) {
            if (json.has(key)) {
                val valObj = json.opt(key)
                if (valObj is Number) return valObj.toDouble()
                if (valObj is String) {
                    val d = valObj.toDoubleOrNull()
                    if (d != null) return d
                }
            }
        }
        return Double.NaN
    }

    override fun onError(conn: WebSocket?, ex: Exception?) {
        Log.e("SensorWebSocketServer", "Server error occurred: ${ex?.message}")
        onStatusChanged("Server Error: ${ex?.message}")
    }

    override fun onStart() {
        Log.d("SensorWebSocketServer", "WebSocket Server successfully running.")
        onStatusChanged("Listening for simulation...")
    }
}
