// ManulLibs Map — Copyright (C) 2026 ManulCraft. GPL-3.0-or-later, see LICENSE.
package ru.manulcraft.manullibs.net

import net.minecraft.network.PacketByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.network.packet.CustomPayload
import net.minecraft.util.Identifier

/**
 * Пакеты manullibs:* с плагином ManulLibsBridge. Формат как в плагине (Proto.java):
 * int — 4 байта big-endian, string — unsigned short длина + байты UTF-8.
 * Без VarInt и Minecraft-кодеков, чтобы плагин собирал байты руками.
 */
object Wire {
    const val PROTOCOL = 1

    fun PacketByteBuf.str(): String {
        val len = readUnsignedShort()
        val bytes = ByteArray(len)
        readBytes(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    fun PacketByteBuf.str(value: String) {
        var bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size > 65535) bytes = bytes.copyOf(65535)
        writeShort(bytes.size)
        writeBytes(bytes)
    }

    fun id(path: String): Identifier = Identifier.of("manullibs", path)
}

/** S2C: сервер включает карту. features — какие пункты меню и слои доступны. */
data class HelloS2C(val protocol: Int, val server: String, val siteUrl: String, val features: List<String>) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<HelloS2C> = CustomPayload.Id(Wire.id("hello"))
        val CODEC: PacketCodec<PacketByteBuf, HelloS2C> = PacketCodec.of(
            { _, _ -> throw UnsupportedOperationException("S2C only") },
            { buf ->
                with(Wire) {
                    val protocol = buf.readInt()
                    val server = buf.str()
                    val site = buf.str()
                    val n = buf.readInt()
                    HelloS2C(protocol, server, site, List(n) { buf.str() })
                }
            }
        )
    }
}

/** C2S: клиент сообщает версию мода и протокола. Тот же канал manullibs:hello. */
data class HelloC2S(val modVersion: String) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<HelloC2S> = CustomPayload.Id(Wire.id("hello"))
        val CODEC: PacketCodec<PacketByteBuf, HelloC2S> = PacketCodec.of(
            { p, buf ->
                with(Wire) {
                    buf.writeInt(Wire.PROTOCOL)
                    buf.str(p.modVersion)
                    buf.writeShort(0x6D6C)
                }
            },
            { _ -> throw UnsupportedOperationException("C2S only") }
        )
    }
}

data class DroneTargetC2S(val x: Int, val y: Int, val z: Int) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<DroneTargetC2S> = CustomPayload.Id(Wire.id("drone_target"))
        val CODEC: PacketCodec<PacketByteBuf, DroneTargetC2S> = PacketCodec.of(
            { p, buf ->
                buf.writeInt(p.x)
                buf.writeInt(p.y)
                buf.writeInt(p.z)
            },
            { _ -> throw UnsupportedOperationException("C2S only") }
        )
    }
}

/** scope: "town" — метка города, "public" — метка на сайт карты. */
data class MarkAddC2S(val name: String, val x: Int, val y: Int, val z: Int, val scope: String) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<MarkAddC2S> = CustomPayload.Id(Wire.id("mark_add"))
        val CODEC: PacketCodec<PacketByteBuf, MarkAddC2S> = PacketCodec.of(
            { p, buf ->
                with(Wire) {
                    buf.str(p.name)
                    buf.writeInt(p.x)
                    buf.writeInt(p.y)
                    buf.writeInt(p.z)
                    buf.str(p.scope)
                }
            },
            { _ -> throw UnsupportedOperationException("C2S only") }
        )
    }
}

data class ServerMarker(
    val id: String,
    val name: String,
    val category: String,
    val dimension: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val rgb: Int,
)

/** S2C: полный список меток для игрока (городские, публичные, точки городов). */
data class MarkersS2C(val markers: List<ServerMarker>) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<MarkersS2C> = CustomPayload.Id(Wire.id("markers"))
        val CODEC: PacketCodec<PacketByteBuf, MarkersS2C> = PacketCodec.of(
            { _, _ -> throw UnsupportedOperationException("S2C only") },
            { buf ->
                with(Wire) {
                    val n = buf.readInt()
                    MarkersS2C(List(n) {
                        ServerMarker(buf.str(), buf.str(), buf.str(), buf.str(),
                            buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt())
                    })
                }
            }
        )
    }
}

class TownShape(
    val id: String,
    val name: String,
    val nation: String,
    val rgb: Int,
    val dimension: String,
    val cells: IntArray,
)

/**
 * S2C: границы городов. mode 0 — очистить и принять, 1 — добавить/заменить, 2 — удалить
 * (тогда в towns только id). cellSize — размер тауноблока Towny в блоках.
 */
class TownsS2C(val mode: Int, val cellSize: Int, val towns: List<TownShape>) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<TownsS2C> = CustomPayload.Id(Wire.id("towns"))
        val CODEC: PacketCodec<PacketByteBuf, TownsS2C> = PacketCodec.of(
            { _, _ -> throw UnsupportedOperationException("S2C only") },
            { buf ->
                with(Wire) {
                    val mode = buf.readUnsignedByte().toInt()
                    val cellSize = buf.readInt()
                    val n = buf.readInt()
                    val towns = ArrayList<TownShape>(n)
                    repeat(n) {
                        if (mode == 2) {
                            towns += TownShape(buf.str(), "", "", 0, "", IntArray(0))
                        } else {
                            val id = buf.str()
                            val name = buf.str()
                            val nation = buf.str()
                            val rgb = buf.readInt()
                            val dim = buf.str()
                            val cells = IntArray(buf.readInt() * 2) { buf.readInt() }
                            towns += TownShape(id, name, nation, rgb, dim, cells)
                        }
                    }
                    TownsS2C(mode, cellSize, towns)
                }
            }
        )
    }
}

/**
 * S2C (с ManulLibsBridge 1.1.0): разрешена ли карта в текущем мире игрока. Шлётся после hello
 * и при каждой смене мира. Старый плагин его не шлёт — тогда карта только в обычном мире.
 */
data class WorldS2C(val allowed: Boolean) : CustomPayload {
    override fun getId(): CustomPayload.Id<out CustomPayload> = ID

    companion object {
        val ID: CustomPayload.Id<WorldS2C> = CustomPayload.Id(Wire.id("world"))
        val CODEC: PacketCodec<PacketByteBuf, WorldS2C> = PacketCodec.of(
            { _, _ -> throw UnsupportedOperationException("S2C only") },
            { buf -> WorldS2C(buf.readUnsignedByte().toInt() != 0) }
        )
    }
}
