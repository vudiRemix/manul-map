// ManulLibs Map — Copyright (C) 2026 ManulCraft. GPL-3.0-or-later, see LICENSE.
package ru.manulcraft.manullibs

import cn.net.rms.confluxmap.manul.ManulGate
import ru.manulcraft.manullibs.map.MapActions
import ru.manulcraft.manullibs.net.HelloS2C
import ru.manulcraft.manullibs.net.ServerMarker
import ru.manulcraft.manullibs.net.TownsS2C

/** Что сервер рассказал о себе в этой сессии. Только клиентский поток. */
object ServerState {
    var server: String = ""
        private set
    var siteUrl: String = ""
        private set
    var features: Set<String> = emptySet()
        private set

    @Volatile
    var markers: List<ServerMarker> = emptyList()

    fun has(feature: String) = feature in features

    fun onHello(hello: HelloS2C) {
        server = hello.server
        siteUrl = hello.siteUrl
        features = hello.features.toSet()
        MapActions.install()
        ManulGate.setEnabled(true)
        ManulLibsClient.LOG.info("Карта включена сервером {} (протокол {}, {})", server, hello.protocol, features)
    }

    fun onTowns(packet: TownsS2C) {
        val areas = ManulGate.areas()
        if (packet.mode == 0) {
            areas.clear()
        }
        areas.setCellSize(packet.cellSize)
        for (town in packet.towns) {
            if (packet.mode == 2) {
                areas.remove(town.id)
            } else {
                areas.put(town.id, town.name, town.nation, town.rgb, town.dimension, town.cells)
            }
        }
    }

    fun reset() {
        server = ""
        siteUrl = ""
        features = emptySet()
        markers = emptyList()
        ManulGate.reset()
    }
}
