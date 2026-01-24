package com.lzofseven.mcserver.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

// Simple RCON Client implementation based on Source RCON Protocol
// https://developer.valvesoftware.com/wiki/Source_RCON_Protocol
@Singleton
class RconClient @Inject constructor() {
    private var host: String = "localhost"
    private var port: Int = 25575
    private val requestId = AtomicInteger(1)

    // Packet Types
    companion object {
        const val SERVERDATA_AUTH = 3
        const val SERVERDATA_EXECCOMMAND = 2
        const val SERVERDATA_AUTH_RESPONSE = 2
        const val SERVERDATA_RESPONSE_VALUE = 0
    }

    suspend fun sendCommand(password: String, command: String): String = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        
        // Retry logic: 3 attempts with increasing delay
        repeat(3) { attempt ->
            var socket: Socket? = null
            try {
                if (attempt > 0) {
                    android.util.Log.d("RconClient", "Retrying RCON connection (Attempt ${attempt + 1})...")
                    kotlinx.coroutines.delay(500L * attempt)
                }
                
                android.util.Log.d("RconClient", "Connecting to $host:$port...")
                socket = Socket(host, port)
                socket.soTimeout = 5000 // 5 seconds timeout
                
                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                // 1. Authenticate
                val authId = requestId.getAndIncrement()
                android.util.Log.d("RconClient", "Sending Auth Packet ID $authId")
                writePacket(output, authId, SERVERDATA_AUTH, password)
                
                // Read Auth Response
                while (true) {
                    val packet = readPacket(input)
                    
                    if (packet.type == SERVERDATA_AUTH_RESPONSE) {
                        if (packet.id == -1) {
                            throw Exception("RCON Authentication Failed. Check password.")
                        }
                        if (packet.id == authId) {
                            break
                        }
                    }
                }
                
                // Give server a moment to settle state
                // REMOVED DELAY: Some servers might timeout idle RCON quickly?
                // kotlinx.coroutines.delay(200)

                // 2. Execute Command
                val cmdId = requestId.getAndIncrement()
                android.util.Log.d("RconClient", "======== log de depuração ======== : RCON SEND [ID:$cmdId]: $command")
                writePacket(output, cmdId, SERVERDATA_EXECCOMMAND, command)
                
                // Read Command Response
                try {
                    val response = readPacket(input)
                    android.util.Log.d("RconClient", "======== log de depuração ======== : RCON RECV [ID:${response.id}]: ${response.body}")
                    return@withContext response.body
                } catch (e: java.io.EOFException) {
                    // Server closed connection immediately.
                    android.util.Log.w("RconClient", "Server closed connection during read (EOF).")
                    return@withContext "Erro: O servidor desconectou antes de responder. O comando pode ter falhado."
                }
            } catch (e: Exception) {
                lastError = e
                android.util.Log.e("RconClient", "RCON Attempt ${attempt + 1} failed: ${e.message}")
            } finally {
                try { socket?.close() } catch (e: Exception) {}
            }
        }
        
        throw Exception("RCON Error after 3 attempts: ${lastError?.message}")
    }

    private fun writePacket(out: DataOutputStream, id: Int, type: Int, body: String) {
        val bodyBytes = body.toByteArray(Charsets.UTF_8) // Changed to UTF-8
        val size = 4 + 4 + bodyBytes.size + 2 // ID + Type + Body + 2 null bytes
        
        // Write Size (Little Endian)
        out.write(intToLittleEndian(size))
        // Write ID
        out.write(intToLittleEndian(id))
        // Write Type
        out.write(intToLittleEndian(type))
        // Write Body
        out.write(bodyBytes)
        // Write 2 Null Bytes (Terminator)
        out.write(0)
        out.write(0)
        out.flush()
    }
    
    private fun readPacket(input: DataInputStream): RconPacket {
        // Read Size
        val sizeBytes = ByteArray(4)
        input.readFully(sizeBytes)
        val size = byteArrayToIntLittleEndian(sizeBytes)
        
        // Safety cap for huge packets
        if (size > 16384 || size < 10) { 
             // Just a sanity check. 
             // Note: Packet size includes ID(4) + Type(4) + Body + Nulls(2). Min size 10 (empty body).
        }

        // Read remaining payload
        val payload = ByteArray(size)
        input.readFully(payload)
        
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val id = buffer.int
        val type = buffer.int
        
        // Body is the rest, minus the last 2 null bytes
        val bodyBytes = ByteArray(size - 8 - 2)
        buffer.get(bodyBytes)
        
        val body = String(bodyBytes, Charsets.UTF_8) // Decode as UTF-8 for game output
        
        return RconPacket(size, id, type, body)
    }

    private fun intToLittleEndian(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun byteArrayToIntLittleEndian(bytes: ByteArray): Int {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
    }

    data class RconPacket(val size: Int, val id: Int, val type: Int, val body: String)
}
