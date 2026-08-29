package com.inwords.expenses.benchmarks.network

import java.io.BufferedOutputStream
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class LoopbackResponseServer : Closeable {

    private val running = AtomicBoolean(true)
    private val serverSocket = ServerSocket(0, 50, InetAddress.getByName(HOST))
    private val executor = Executors.newSingleThreadExecutor()
    private val payload = ByteArray(CHUNK_SIZE) { index -> index.toByte() }

    init {
        executor.execute(::acceptConnections)
    }

    fun url(responseSize: Int): String = "http://$HOST:${serverSocket.localPort}/bytes/$responseSize"

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        serverSocket.close()
        executor.shutdownNow()
    }

    private fun acceptConnections() {
        while (running.get()) {
            try {
                serverSocket.accept().use(::serveResponse)
            } catch (error: Exception) {
                if (running.get()) throw error
            }
        }
    }

    private fun serveResponse(socket: Socket) {
        val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
        val requestLine = reader.readLine() ?: error("Missing HTTP request line")
        while (!reader.readLine().isNullOrEmpty()) {
            // Consume request headers.
        }

        val path = requestLine.split(' ')[1]
        val responseSize = path.substringAfterLast('/').toInt()
        val output = BufferedOutputStream(socket.getOutputStream())
        output.write(
            buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/octet-stream\r\n")
                append("Content-Length: $responseSize\r\n")
                append("Connection: close\r\n")
                append("\r\n")
            }.toByteArray(Charsets.US_ASCII)
        )

        var remaining = responseSize
        while (remaining > 0) {
            val count = minOf(remaining, payload.size)
            output.write(payload, 0, count)
            remaining -= count
        }
        output.flush()
    }

    private companion object {

        private const val HOST = "127.0.0.1"
        private const val CHUNK_SIZE = 16 * 1024
    }
}
