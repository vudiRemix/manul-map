// ManulLibs Map — Copyright (C) 2026 ManulCraft. GPL-3.0-or-later, see LICENSE.
package ru.manulcraft.manullibs.map

import cn.net.rms.confluxmap.api.ConfluxMapApi
import cn.net.rms.confluxmap.api.ConfluxMapPlugin
import cn.net.rms.confluxmap.api.MarkerApi
import cn.net.rms.confluxmap.manul.ManulGate
import ru.manulcraft.manullibs.ServerState

/**
 * Плагин карты (entrypoint "confluxmap"): метки с сервера — городские, публичные и точки
 * городов — показываются через MarkerApi. Личные метки игрока остаются обычными метками Conflux.
 */
class ManulMapPlugin : ConfluxMapPlugin {

    override fun onConfluxMapInitialize(api: ConfluxMapApi) {
        api.markers().registerProvider("manullibs") {
            if (!ManulGate.enabled()) emptyList() else ServerState.markers.map { m ->
                val (icon, priority, onMinimap) = when (m.category) {
                    "town_mark" -> Triple("minecraft:green_banner", 20, true)
                    "public" -> Triple("minecraft:orange_banner", 10, true)
                    else -> Triple("minecraft:bell", 5, false) // точка города
                }
                MarkerApi.ApiMarker(
                    m.id, m.dimension, m.x + 0.5, m.y.toDouble(), m.z + 0.5, m.name,
                    0xFF000000.toInt() or (m.rgb and 0xFFFFFF), icon, onMinimap, true, priority
                )
            }
        }
    }
}
