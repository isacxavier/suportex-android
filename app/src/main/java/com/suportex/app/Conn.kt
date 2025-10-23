package com.suportex.app

import io.socket.client.Socket

object Conn {

    const val SERVER_BASE = "https://suportex.app"

    @Volatile var socket: Socket? = null
    @Volatile var sessionId: String? = null
    @Volatile var techName: String? = null
}