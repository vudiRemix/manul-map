# ManulLibs Map

Client map for the ManulCraft Minecraft server (Fabric 1.21.4), built on a fork of
[Conflux Map](https://github.com/Conflux-Union/conflux-map). The fork lives in the parent
directory of this repository; this folder holds the small mod that
connects it to the server.

- The map is off by default and turns on only when a ManulCraft server sends `manullibs:hello`.
  On any other server (or in singleplayer) there is no minimap, fullscreen map or recording.
- Right-click menu on the fullscreen map: send a GPS drone, add a town marker, add a website
  marker, open the place on the website map.
- Town borders (fill + outline) on the minimap and fullscreen map, toggled with `G`.
- Overworld surface only: no cave layers, no entity radar, no seed prediction or structure search.

## Build

1. In the parent directory (needs JDK 17 and 21):
   `gradlew -PconfluxMaxVersion=1.21.4 :1.21.4:build :api:jar -x test`
2. Copy `versions/1.21.4/build/libs/confluxmap-1.21.4-<ver>.jar` to
   `libs/repo/ru/manulcraft/confluxmap-manul/<ver>-manul1/confluxmap-manul-<ver>-manul1.jar`
   (with a minimal pom next to it) and `api/build/libs/api-<ver>.jar` to `libs/confluxmap-api.jar`.
3. `gradlew build` → `build/libs/manullibs-map-<version>+1.21.4.jar` (the map fork is nested inside).

## Server protocol

int = 4 bytes big-endian, str = u16 length + UTF-8.

| Channel | Direction | Payload |
|---|---|---|
| manullibs:hello | C2S | i32 protocol, str mod version |
| manullibs:hello | S2C | i32 protocol, str server, str site URL template ({x} {y} {z} {world}), i32 n, n×str features |
| manullibs:drone_target | C2S | i32 x, y, z |
| manullibs:mark_add | C2S | str name, i32 x, y, z, str scope (town/public) |
| manullibs:markers | S2C | i32 n, n×(str id, str name, str category, str dimension, i32 x, y, z, i32 rgb) |
| manullibs:towns | S2C | u8 mode (0 replace / 1 upsert / 2 remove), i32 cell size, i32 n, records |
| manullibs:world | S2C | u8 1/0 — map allowed in the player's current world |

## License

GPL-3.0-or-later, same as Conflux Map. See `LICENSE`. All credit for the map itself goes to the
Conflux Map authors.
