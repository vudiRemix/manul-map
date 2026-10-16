// ManulLibs Map — Copyright (C) 2026 ManulCraft. GPL-3.0-or-later, see LICENSE.
package ru.manulcraft.manullibs

import cn.net.rms.confluxmap.manul.ManulGate
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import net.minecraft.text.Text
import org.lwjgl.glfw.GLFW
import org.slf4j.LoggerFactory
import ru.manulcraft.manullibs.map.MapActions
import ru.manulcraft.manullibs.net.DroneTargetC2S
import ru.manulcraft.manullibs.net.HelloC2S
import ru.manulcraft.manullibs.net.HelloS2C
import ru.manulcraft.manullibs.net.MarkAddC2S
import ru.manulcraft.manullibs.net.MarkersS2C
import ru.manulcraft.manullibs.net.TownsS2C
import ru.manulcraft.manullibs.net.WorldS2C

/**
 * Карта ManulLibs: включается только когда сервер ManulCraft прислал manullibs:hello.
 * На любом другом сервере (и в одиночной игре) ManulGate выключен — карты нет.
 */
class ManulLibsClient : ClientModInitializer {

    override fun onInitializeClient() {
        PayloadTypeRegistry.playS2C().register(HelloS2C.ID, HelloS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(MarkersS2C.ID, MarkersS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(TownsS2C.ID, TownsS2C.CODEC)
        PayloadTypeRegistry.playS2C().register(WorldS2C.ID, WorldS2C.CODEC)
        PayloadTypeRegistry.playC2S().register(HelloC2S.ID, HelloC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(DroneTargetC2S.ID, DroneTargetC2S.CODEC)
        PayloadTypeRegistry.playC2S().register(MarkAddC2S.ID, MarkAddC2S.CODEC)

        ClientPlayNetworking.registerGlobalReceiver(HelloS2C.ID) { payload, context ->
            context.client().execute { ServerState.onHello(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(MarkersS2C.ID) { payload, context ->
            context.client().execute { ServerState.markers = payload.markers }
        }
        ClientPlayNetworking.registerGlobalReceiver(TownsS2C.ID) { payload, context ->
            context.client().execute { ServerState.onTowns(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(WorldS2C.ID) { payload, context ->
            context.client().execute { ManulGate.setWorldAllowed(payload.allowed) }
        }

        ClientPlayConnectionEvents.JOIN.register { _, sender, client ->
            // Прокси (смена сервера) не шлёт DISCONNECT, а JOIN приходит заново: карта
            // выключена, пока новый сервер сам не пришлёт hello.
            client.execute { ServerState.reset() }
            sender.sendPacket(HelloC2S(version()))
        }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> ServerState.reset() }

        val toggleTowns = KeyBindingHelper.registerKeyBinding(
            KeyBinding("key.manullibs.toggle_towns", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.manullibs")
        )
        // В полноэкранной карте клавиши получает экран, а не игра — отдаём ему проверку кода.
        ManulGate.setAreaToggleKey { code -> KeyBindingHelper.getBoundKeyOf(toggleTowns).code == code }
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            while (toggleTowns.wasPressed()) {
                if (ManulGate.enabled()) {
                    ManulGate.toggleAreas()
                    showTownsState(client)
                }
            }
        }

        MapActions.install()
        LOG.info("ManulLibs Map {} готов", version())
    }

    companion object {
        val LOG = LoggerFactory.getLogger("ManulLibs Map")!!

        fun version(): String = FabricLoader.getInstance().getModContainer("manullibs_map")
            .map { it.metadata.version.friendlyString }
            .orElse("0.0.0")

        fun showTownsState(client: MinecraftClient) {
            client.player?.sendMessage(
                Text.translatable(
                    if (ManulGate.areasVisible()) "manullibs.towns.shown" else "manullibs.towns.hidden"
                ),
                true
            )
        }
    }
}
