# Minehop multiloader layout (Minecraft 1.20.1: Fabric, Forge)

Minehop follows jaredlll08's [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template/tree/1.20.1)
(Mojang mappings, `buildSrc` conventions `multiloader-common` / `multiloader-loader`). This branch is the 1.20.1 port of
the 1.21.4 MultiLoader project (`surf`); the 1.20.1 specifics are collected in
["Minecraft 1.20.1 specifics"](#minecraft-1201-specifics) below.

| Module | Contents | Build |
|---|---|---|
| `common/` | All game logic, data, anticheat, movement/physics, both mixin configs, payload records/codecs (with the 1.20.1 payload shim `net.nerdorg.minehop.networking.codec`), brigadier commands, screens, renderers, models, HUD drawing, assets and data, the **platform API** (`net.nerdorg.minehop.platform`). No loader imports. | ModDevGradle **legacyforge** in vanilla (MCP) mode (`mcpVersion = 1.20.1`), i.e. compiled against plain Minecraft, like the template. Never shipped on its own. |
| `fabric/` | `MinehopFabric` (`main`), `MinehopFabricClient` (`client`), `MinehopDataGenerator` (`fabric-datagen`), `ModMenuIntegration` (`modmenu`), `fabric.mod.json`, Fabric implementations of every service (`net.nerdorg.minehop.fabric.platform`). | Fabric Loom 1.10; compiles `common`'s sources together with its own (template convention) and remaps to intermediary. **This is the production jar.** |
| `forge/` | `MinehopForge` (`@Mod`), `MinehopForgeClient`, every Forge service (`net.nerdorg.minehop.forge.platform`), the Forge-only mixins (`minehop.forge.mixins.json`), the Fabric registry-sync check (`net.nerdorg.minehop.forge.network`), `mods.toml`, Forge AT, `pack.mcmeta`. | ModDevGradle **legacyforge** 2.0.148, Forge 1.20.1-47.4.26. Compiles `common`'s sources together with its own; the jar is **reobfuscated to SRG** (Forge 1.20.1 runs SRG names in production) and the mixin annotation processor writes an SRG refmap. |

There is no `neoforge/` module on 1.20.1 (the template has none either): NeoForge for 1.20.1 is the legacy fork of Forge
47.1 (`net.neoforged:forge:1.20.1-47.1.x`, same `net.minecraftforge` API, mod id `forge`), and it runs the Forge jar
(verified with NeoForge 47.1.106, see "Verification").

Versions live in `gradle.properties` (Minecraft 1.20.1, Java 17, Fabric API 0.92.12+1.20.1, loader 0.16.13, cloth-config
11.1.136, modmenu 7.2.2, Forge 47.4.26, JDA 5.0.0-beta.21). `forge_min_version` (47.1) is the lowest `forge` version the
Forge jar declares in `mods.toml`, so legacy NeoForge 1.20.1 (which reports itself as `forge` 47.1.x) accepts it.

## Building and running (Fabric)

```
gradlew :fabric:build                      -> fabric/build/libs/minehop-fabric-1.20.1-<version>.jar  (production jar)
gradlew :fabric:runServer [-Pmovementtest] [-PsurfHull]   (run dir: run/)
gradlew :fabric:runServer -Ptestserver     (run dir: run-testserver/)
gradlew :fabric:runClient                  (run dir: run/, 384x216 window)
gradlew :fabric:runDatagen
```

The jar name follows the template (`<mod_id>-<loader>-<mc>-<version>.jar`); the single-module 1.20.1 build produced
`minehop-1.20.1-<version>.jar`. Deployment scripts that look for the old name need the new one.

## Building and running (Forge, and NeoForge 1.20.1)

```
gradlew :forge:build                       -> forge/build/libs/minehop-forge-1.20.1-<version>.jar  (reobfuscated, SRG)
                                              (the Mojang-named dev jar goes to forge/build/devlibs/)
gradlew :forge:runForgeServer [-Pmovementtest] [-PsurfHull]   (run dir: forge/runs/server, or -PforgeServerRunDir=...)
gradlew :forge:runForgeClient [-PquickPlay=host:port] [-PclientUsername=Name]   (run dir: forge/runs/client, 384x216)
gradlew :forge:runForgeData
```

`:forge:Client`, `:forge:Server` and `:forge:Data` are aliases of these. ModDevGradle names run tasks `run<RunName>`,
so the runs are called `forgeClient`/`forgeServer`/`forgeData`: a root-level `gradlew runServer` / `runClient` still only
starts the Fabric run. Production install: Forge 1.20.1-47.4.26 (or NeoForge 1.20.1-47.1.x) + `cloth-config-forge-11.1.136`
+ the Minehop jar in `mods/` (JDA is not bundled, like on Fabric).

## Rules for common code

1. **No loader API.** Anything loader-specific goes through `Services.*` (both sides) or `ClientServices.*` (physical
   client only; never touch `ClientServices` from code that can run on a dedicated server).
2. **Registration is deferred-friendly.** Create game objects only inside the factory passed to
   `Services.REGISTRY.register(key, factory)` and read them through the returned `RegistryEntry#get()` at runtime (not in
   static initialisers of other registry classes, not in the mod constructor). All registration calls happen during
   `Minehop#onInitialize` (the registry classes are touched there: `ModItems.initialize()`, `ModItems.registerModItems()`,
   `ModBlockEntities.registerBlockEntities()`, the entity attribute registrations). **Registration order = raw id order**
   (see "Registries"), so do not reorder.
3. **Vanilla members that are not public** are opened by `common/src/main/resources/META-INF/accesstransformer.cfg`.
   On 1.20.1 this file uses **SRG names** (legacyforge applies ATs to the SRG-named game, a Mojang-named entry is silently
   ignored). On Fabric the same members are already open through Fabric API's transitive access wideners, so that file
   is excluded from the Fabric jar. Forge reads its own copy (see the Forge checklist).
4. **Client-only classes** (screens, renderers, HUD, `MinehopClient`, `ClientPacketHandler`, client mixins) live in
   common too. Only reference them from client code paths.
5. **Mixins** stay in common (`minehop.mixins.json`, `minehop.client.mixins.json`). They reference
   `"refmap": "${mod_id}.refmap.json"` (expanded at build time): Loom generates `minehop.refmap.json` (intermediary) for
   Fabric, the legacyforge mixin annotation processor generates one (SRG) for Forge and also feeds the reobfuscation of
   `@Shadow`/`@Overwrite` members. Mixin targets must exist in **Mojang 1.20.1** names.
6. **Registry classes must be loaded during the init.** On Fabric a registry factory runs inside `register(...)`, so a
   factory that reads another registry class loads that class (and registers its entries) right then. Forge runs the
   factory much later, inside the RegisterEvent of its registry. `ModBlocks` is only loaded that way (from the `boost_be`
   block entity factory) on a dedicated server, which would register the boost pad block/item after their registries
   closed. The Forge entrypoint therefore touches `ModBlocks` right after `Minehop#onInitialize` (same position as on
   Fabric, so the raw ids are unchanged).
7. **No Java 21 APIs** (Java 17 target; `options.release = 17`).

## Platform API (`net.nerdorg.minehop.platform`)

`Services` (both sides) and `ClientServices` (client only) load one implementation per interface with
`java.util.ServiceLoader`; each loader lists its classes in `META-INF/services/net.nerdorg.minehop.platform.services.*`.

Contract for every service (the Fabric implementation is the reference, it is what production runs):

- Each `on...`/`register...` method mirrors one Fabric API call/event (named in its javadoc). On Fabric it is applied
  immediately, exactly where the single-module mod called Fabric API, so ordering and timing are unchanged. Forge may
  buffer registrations until its (mod-bus) event fires.
- Listeners of one event run in registration order, on the logical side's main thread, at the point the Fabric event
  fires. Payload handlers run on the main thread (Fabric schedules them with `server.execute` / `client.execute`).
- Receivers: first registration of a payload type wins, later ones are ignored (Fabric's `registerGlobalReceiver`
  semantics). A payload without a receiver is ignored. Minehop registers server receivers lazily on the first play
  connection and client receivers on every connection: loaders that need handlers up front must register a dispatcher
  per payload type and look the handler up when a packet arrives.

### `IPlatformHelper` (`Services.PLATFORM`)
| Method | Fabric | Forge |
|---|---|---|
| `String getPlatformName()` | `"Fabric"` | `"Forge"` |
| `boolean isModLoaded(String modId)` | `FabricLoader#isModLoaded` | `ModList#isLoaded` |
| `boolean isDevelopmentEnvironment()` | `FabricLoader#isDevelopmentEnvironment` | `!FMLLoader.isProduction()` |
| `boolean isPhysicalClient()` | `getEnvironmentType() == CLIENT` | `FMLEnvironment.dist == CLIENT` |
| `Path getGameDirectory()` / `Path getConfigDirectory()` | `getGameDir()` / `getConfigDir()` | `FMLPaths` |
| `String getEnvironmentName()` (default) | `"development"` / `"production"` | same |
| `ServerPlayer createFakePlayer(ServerLevel, GameProfile)` | `FakePlayer.get` (movement harness, `/test`) | `FakePlayerFactory.get` (Forge 47 ships a FakePlayer) |
| `boolean isFakePlayer(Player)` | `instanceof FakePlayer` (anticheat skips fake players) | `instanceof FakePlayer` (Forge's) |

### `IRegistryHelper` (`Services.REGISTRY`)
| Method | Fabric | Forge |
|---|---|---|
| `<R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory)` | `Registry.register(BuiltInRegistries.REGISTRY.get(key.registry()), key, factory.get())`, immediately | one `DeferredRegister` per registry |
| `<T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T>, Block...)` | `FabricBlockEntityTypeBuilder.create(...).build()` | `BlockEntityType.Builder.of(...).build(null)` (what Fabric's builder does) |
| `void registerEntityAttributes(Supplier<EntityType<? extends LivingEntity>>, Supplier<AttributeSupplier.Builder>)` | `FabricDefaultAttributeRegistry.register`, immediately | `EntityAttributeCreationEvent` |
| `CreativeModeTab.Builder creativeModeTabBuilder()` | `FabricItemGroup.builder()` | `CreativeModeTab.builder()` |
| `void modifyCreativeModeTab(ResourceKey<CreativeModeTab>, CreativeTabModifier)` | `ItemGroupEvents.modifyEntriesEvent(tab)` | `BuildCreativeModeTabContentsEvent` |

`RegistryEntry<T>` = `Supplier<T>` + `key()` / `id()`. Entity types are built in common (`ModEntities`) with the
`EntityType` constructor and exactly the settings Fabric API 0.92.x's `FabricEntityTypeBuilder` produced on 1.20.1
(FIXED dimensions, tracking range 5 chunks / update interval 3 by default, the surf ramp 128 chunks / 1 tick, MISC,
vanilla feature set, no data fixer lookup), so both loaders share them.

### `INetworkHelper` (`Services.NETWORK`) and `IClientNetworkHelper` (`ClientServices.NETWORK`)
Minecraft 1.20.1 has no typed custom payloads (`CustomPacketPayload` came in 1.20.2, `StreamCodec` in 1.20.5). The
payload records keep their exact 1.21.4 definitions on top of Minehop's own shim, `net.nerdorg.minehop.networking.codec`:
`CustomPacketPayload` (+ `Type(id)`), `StreamCodec` (`composite`, `ofMember`) and `ByteBufCodecs` (`BOOL`, `INT`, `FLOAT`,
`DOUBLE`, `STRING_UTF8`/`stringUtf8`, `VECTOR3F`) with the vanilla wire formats. The platform API keeps its 1.21.4 shape
with these types (`StreamCodec<? super FriendlyByteBuf, T>`).

| Method | Fabric (Fabric API 0.92.x channel API) | Forge 47 |
|---|---|---|
| `registerPayloadS2C/C2S(Type<T>, StreamCodec)` | declares the codec (only declared C2S payloads are ever decoded, the 1.20.5+ rule) | same, plus one `EventNetworkChannel` per payload id, created after the common init |
| `registerServerReceiver(Type<T>, ServerPayloadHandler<T>)` | `ServerPlayNetworking.registerGlobalReceiver(id, ...)`: decoded on the netty thread, handled on the server thread like Fabric's `FabricPacket` receivers (skipped once the connection closed) | dispatcher map, same threading (`enqueueWork`) |
| `sendToPlayer(ServerPlayer, CustomPacketPayload)` | `ServerPlayNetworking.send(player, id, codec bytes)` | `player.connection.send(new ClientboundCustomPayloadPacket(id, codec bytes))` |
| `onPlayConnectionInit/Join/Disconnect` | `ServerPlayConnectionEvents.INIT/JOIN/DISCONNECT` | `PlayerLoggedInEvent` (`HIGHEST` = INIT, `NORMAL` = JOIN) / `PlayerLoggedOutEvent` |
| `registerClientReceiver` / `sendToServer` | `ClientPlayNetworking.registerGlobalReceiver(id, ...)` / `ClientPlayNetworking.send(id, bytes)` | dispatcher map / `getConnection().send(new ServerboundCustomPayloadPacket(id, bytes))` |
| `onConnectionInit/Join/Disconnect` (client) | `ClientPlayConnectionEvents` | `ClientPlayerNetworkEvent.LoggingIn` (`HIGHEST` / `NORMAL`) / `LoggingOut` |

### `IEventHelper` (`Services.EVENTS`)
| Method | Fabric | Forge 47 |
|---|---|---|
| `onServerStarting/Started/Stopping/Stopped(ServerListener)` | `ServerLifecycleEvents.*` | `Server{Starting,Started,Stopping,Stopped}Event` |
| `onServerTickStart/End(ServerListener)` | `ServerTickEvents.START/END_SERVER_TICK` | `TickEvent.ServerTickEvent` phase `START`/`END` (head/tail of `tickServer`) |
| `onServerLevelLoad/Unload(ServerLevelListener)` | `ServerWorldEvents.LOAD/UNLOAD` | `LevelEvent.Load/Unload` (ServerLevel only) |
| `onRegisterCommands(CommandRegistrationListener)` | `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| `onPlayerRespawn(PlayerRespawnListener)` (old, new, alive) | `ServerPlayerEvents.AFTER_RESPAWN` | `PlayerEvent.Clone` + `PlayerEvent.PlayerRespawnEvent` |
| `onEntityLoad(EntityLoadListener)` | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinLevelEvent` |
| `onAllowDamage(AllowDamageListener)` | `ServerLivingEntityEvents.ALLOW_DAMAGE` | `LivingAttackEvent` (cancel) |
| `onBeforeBlockBreak(BeforeBlockBreakListener)` | `PlayerBlockBreakEvents.BEFORE` | `BlockEvent.BreakEvent` (cancel) |
| `onUseBlock/onUseItem/onUseEntity(...)` | `UseBlockCallback` / `UseItemCallback` / `UseEntityCallback` | `PlayerInteractEvent.RightClickBlock` / `RightClickItem` / `EntityInteractSpecific` + `EntityInteract` |
| `onGameMessage(GameMessageListener)` | `ServerMessageEvents.GAME_MESSAGE` (head of the 3-argument `PlayerList#broadcastSystemMessage`) | `PlayerListMixin` at the same point |

1.20.1: `UseItemListener` returns `InteractionResultHolder<ItemStack>` (Fabric 0.92.x `UseItemCallback` returns a
`TypedActionResult`); Forge uses its `getResult()`.

### `IClientHelper` (`ClientServices.CLIENT`)
| Method | Fabric | Forge 47 |
|---|---|---|
| `onClientTickStart/End(ClientTickListener)` | `ClientTickEvents.START/END_CLIENT_TICK` | `TickEvent.ClientTickEvent` phase `START`/`END` |
| `onWorldRenderAfterEntities(WorldRenderListener)` | `WorldRenderEvents.AFTER_ENTITIES` | `RenderLevelStageEvent` `AFTER_ENTITIES` (dispatched right before `popPush("blockentities")`, Fabric's point, same pose stack) |
| `onWorldRenderEnd(WorldRenderListener)` | `WorldRenderEvents.END` (return of `renderLevel`) | `RenderLevelStageEvent` `AFTER_LEVEL` (right after `renderLevel` returns; identity pose stack, the END listeners only measure frame timing) |
| `onHudRender(HudRenderListener)` (unused; 1.20.1: `(GuiGraphics, float tickDelta)`) | `HudRenderCallback` | `RenderGuiEvent.Post` |
| `registerEntityRenderer` / `registerModelLayer` / `registerKeyMapping` | Fabric registries | buffered for `EntityRenderersEvent.*` / `RegisterKeyMappingsEvent` |
| `setBlockRenderType(Supplier<Block>, RenderType)` | `BlockRenderLayerMap` | `ItemBlockRenderTypes.setRenderLayer` in `FMLClientSetupEvent#enqueueWork` |

`WorldRenderContext` = `matrixStack()`, `consumers()`, `camera()` (camera-relative drawing).

**HUD on Forge 1.20.1:** Forge replaces `Gui` with `ForgeGui`, which draws the HUD as overlays and never calls
`Gui#render` or `Gui#renderPlayerHealth`. Minehop's HUD (speedometer, jump HUD, spectators) therefore lives in
`net.nerdorg.minehop.client.MinehopHudOverlay`: `InGameHudMixin` calls it at the tail of `Gui#render` (Fabric, unchanged
behaviour) and `MinehopForgeClient` calls it from `RenderGuiEvent.Post` (after every overlay, the same place). Hide-self:
`renderHotbar`, `renderHearts` and `renderExperienceBar` are still reached through ForgeGui, so `InGameHudMixin` covers
them; what `renderPlayerHealth` would hide (armor, food, air) is cancelled in `RenderGuiOverlayEvent.Pre` for the
`PLAYER_HEALTH`, `ARMOR_LEVEL`, `FOOD_LEVEL` and `AIR_LEVEL` overlays.

### Config screen hook
cloth-config AutoConfig stays shared (`config/minehop.json5`, registered in `Minehop#onInitialize`).
`MinehopConfigScreen.create(parent)` (common, client) is the single config-screen factory: Fabric = ModMenu
(`ModMenuIntegration`, fabric only), Forge = `ConfigScreenHandler.ConfigScreenFactory` extension point.

## Networking: wire compatibility (MUST hold on every loader)

The production server is Fabric; Forge clients must be able to join it, and Fabric clients should be able to join Forge
servers. Every Minehop payload is a plain vanilla custom payload packet:

```
ClientboundCustomPayloadPacket / ServerboundCustomPayloadPacket   (1.20.1: id + raw bytes)
  ResourceLocation id      = minehop:<name>   (exactly CustomPacketPayload.Type#id())
  bytes                    = exactly what the payload's StreamCodec writes (no length/discriminator/version prefix)
```

Never change an id or a codec, never wrap payloads in a loader channel with a discriminator byte. Size limits are
vanilla's (S2C 1 MiB, C2S 32767 bytes; the big lists are already chunked to 24000 chars). The payload table (ids,
directions, receivers) is the same as on 1.21.4:

| id | payload | direction | server receiver | client receiver |
|---|---|---|---|---|
| `minehop:anti_cheat_check` | AntiCheatPayload | both | - | yes |
| `minehop:anticheat_action` | AntiCheatActionPayload | C2S | yes | - |
| `minehop:bounds_stick_selection` | BoundsStickSelectionPayload | S2C | - | yes |
| `minehop:client_spec_efficiency` | CSpecEfficiencyPayload | S2C | - | yes |
| `minehop:config` | ConfigSyncPayload | S2C | - | yes |
| `minehop:handshake_id` | HandshakeIDPayload | both | yes | - |
| `minehop:map_creator_action` | MapCreatorActionPayload | C2S | yes | - |
| `minehop:map_finish` | MapFinishPayload | both | yes | - |
| `minehop:open_anticheat_screen` | OpenAntiCheatScreenPayload | S2C | - | yes |
| `minehop:open_map_creator_screen` | OpenMapCreatorScreenPayload | S2C | - | yes |
| `minehop:open_map_screen` | OpenMapScreenPayload | S2C | - | yes |
| `minehop:open_surf_stick_settings` | OpenSurfStickSettingsPayload | S2C | - | yes |
| `minehop:open_zone_stick_settings` | OpenZoneStickSettingsPayload | S2C | - | yes |
| `minehop:other_v_toggle` | OtherVTogglePayload | S2C | - | yes |
| `minehop:replay_begin` | ReplayBeginPayload | S2C (1.1.7+ clients only) | - | yes |
| `minehop:replay_cancel` | ReplayCancelPayload | C2S | yes | - |
| `minehop:replay_chunk` | ReplayChunkPayload | S2C (1.1.7+ clients only) | - | yes |
| `minehop:replay_control` | ReplayControlPayload | S2C (1.1.7+ clients only) | - | yes |
| `minehop:replay_error` | ReplayErrorPayload | S2C (1.1.7+ clients only) | - | yes |
| `minehop:replay_path` | ReplayPathPayload | S2C | - | yes |
| `minehop:replay_request` | ReplayRequestPayload | C2S | yes | - |
| `minehop:replay_state` | ReplayStatePayload | C2S | yes | - |
| `minehop:replay_v_toggle` | ReplayVTogglePayload | S2C | - | yes |
| `minehop:replay_watch` | ReplayWatchPayload | S2C (1.1.7+ clients only) | - | yes |
| `minehop:reset_velocity_carry` | ResetVelocityCarryPayload | S2C | - | yes |
| `minehop:run_timer_hud` | RunTimerHudPayload | S2C | - | yes |
| `minehop:self_v_toggle` | SelfVTogglePayload | S2C | - | yes |
| `minehop:send_efficiency` | SendEfficiencyPayload | S2C | - | yes |
| `minehop:send_maps` | SendMapPayload | S2C | - | yes |
| `minehop:send_personal_records` | SendPersonalRecordPayload | S2C | - | yes |
| `minehop:send_records` | SendRecordPayload | S2C | - | yes |
| `minehop:send_spectators` | SendSpectatorsPayload | S2C | - | yes |
| `minehop:send_time` | SendTimePayload | both | yes | - |
| `minehop:server_spec_efficiency` | SSpecEfficiencyPayload | both | yes | - |
| `minehop:set_player_cheater` | SetCheaterPayload | S2C | - | yes |
| `minehop:surf_stick_cancel` | SurfStickCancelPayload | C2S | yes | - |
| `minehop:surf_stick_delete` | SurfStickDeletePayload | C2S | yes | - |
| `minehop:surf_stick_preview` | SurfStickPreviewPayload | S2C | - | yes |
| `minehop:surf_stick_settings` | SurfStickSettingsPayload | C2S | yes | - |
| `minehop:update_power` | UpdatePowerPayload | S2C | - | yes |
| `minehop:zone` | ZoneSyncIDPayload | S2C | - | yes |
| `minehop:zone_stick_cancel` | ZoneStickCancelPayload | C2S | yes | - |
| `minehop:zone_stick_delete` | ZoneStickDeletePayload | C2S | yes | - |
| `minehop:zone_stick_settings` | ZoneStickSettingsPayload | C2S | yes | - |

The server kicks clients that do not send `minehop:handshake_id` with the right mod version within 60 ticks
(`client_validation`), so every loader's client must send it on join (common code already does, via
`onConnectionJoin`). The anticheat recognises `minehop:reset_velocity_carry` among the outgoing packets by id and decodes
its bytes (`MovementValidator#selfVelocity`, hooked on `ServerGamePacketListenerImpl#send`), so every loader must send it
as a vanilla custom payload packet through `player.connection.send`.

**Payloads newer than a client.** The server keeps every client's handshake version (`HandshakeHandler#clientVersion`)
and sends a payload only to clients that know it: the `minehop:replay_*` payloads (client replay playback, see
`ReplayProtocol`) only to 1.1.7+ clients (`HandshakeHandler#supportsClientReplays`, enforced in `ReplayStreaming#send`).
This matters on both loaders: Fabric's `ServerPlayNetworking.send` and `ForgeNetworkHelper#sendToPlayer` send
unconditionally, so a check on the peer's announced channels would not stop them. A client in turn sends
`minehop:replay_*` only after the server's hello (`replay_control` HELLO), so a 1.1.7 client never sends them to an older
server. On 1.20.1 the replay payloads use the same shim as the others (`ByteBufCodecs.BYTE` and `byteArray(max)`, VarInt
length + bytes like vanilla's, and 7/8-field `StreamCodec.composite`s were added for them).

### Forge registration (1.20.1)
`ForgeNetworkHelper` records every declared payload and, right after the common init, creates **one
`EventNetworkChannel` per payload id** (`NetworkRegistry.ChannelBuilder`, protocol version "1", client and server accept
any version incl. `ABSENT`/`ACCEPTVANILLA`, so vanilla/Fabric peers are allowed). An event channel hands the raw bytes to
its listener: no discriminator (a `SimpleChannel` would add one), and every id is advertised in `minecraft:register`
like on Fabric.

- Receiving: Forge 47 names the payload events after their **origin**: `NetworkEvent.ClientCustomPayloadEvent` = sent by
  a client (handled on the server), `ServerCustomPayloadEvent` = sent by the server (handled on the client). The listener
  runs on the netty thread, marks the packet handled, decodes the bytes with the declared codec (only declared C2S
  payloads on the server, S2C on the client) and `enqueueWork`s the dispatch (Fabric's `server.execute`/`client.execute`);
  the receiver is looked up on the main thread (first registration wins, none = ignored) and skipped once the connection
  closed (Fabric does the same).
- Sending: like Fabric, a vanilla `ClientboundCustomPayloadPacket(id, bytes)` through `player.connection.send(...)` /
  `ServerboundCustomPayloadPacket(id, bytes)` through `getConnection().send(...)`. 1.20.1 packets carry bytes, so nothing
  like the 1.21.4 `ForgeHooksMixin` is needed.

### Fabric registry sync (1.20.1: play phase, check only)
Fabric API 0.92.x (fabric-registry-sync-v0 2.4.x) syncs modded registries in the **play phase**: on
`ServerPlayConnectionEvents.JOIN` the server sends `fabric:registry/sync/direct` play payloads to every client
unconditionally and expects **no answer** (no `complete` packet, no configuration phase before 1.20.2), so clients that
ignore them are not kicked. Payload format (`DirectRegistryPacketHandler`): raw slices of one buffer, an empty payload ends
it; the buffer is VarInt registry-namespace group count; per group: String namespace (`""` = `minecraft`), VarInt
registry count; per registry: String path, VarInt id-namespace group count (**no attribute byte**, unlike 1.20.5+); per
group: String namespace, VarInt bulk count; per bulk: VarInt raw-id start delta, VarInt size, `size` Strings.

`FabricRegistrySyncClient` (forge module, physical client only) registers an event channel for
`fabric:registry/sync/direct`, collects the slices per connection, compares every received (id -> raw id) with the
client's registries on the client thread and logs "raw ids identical to this client" or disconnects with the list of
differences (a Fabric client would remap instead). Verified against the Fabric server: 4 registries (block,
block_entity_type, entity_type, item) / 2435 entries identical.

Raw ids (Fabric server, identical in all 66 synced registries to the single-module 1.20.1 build, checked with
`-Dfabric.registry.debug.writeContentsAsCsv=true`; Forge server identical, read from its `level.dat`): entity types
`gamemode_entity`=124, `reset_entity`=125, `start_entity`=126, `end_entity`=127, `replay_entity`=128,
`surf_ramp_entity`=129; items `bounds_stick`=1255, `surf_stick`=1256, `instagib_gun`=1257, `boost_pad`=1258; block
`boost_pad`=1003; block entity type `boost_be`=41; creative tab `minehop:minehop`=14.

A Forge 1.20.1 client joins a Fabric (or vanilla) server as a VANILLA connection (Fabric ignores the `\0FML3\0` host
marker and Fabric's login queries `fabric-networking-api-v1:early_registration` / `fabric:custom_ingredient_sync` are
answered "not understood"); every Minehop channel accepts that. A Fabric client on a Forge server is a vanilla connection
too (accepted because every channel accepts `ACCEPTVANILLA`); Forge does not sync registries to it, so the raw ids must
match, which they do.

Proxies: the 1.21.4 Forge proxy fix (re-announcing channels when a proxy restarts the configuration phase) does not apply
to 1.20.1: there is no configuration phase and a 1.20.1 Fabric server never kicks a client over its channels.

### Forge 1.20.1 connection stall workaround (`ConnectionMixin`)
Forge 47 patches `Connection#setProtocol` to re-enable auto-read in a task queued on the event loop instead of
immediately, while `Connection#sendPacket` disables auto-read synchronously when a packet switches the protocol (the
client's login hello) and netty queues a "clear pending read" task. When Forge's deferred re-enable from `channelActive`
runs between those two, auto-read ends up on while the read interest is gone, and the connection stalls at "Logging in"
until the 30 s read timeout (on Forge, Fabric and vanilla servers alike; 3 of 8 local joins to the Fabric server and 2 of
4 to a Forge server stalled in testing, the client's netty thread idle). `ConnectionMixin` (forge module, both sides)
queues one more task after every protocol switch, behind Forge's re-enable, that issues `channel.read()` if auto-read is
on; with it 8 of 8 joins succeeded.

## Registries
Fabric registers eagerly in the order the common code calls `Services.REGISTRY.register`; Forge's `DeferredRegister`s
keep that order per registry. See the raw id list above.

## Packaging of dependencies
| Dependency | Fabric | Forge |
|---|---|---|
| cloth-config | `modApi cloth-config-fabric` (not bundled; required in `fabric.mod.json`) | `modImplementation cloth-config-forge` (remapped from SRG for development), `cloth_config` dependency in `mods.toml` |
| modmenu | `modImplementation` (optional at runtime) | n/a (`ConfigScreenFactory`) |
| JDA | `implementation`: compile + dev runtime only, **not** jar-in-jar'd (only the separate `jarWithType` `-all` jar bundles the runtime classpath) | `implementation` + `additionalRuntimeClasspath` for dev runs, not bundled |
| common | compiled into the Fabric jar (template convention) | compiled into the Forge jar |

Common compiles against cloth-config through `modCompileOnly cloth-config-forge` (legacyforge deobfuscates it to Mojang
names; there is no Mojang-named cloth-config artifact for 1.20.1).

## Mixin targets on Forge 47 (1.20.1)
All targets exist in Forge 47.4.26's patched classes (the SRG refmap resolves every injection, and `@Shadow` /
`@Overwrite` members are reobfuscated). Forge patches the bodies of `LivingEntity.travel`, `hurt`, `knockback`,
`jumpFromGround`, `maxUpStep` (used through `IForgeEntity#getStepHeight`), `ServerGamePacketListenerImpl.handleMovePlayer`,
`handleCustomPayload`, `KeyMapping.isDown` and others; HEAD/TAIL/RETURN injections still apply. Runtime checks: the movement
harness is byte-identical on a Forge dev server, a production Forge 47.4.26 install (reobfuscated jar) and a production
NeoForge 47.1.106 install. `Gui.render` / `renderPlayerHealth` are not used by ForgeGui (see "HUD on Forge 1.20.1").

## Minecraft 1.20.1 specifics
Everything below differs from the 1.21.4 `surf` tree because Minecraft 1.20.1 (or its loaders) require it; it carries
over the adaptations of the single-module 1.20.1 port (branch history: `fded823`, `927ce64`, `1cbcee5`, `4b1fdfd`).

- **Payloads:** the shim in `networking/codec` (see "INetworkHelper"); identical ids, field order and wire types.
- **Anticheat (pre-1.21.2 protocol):** there is no `ServerboundClientTickEndPacket` and no key-state
  `ServerboundPlayerInputPacket`, so each processed move packet ends one client tick; sneak/sprint come from
  `ServerboundPlayerCommandPacket` (`ServerPlayNetworkHandlerStreamMixin` hooks `handlePlayerCommand`); jump is always
  treated as possible; the flag-only Strafe replay is off (`MOVEMENT_KEYS_KNOWN = false`); velocity transactions and timer
  heartbeats use the play-phase `ClientboundPingPacket`/`ServerboundPongPacket` (there is no `ServerCommonPacketListenerImpl`
  before 1.20.2, `ServerCommonNetworkHandlerStreamMixin` hooks `ServerGamePacketListenerImpl#send`/`handlePong`); the
  chunk-stall exemption falls back to "chunk not loaded on the server" + the post-teleport window (no `ChunkDataSender`);
  an idle player answers heartbeats without sending ticks, so `ticksWithheldWhileAnswering` is never reported and withheld
  gaps earn no catch-up credit or window extension (`TimerBalance`); teleports hook `teleport(DDDFFLjava/util/Set;)V`
  and the confirm hooks the `absMoveTo` every accepted confirm performs; a move is "accepted" when vanilla reaches
  `checkMovementStatistics`; the server tick is a fixed 50 ms (no tick-rate manager).
- **Client tick stream (1.1.7 replays and run timing):** 1.21.2+ servers hand every client tick (ClientTickEnd) to
  `ReplayEvents#onClientTick`; here `MovementValidator#onMovePacketProcessed` does it for every move packet (the echo a
  client sends after confirming a teleport excluded). The tick's network arrival time travels with the packet itself
  (`ServerboundMovePlayerPacketMixin` stamps it in the netty-thread pass, `MovePacketArrival`), so a stalled server
  thread or a cancelled server-thread pass can't shift it. A pre-1.21.2 client sends nothing in a tick in which it stood
  still (moved < 2e-4, didn't turn, same ground flag), except its position every 20th tick, so the idle ticks before a
  packet are handed on too (`ClientTick#inferred`, `MovementValidator#idleTicksBefore`): exactly 20 ticks since the last
  position packet when the packet is such a reminder, otherwise (only after a tick that ended at rest on the ground)
  estimated from the arrival gap, never more than the gap or the 20-tick bound. They sit at the previous tick's position,
  view and ground flag, timed one tick apart back from the packet that ends the gap, so replays keep the client's tick
  clock (pre-run frames while standing in the start zone, standing still mid-run) and a run launched from standing still
  starts at the last idle tick, one tick before the first move packet that leaves the ground. Inferred ticks are never
  validated and never counted by the anticheat (`MovementValidator.clientTicks`): the run-tick check that can reject a
  run (more client ticks than the measured real time allows) counts move packets, which can only undercount; the
  tick/time evidence (`FLAG_TICK_TIME_MISMATCH`, the replay header's tick count) counts the stream including inferred
  ticks (`RunClock.streamTicksOfRun`). Replay frames carry only the sneak/sprint input flags (the movement and jump keys
  are unknown), and the server derives the bhop chain without the jump key: a take-off counts, and a second client tick
  on the ground (or an idle tick) ends the chain. Strafe efficiency can't be replayed on the server without the keys, so
  1.20.1 keeps the nonce'd request flow (`RunStats`: 1.1.7 clients answer with their HUD efficiency, 1.1.5/1.1.6 clients
  echo the nonce and record 0).
- **Newer surf fixes in the 1.20.1 tick model:** the friction-ground test (0.20) and the sneak edge back-off carry are
  kept; the back-off fall test uses the 1.20.1 client's form (`Player#maybeBackOffFromEdge`/`isAboveGround`: the whole
  box moved down by the step height, not 1.21.2+'s slab) and its reach reads `maxUpStep()` (no `STEP_HEIGHT` attribute);
  preserve-speed resets use the validator's last client step (one move packet).
- **Game API:** NBT instead of data components, `MobSpawnType` instead of `EntitySpawnReason`, `new ResourceLocation(...)`,
  `MobEffect` instead of `Holder<MobEffect>`, `hurt` (server-only check added) instead of `hurtServer`, the `TeleportTarget`
  helper (`util/TeleportTarget`, `ZoneUtil.teleportTo`) instead of 1.21's teleport transitions, block/item properties
  without registry keys, `ResourceLocation`-based registries lookups, `player.latency`.
- **Rendering:** entity renderers take the entity (the `*RenderState` classes are per-frame holders filled from it),
  models render their whole part tree (`renderToBuffer`), the HUD hotbar is drawn from the widgets/icons atlases,
  `SurfRampRenderer`'s recorded mesh replays through the element-wise 1.20.1 `VertexConsumer` calls,
  `SurfRampEntity` hooks entity removal through its level callback (1.20.1 has no `Entity#onRemoval`).
- **Boost pad:** the item model's parent is `minehop:block/boost_pad`, and `BoostBlock` renders with `RenderShape.MODEL`
  (1.20.1's `BaseEntityBlock` defaults to `INVISIBLE`; 1.20.5+ dropped that, so the 1.21.4 pad is visible).
- **Physics:** unchanged; the movement harness output is byte-identical to the 1.21.4 baseline on every loader.

## Verification (2026-10-04)
- `gradlew build` -> `fabric/build/libs/minehop-fabric-1.20.1-1.1.5.jar`, `forge/build/libs/minehop-forge-1.20.1-1.1.5.jar`.
- Movement harness (copy of the old 1.20.1 harness world, seed `-5500227761434375434`): 1692 lines, byte-identical to the
  1.21.4 baseline on the Fabric dev server, a production Fabric server (fabric-server-launch + Fabric API + cloth-config +
  the jar), the Forge dev server, a production Forge 47.4.26 server (reobfuscated jar) and a production NeoForge 47.1.106
  server (the same Forge jar).
- Joins (dev clients, quickPlay, no keyboard input): Fabric -> Fabric, Forge -> Fabric (registry sync identical), Forge
  -> Forge, Fabric -> Forge, Fabric -> NeoForge, Forge -> NeoForge: `[AC] <player> status none -> checked`, no flags, no
  kick, map browser opened through RCON (`execute as <player> run map`), Minehop HUD drawn on Forge.
- Map run on the Fabric server (map + zones created through RCON, a boost pad launches the player from the start into the
  end zone): Fabric client and Forge client both completed it, records saved in `MineHop_Data/minehop_records.json`.
