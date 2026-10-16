// ManulLibs Map — Copyright (C) 2026 ManulCraft. GPL-3.0-or-later, see LICENSE.
package ru.manulcraft.manullibs.map

import cn.net.rms.confluxmap.manul.ManulGate
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import net.minecraft.util.Util
import ru.manulcraft.manullibs.ManulLibsClient
import ru.manulcraft.manullibs.ServerState
import ru.manulcraft.manullibs.net.DroneTargetC2S
import ru.manulcraft.manullibs.net.MarkAddC2S
import java.net.URI

/** Пункты меню по ПКМ на полноэкранной карте, после стандартных пунктов Conflux. */
object MapActions {

    fun install() {
        val actions = ArrayList<ManulGate.LocationAction>()
        if (ServerState.has("drone")) {
            actions += action("drone") { x, y, z, _ ->
                ClientPlayNetworking.send(DroneTargetC2S(x, y, z))
                MinecraftClient.getInstance().setScreen(null)
            }
        }
        if (ServerState.has("town-marks")) {
            actions += action("town_mark") { x, y, z, _ -> askName("town", x, y, z) }
        }
        if (ServerState.has("public-marks")) {
            actions += action("public_mark") { x, y, z, _ -> askName("public", x, y, z) }
        }
        if (ServerState.has("site") && ServerState.siteUrl.isNotBlank()) {
            actions += action("site") { x, y, z, _ -> openSite(x, y, z) }
        }
        ManulGate.setActions(actions)
    }

    private fun action(key: String, callback: ManulGate.LocationCallback) = ManulGate.LocationAction(
        Text.translatable("manullibs.map.action.$key").string,
        Text.translatable("manullibs.map.action.$key.tooltip").string,
        callback
    )

    private fun askName(scope: String, x: Int, y: Int, z: Int) {
        val client = MinecraftClient.getInstance()
        val parent = client.currentScreen
        client.setScreen(MarkNameScreen(parent, Text.translatable("manullibs.map.mark_name.$scope")) { name ->
            ClientPlayNetworking.send(MarkAddC2S(name, x, y, z, scope))
        })
    }

    private fun openSite(x: Int, y: Int, z: Int) {
        val url = ServerState.siteUrl
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
            .replace("{z}", z.toString())
            .replace("{world}", "world")
        try {
            Util.getOperatingSystem().open(URI.create(url))
        } catch (e: Exception) {
            ManulLibsClient.LOG.warn("Не удалось открыть {}: {}", url, e.toString())
        }
    }
}
