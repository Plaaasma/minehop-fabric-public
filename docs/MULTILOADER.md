# Minehop multiloader layout (Fabric, NeoForge, Forge)

Minehop follows jaredlll08's [MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template/tree/1.21.4)
(Mojang mappings, `buildSrc` conventions `multiloader-common` / `multiloader-loader`).

| Module | Contents | Build |
|---|---|---|
| `common/` | All game logic, data, anticheat, movement/physics, both mixin configs, payload records/codecs, brigadier commands, screens, renderers, models, HUD drawing, assets and data, the **platform API** (`net.nerdorg.minehop.platform`). No loader imports. | ModDevGradle in vanilla mode (NeoForm `1.21.4-20241203.161809`), i.e. compiled against plain Minecraft. Never shipped on its own. |
| `fabric/` | `MinehopFabric` (`main`), `MinehopFabricClient` (`client`), `MinehopDataGenerator` (`fabric-datagen`), `ModMenuIntegration` (`modmenu`), `fabric.mod.json`, Fabric implementations of every service (`net.nerdorg.minehop.fabric.platform`). | Fabric Loom 1.10; compiles `common`'s sources together with its own (template convention) and remaps to intermediary. **This is the production jar.** |
| `neoforge/` | Skeleton: `@Mod` entrypoint calling common, `neoforge.mods.toml`, TODO service stubs. | ModDevGradle, NeoForge 21.4.158. Not in `settings.gradle` yet (phase 3). `:neoforge:compileJava` passed in phase 2 (common + stubs). |
| `forge/` | `MinehopForge` (`@Mod`), `MinehopForgeClient`, every Forge service (`net.nerdorg.minehop.forge.platform`), the Forge-only mixins (`minehop.forge.mixins.json`), the Fabric registry-sync client (`net.nerdorg.minehop.forge.network`), `mods.toml`, Forge AT, `pack.mcmeta`. | ForgeGradle 6 + mixingradle, Forge 1.21.4-54.1.18 (phase 3, implemented). Compiles `common`'s sources together with its own; official names at runtime, no reobf, no refmap. |

Versions live in `gradle.properties` (Fabric API 0.119.2+1.21.4, loader 0.16.13, cloth-config 17.0.144, modmenu 13.0.3,
NeoForge 21.4.158, Forge 54.1.18, JDA 5.0.0-beta.21).

## Building and running (Fabric)

```
gradlew :fabric:build                      -> fabric/build/libs/minehop-fabric-1.21.4-<version>.jar  (production jar)
gradlew :fabric:runServer [-Pmovementtest] [-PsurfHull]   (run dir: run/, as before)
gradlew :fabric:runServer -Ptestserver     (run dir: run-testserver/, as before)
gradlew :fabric:runClient                  (run dir: run/, 384x216 window, as before)
gradlew :fabric:runDatagen
```

The jar name follows the template (`<mod_id>-<loader>-<mc>-<version>.jar`); the single-module build produced
`minehop-1.21.4-<version>.jar`. Deployment scripts that look for the old name need the new one.

## Building and running (Forge)

```
gradlew :forge:build                       -> forge/build/libs/minehop-forge-1.21.4-<version>.jar
gradlew :forge:Server [-Pmovementtest] [-PsurfHull]   (run dir: forge/runs/server, or -PforgeServerRunDir=...)
gradlew :forge:Client [-PquickPlay=host:port] [-PclientUsername=Name]   (run dir: forge/runs/client, 384x216 window)
```

The run tasks keep the template's names (`Client`, `Server`, `Data`), so a root-level `gradlew runServer` / `runClient`
still only starts the Fabric run. Production install: Forge 1.21.4-54.1.18 + `cloth-config-forge-17.0.144` + the Minehop
jar in `mods/` (JDA is not bundled, like on Fabric).

## Rules for common code

1. **No loader API.** Anything loader-specific goes through `Services.*` (both sides) or `ClientServices.*` (physical
   client only; never touch `ClientServices` from code that can run on a dedicated server).
2. **Registration is deferred-friendly.** Create game objects only inside the factory passed to
   `Services.REGISTRY.register(key, factory)` and read them through the returned `RegistryEntry#get()` at runtime (not in
   static initialisers of other registry classes, not in the mod constructor). All registration calls happen during
   `Minehop#onInitialize` (the registry classes are touched there: `ModItems.initialize()`, `ModItems.registerModItems()`,
   `ModBlockEntities.registerBlockEntities()`, the entity attribute registrations). **Registration order = raw id order**
   (see "Registries"), so do not reorder.
3. **Vanilla members that are not public** are opened by `common/src/main/resources/META-INF/accesstransformer.cfg`
   (Mojang names; used by NeoForm/NeoForge). On Fabric the same members are already open through Fabric API's transitive
   access wideners, so that file is excluded from the Fabric jar. Forge needs its own AT (see the Forge checklist).
4. **Client-only classes** (screens, renderers, HUD, `MinehopClient`, `ClientPacketHandler`, client mixins) live in
   common too. Only reference them from client code paths (as before; the old split source sets enforced it, now it is
   a convention).
5. **Mixins** stay in common (`minehop.mixins.json`, `minehop.client.mixins.json`). They reference
   `"refmap": "${mod_id}.refmap.json"` (expanded at build time): Loom generates `minehop.refmap.json` for Fabric;
   NeoForge and Forge run Mojang names and ignore the missing refmap.
6. **Registry classes must be loaded during the init.** On Fabric a registry factory runs inside `register(...)`, so a
   factory that reads another registry class loads that class (and registers its entries) right then. Deferred
   loaders run the factory much later, inside the RegisterEvent of its registry. Today `ModBlocks` is only loaded
   that way (from the `boost_be` block entity factory) on a dedicated server, which would register the boost pad
   block/item after their registries closed. The Forge entrypoint therefore touches `ModBlocks` right after
   `Minehop#onInitialize` (same position as on Fabric, so the raw ids are unchanged); NeoForge needs the same.

## Platform API (`net.nerdorg.minehop.platform`)

`Services` (both sides) and `ClientServices` (client only) load one implementation per interface with
`java.util.ServiceLoader`; each loader lists its classes in `META-INF/services/net.nerdorg.minehop.platform.services.*`.

Contract for every service (the Fabric implementation is the reference, it is what production runs):

- Each `on...`/`register...` method mirrors one Fabric API call/event (named in its javadoc). On Fabric it is applied
  immediately, exactly where the single-module mod called Fabric API, so ordering and timing are unchanged. NeoForge and
  Forge may buffer registrations until their (mod-bus) event fires.
- Listeners of one event run in registration order, on the logical side's main thread, at the point the Fabric event
  fires. Payload handlers run on the main thread (Fabric schedules them with `server.execute` / `client.execute`).
- Receivers: first registration of a payload type wins, later ones are ignored (Fabric's `registerGlobalReceiver`
  semantics). A payload without a receiver is ignored. Minehop registers server receivers lazily on the first play
  connection and client receivers on every connection: loaders that need handlers up front must register a dispatcher
  per payload type and look the handler up when a packet arrives.

### `IPlatformHelper` (`Services.PLATFORM`)
| Method | Fabric |
|---|---|
| `String getPlatformName()` | `"Fabric"` |
| `boolean isModLoaded(String modId)` | `FabricLoader#isModLoaded` |
| `boolean isDevelopmentEnvironment()` | `FabricLoader#isDevelopmentEnvironment` |
| `boolean isPhysicalClient()` | `getEnvironmentType() == CLIENT` |
| `Path getGameDirectory()` / `Path getConfigDirectory()` | `getGameDir()` / `getConfigDir()` |
| `String getEnvironmentName()` (default) | `"development"` / `"production"` |
| `ServerPlayer createFakePlayer(ServerLevel, GameProfile)` | `FakePlayer.get` (movement harness, `/test`) |
| `boolean isFakePlayer(Player)` | `instanceof FakePlayer` (anticheat skips fake players) |

### `IRegistryHelper` (`Services.REGISTRY`)
| Method | Fabric |
|---|---|
| `<R, T extends R> RegistryEntry<T> register(ResourceKey<R> key, Supplier<T> factory)` | `Registry.register(BuiltInRegistries.REGISTRY.getValue(key.registry()), key, factory.get())`, immediately |
| `<T extends BlockEntity> BlockEntityType<T> createBlockEntityType(BlockEntityFactory<T>, Block...)` | `FabricBlockEntityTypeBuilder.create(...).build()` (vanilla constructor is private) |
| `void registerEntityAttributes(Supplier<EntityType<? extends LivingEntity>>, AttributeSupplier.Builder)` | `FabricDefaultAttributeRegistry.register` |
| `CreativeModeTab.Builder creativeModeTabBuilder()` | `FabricItemGroup.builder()` |
| `void modifyCreativeModeTab(ResourceKey<CreativeModeTab>, CreativeTabModifier)` | `ItemGroupEvents.modifyEntriesEvent(tab).register(...)` |

`RegistryEntry<T>` = `Supplier<T>` + `key()` / `id()`. Entity types are built in common with vanilla's
`EntityType.Builder` using exactly the settings `FabricEntityTypeBuilder` produced (it handed the size to `sized()`,
tracking range 5 chunks / update interval 3 by default, the surf ramp 128 chunks / 1 tick), so all loaders share them.

### `INetworkHelper` (`Services.NETWORK`)
| Method | Fabric |
|---|---|
| `registerPayloadS2C(Type<T>, StreamCodec<? super RegistryFriendlyByteBuf, T>)` | `PayloadTypeRegistry.playS2C().register` |
| `registerPayloadC2S(Type<T>, StreamCodec<...>)` | `PayloadTypeRegistry.playC2S().register` |
| `registerServerReceiver(Type<T>, ServerPayloadHandler<T>)` | `ServerPlayNetworking.registerGlobalReceiver`; context = `player()`, `server()` |
| `sendToPlayer(ServerPlayer, CustomPacketPayload)` | `ServerPlayNetworking.send` |
| `onPlayConnectionInit(PlayConnectionListener)` | `ServerPlayConnectionEvents.INIT` (handler, server) |
| `onPlayConnectionJoin(PlayConnectionListener)` | `ServerPlayConnectionEvents.JOIN` (handler, server) |
| `onPlayConnectionDisconnect(PlayConnectionListener)` | `ServerPlayConnectionEvents.DISCONNECT` (handler, server) |

### `IEventHelper` (`Services.EVENTS`)
| Method | Fabric | NeoForge / Forge |
|---|---|---|
| `onServerStarting/Started/Stopping/Stopped(ServerListener)` | `ServerLifecycleEvents.*` | `Server{Starting,Started,Stopping,Stopped}Event` |
| `onServerTickStart/End(ServerListener)` | `ServerTickEvents.START/END_SERVER_TICK` | `ServerTickEvent.Pre/Post` / `TickEvent.ServerTickEvent.Pre/Post` |
| `onServerLevelLoad/Unload(ServerLevelListener)` | `ServerWorldEvents.LOAD/UNLOAD` | `LevelEvent.Load/Unload` (ServerLevel only) |
| `onRegisterCommands(CommandRegistrationListener)` | `CommandRegistrationCallback` | `RegisterCommandsEvent` |
| `onPlayerRespawn(PlayerRespawnListener)` (old, new, alive) | `ServerPlayerEvents.AFTER_RESPAWN` | `PlayerEvent.Clone` + `PlayerEvent.PlayerRespawnEvent` |
| `onEntityLoad(EntityLoadListener)` | `ServerEntityEvents.ENTITY_LOAD` | `EntityJoinLevelEvent` |
| `onAllowDamage(AllowDamageListener)` | `ServerLivingEntityEvents.ALLOW_DAMAGE` | `LivingIncomingDamageEvent` / `LivingAttackEvent` (cancel) |
| `onBeforeBlockBreak(BeforeBlockBreakListener)` | `PlayerBlockBreakEvents.BEFORE` | `BlockEvent.BreakEvent` (cancel) |
| `onUseBlock/onUseItem/onUseEntity(...)` | `UseBlockCallback` / `UseItemCallback` / `UseEntityCallback` | `PlayerInteractEvent.RightClickBlock` / `RightClickItem` / `EntityInteract(Specific)` |
| `onGameMessage(GameMessageListener)` | `ServerMessageEvents.GAME_MESSAGE` | none: loader-side mixin on `PlayerList#broadcastSystemMessage` |

Forge specifics (EventBus 6, `MinecraftForge.EVENT_BUS`): `onServerTickStart/End` = `TickEvent.ServerTickEvent.Pre/Post`
(fired after the pause-when-empty check, Fabric's START fires before it: no difference with `pause-when-empty-seconds=0`,
and no Minehop listener uses the tick start); `onAllowDamage` = `LivingAttackEvent`; `onUseEntity` = `EntityInteractSpecific`
(hit result = local position + entity position, like Fabric) and `EntityInteract`; `onPlayerRespawn` pairs `PlayerEvent.Clone`
with `PlayerRespawnEvent`; `onGameMessage` = `PlayerListMixin` (tail of the 3-argument `broadcastSystemMessage`).
Server play connection: INIT = `PlayerLoggedInEvent` at `HIGHEST`, JOIN = `PlayerLoggedInEvent`, DISCONNECT =
`PlayerLoggedOutEvent`. Client: INIT = `ClientPlayerNetworkEvent.LoggingIn` at `HIGHEST`, JOIN = `LoggingIn`, DISCONNECT =
`LoggingOut`. (Fabric fires JOIN a little earlier inside `placeNewPlayer` / at the end of `handleLogin`; Minehop's listeners
do not depend on that.)

### `IClientHelper` (`ClientServices.CLIENT`)
| Method | Fabric | NeoForge (Forge differences in the checklist) |
|---|---|---|
| `onClientTickStart/End(ClientTickListener)` | `ClientTickEvents.START/END_CLIENT_TICK` | `ClientTickEvent.Pre/Post` |
| `onWorldRenderAfterEntities(WorldRenderListener)` | `WorldRenderEvents.AFTER_ENTITIES` | `RenderLevelStageEvent` `AFTER_ENTITIES` |
| `onWorldRenderEnd(WorldRenderListener)` | `WorldRenderEvents.END` | `RenderLevelStageEvent` `AFTER_LEVEL` |
| `onHudRender(HudRenderListener)` (unused today: the HUD is drawn by `InGameHudMixin`) | `HudRenderCallback` | `RenderGuiEvent.Post` (Forge 54 has no `RenderGuiEvent`: a layer added on top of the root in `AddGuiOverlayLayersEvent`) |
| `registerEntityRenderer(Supplier<EntityType>, EntityRendererProvider)` | `EntityRendererRegistry` | `EntityRenderersEvent.RegisterRenderers` |
| `registerModelLayer(ModelLayerLocation, Supplier<LayerDefinition>)` | `EntityModelLayerRegistry` | `EntityRenderersEvent.RegisterLayerDefinitions` |
| `KeyMapping registerKeyMapping(KeyMapping)` | `KeyBindingHelper.registerKeyBinding` | `RegisterKeyMappingsEvent` |
| `setBlockRenderType(Supplier<Block>, RenderType)` | `BlockRenderLayerMap` | `ItemBlockRenderTypes.setRenderLayer` in `FMLClientSetupEvent#enqueueWork` |

`WorldRenderContext` = `matrixStack()`, `consumers()`, `camera()` (camera-relative drawing).

Forge 54 has no `RenderLevelStageEvent`; `LevelRendererMixin` (forge module) fires AFTER_ENTITIES at the head of
`LevelRenderer#renderBlockEntities` (called right after `popPush("blockentities")`, i.e. Fabric's injection point, with the
main pass's pose stack, buffer source and camera) and END at the return of `renderLevel`. Client tick =
`TickEvent.ClientTickEvent.Pre/Post`; renderers, layers and key mappings are buffered for `EntityRenderersEvent.*` /
`RegisterKeyMappingsEvent`; the render type is set in `FMLClientSetupEvent` (`ItemBlockRenderTypes.setRenderLayer`,
deprecated for removal in Forge but working in 54).

### `IClientNetworkHelper` (`ClientServices.NETWORK`)
| Method | Fabric |
|---|---|
| `registerClientReceiver(Type<T>, ClientPayloadHandler<T>)` | `ClientPlayNetworking.registerGlobalReceiver`; context = `client()`, `player()` |
| `sendToServer(CustomPacketPayload)` | `ClientPlayNetworking.send` |
| `onConnectionInit/Join/Disconnect(ConnectionListener)` (handler, client) | `ClientPlayConnectionEvents.INIT/JOIN/DISCONNECT` |

### Config screen hook
cloth-config AutoConfig stays shared (`config/minehop.json5`, registered in `Minehop#onInitialize`).
`MinehopConfigScreen.create(parent)` (common, client) is the single config-screen factory: Fabric = ModMenu
(`ModMenuIntegration`, fabric only), NeoForge = `IConfigScreenFactory` extension point, Forge =
`ConfigScreenHandler.ConfigScreenFactory` extension point (both already wired in the skeleton entrypoints).

## Networking: wire compatibility (MUST hold on every loader)

The production server is Fabric; Forge/NeoForge clients must be able to join it, and Fabric clients should be able to
join Forge/NeoForge servers. Every Minehop payload is a plain vanilla custom payload packet:

```
ClientboundCustomPayloadPacket / ServerboundCustomPayloadPacket
  ResourceLocation id      = minehop:<name>   (exactly CustomPacketPayload.Type#id())
  bytes                    = exactly what the payload's StreamCodec writes (no length/discriminator/version prefix)
```

Never change an id or a codec, never wrap payloads in a loader channel with a discriminator byte. Size limits are
vanilla's (S2C 1 MiB, C2S 32767 bytes; the big lists are already chunked to 24000 chars).

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
| `minehop:replay_path` | ReplayPathPayload | S2C | - | yes |
| `minehop:replay_v_toggle` | ReplayVTogglePayload | S2C | - | yes |
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
`onConnectionJoin`).

### NeoForge registration
In the mod-bus `RegisterPayloadHandlersEvent`: `PayloadRegistrar r = event.registrar("1").optional();` then, per type
recorded by `registerPayloadS2C/C2S`: `playBidirectional` if it was declared both ways, else `playToClient` /
`playToServer`, with the type's codec and a dispatcher handler (looks up the handler set via
`registerServerReceiver` / `registerClientReceiver`). `optional()` is required: a Fabric server performs no NeoForge
channel negotiation, so NeoForge treats it as an "other" connection and only optional payloads may flow; it learns the
server's channels from the `minecraft:register` packet Fabric sends on join (Minehop's server receivers are registered
in `onPlayConnectionInit`, i.e. before that packet). Keep the default `HandlerThread.MAIN`. Send with
`PacketDistributor.sendToPlayer` / `sendToServer` (verify they do not refuse channels learned ad hoc; fall back to
`connection.send(payload)`).

### Forge registration (implemented)
`ForgeNetworkHelper` records every declared payload and, right after the common init, builds **one
`ChannelBuilder.named(minehop:network).optional().payloadChannel().play()`** channel: `clientbound()` / `serverbound()` /
`bidirectional()` (declared both ways) `.add(type, codec, handler)` per payload. A `PayloadChannel` registers each payload
under its **own** id (`minecraft:register` advertises them, like Fabric) and reads/writes exactly the codec's bytes, no
discriminator (`SimpleChannel` would add one). `optional()` accepts vanilla/Fabric peers.

- Receiving: Forge decodes the bytes with the declared codec and calls the handler on the netty thread; the handler marks
  the packet handled and `enqueueWork`s the dispatch (same main-thread queue as vanilla packets, like Fabric's
  `server.execute`/`client.execute`). The receiver is looked up on the main thread (first registration wins, none =
  ignored). A payload arriving in the wrong direction is rejected by Forge's channel validation (Fabric would drop it).
- Sending: **like Fabric**, `player.connection.send(new ClientboundCustomPayloadPacket(payload))` /
  `getConnection().send(new ServerboundCustomPayloadPacket(payload))` with the payload object itself, not a
  `ForgePayload`, because Minehop's anticheat inspects outgoing packets (`ResetVelocityCarryPayload` in
  `MovementValidator#selfVelocity`, through `ServerCommonNetworkHandlerStreamMixin` on `send`). Vanilla's payload codec falls
  back to `ForgeHooks.getCustomPayloadCodec` for these ids, which only knows how to encode `ForgePayload`;
  `ForgeHooksMixin` (forge module) wraps the returned codec for Minehop ids so it also encodes the typed payload with
  its declared codec. Same bytes either way; in-memory (singleplayer) connections are serialized too, so the receiving
  side always gets bytes.

### Fabric registry sync (blocks Forge/NeoForge clients on the Fabric server until handled)
Minehop adds entries to synced registries, so the Fabric server's registry sync
(`RegistrySyncManager.configureClient`, configuration phase) **disconnects every client that cannot receive
`fabric:registry/sync/direct`** ("This server requires Fabric Loader and Fabric API installed on your client!").
Wire compatibility of Minehop's own payloads is not enough; the NeoForge and Forge clients must speak this handshake:

1. Register a configuration-phase S2C payload `fabric:registry/sync/direct` (NeoForge:
   `registrar.optional().configurationToClient`, so it is listed in the client's `minecraft:register` and the server's
   `canSend` passes; Forge: an optional configuration-phase channel) and a C2S `fabric:registry/sync/complete`.
2. Payload format (`DirectRegistryPacketHandler`, fabric-registry-sync-v0 6.1.11): each packet is raw bytes (no
   length prefix); concatenate them until an **empty** packet arrives. The combined buffer is: VarInt registry-namespace
   group count; per group: String namespace (`""` = `minecraft`), VarInt registry count; per registry: String path,
   byte attribute flags, VarInt id-namespace group count; per group: String namespace (`""` = `minecraft`), VarInt bulk
   count; per bulk: VarInt raw-id start delta (first raw id = previous bulk's last raw id + delta), VarInt size, then
   `size` Strings (paths), raw ids consecutive.
3. Compare every received (id -> raw id) with the client's own registries. Reply `fabric:registry/sync/complete` (empty
   payload) when they all match; otherwise disconnect with a clear message (or remap, which is a much bigger job).
   Fabric fills each slice from its buffer's whole backing array, so ignore trailing bytes after the structure.
4. With vanilla + Minehop only, the raw ids match when Minehop registers in the same order on every loader. The phase 2
   dump of the Fabric server (identical before and after the migration) is: entity types
   `gamemode_entity`=149, `reset_entity`=150, `start_entity`=151, `end_entity`=152, `replay_entity`=153,
   `surf_ramp_entity`=154; items `bounds_stick`=1385, `surf_stick`=1386, `instagib_gun`=1387, `boost_pad`=1388; block
   `boost_pad`=1095; block entity type `boost_be`=45; creative tab `minehop:minehop`=14. NeoForge/Forge must produce the
   same numbers (check with their registry dumps).

Forge (implemented, `FabricRegistrySyncClient`, physical client only): an optional configuration-phase `PayloadChannel`
(`minehop:fabric_registry_sync`) with `fabric:registry/sync/direct` (clientbound) and `fabric:registry/sync/complete`
(serverbound); Forge's `ChannelListManager` answers the Fabric server's configuration `minecraft:register` with them, so
`canSend` passes. Slices are collected per connection (channel attribute), compared on the client thread, then
`complete` is sent or the client disconnects with the list of differences in the log. Verified against the Fabric
reference server: 4 registries (block, block_entity_type, entity_type, item) / 2686 entries all equal, Minehop's raw ids
exactly the numbers below. Forge needs nothing else to join a non-Forge server: the connection is treated as VANILLA
(Fabric ignores the `\0FORGE` host-name marker), and every Minehop channel is optional.

The reverse direction (Fabric client on a NeoForge/Forge server): the NeoForge server treats a Fabric client as an
"other" connection; it is accepted when all Minehop payloads are `optional()`, but NeoForge does not sync registries
to it, so the raw ids above must match there as well. Test it explicitly.

## Registries
Fabric registers eagerly in the order the common code calls `Services.REGISTRY.register`. Phase 2 verified with
`-Dfabric.registry.debug.writeContentsAsCsv=true` that all 80 synced registries (id, raw id) are identical between the
single-module build (`cb4118e`) and the multiloader Fabric build.

## Packaging of dependencies
| Dependency | Fabric (unchanged) | NeoForge | Forge |
|---|---|---|---|
| cloth-config | `modApi cloth-config-fabric` (not bundled; required in `fabric.mod.json`) | `implementation cloth-config-neoforge`, `cloth_config` dependency in `neoforge.mods.toml` | `implementation cloth-config-forge`, `cloth_config` dependency in `mods.toml` |
| modmenu | `modImplementation` (optional at runtime) | n/a (`IConfigScreenFactory`) | n/a (`ConfigScreenFactory`) |
| JDA | `implementation`: compile + dev runtime only, **not** jar-in-jar'd into the mod jar (only the separate `jarWithType` `-all` jar bundles the runtime classpath) | same: `implementation` + `additionalRuntimeClasspath` for dev runs, not bundled (to bundle: `jarJar`) | same: `minecraftLibrary` for dev, not bundled (to bundle: ForgeGradle `jarJar`) |
| common | compiled into the Fabric jar (template convention) | compiled into the NeoForge jar | compiled into the Forge jar |

## Mixin targets on NeoForge (static audit, phase 2)
All targets exist in NeoForge 21.4.158's patched classes, and every `@ModifyConstant` constant (100.0F, 300.0F, 0.0625D
in `handleMovePlayer`, 100.0D in `handleMoveVehicle`) and `INVOKE` target (`PacketUtils.ensureRunningOnSameThread`,
`ServerPlayer.hasChangedDimension`) is still present. NeoForge patches the bodies of `LivingEntity.travel`,
`jumpFromGround`, `hurtServer`, `knockback`, `randomTeleport`, `onClimbable`, `calculateEntityAnimation`,
`ServerCommonPacketListenerImpl.send(Packet, PacketSendListener)`, `ServerGamePacketListenerImpl.handleMovePlayer`,
`handleMoveVehicle`, `teleport(PositionMoveRotation, Set)`, `Gui.render`, `renderSlot`, `renderExperienceBar`,
`renderItemHotbar`, `KeyMapping.<init>`, `isDown`, `SoundEngine.play`: HEAD/TAIL/RETURN injections still apply, but
run the movement harness and the anticheat on NeoForge. **Known behaviour gap:** NeoForge renders the HUD through
`GuiLayerManager` and never calls `Gui.renderPlayerHealth` (it calls `renderHealthLevel`/`renderArmorLevel`/
`renderFoodLevel` directly), so `InGameHudMixin`'s `renderPlayerHealth` cancel (hide-self) does not hide armor and
food there; `renderHearts` is still cancelled. Fix in the neoforge module (`RenderGuiLayerEvent.Pre` cancel for
`VanillaGuiLayers.ARMOR_LEVEL`/`FOOD_LEVEL` while `ConfigWrapper.config.hideSelf`), not in common.

Forge 54.1.18: every target method exists, the same constants and `INVOKE` targets are present, and `Gui.render` still
calls `renderPlayerHealth` (no HUD gap). Forge's game jar is recompiled from decompiled sources, so a method-body
comparison with vanilla is not meaningful there; verify behaviour at runtime (harness, anticheat, HUD). Verified in
phase 3: harness byte-identical, anticheat checked/no flags, hide-self hides hand, hotbar and status bars.
Note: Forge's `-sources.jar` for 54.1.18 still contains source files of classes that are not in the build (e.g.
`RenderGuiEvent`); check APIs against the compiled jar (`forge/build/fg_cache/.../forge-...-mapped_official_1.21.4.jar`).

## Phase 3 checklist

### NeoForge (`neoforge/`)
1. Add `include('neoforge')` to `settings.gradle`.
2. Entrypoint: keep `MinehopNeoForge` (mod bus stored before common init) and `MinehopNeoForgeClient` (config screen
   extension point; common client init from the mod constructor because `RegisterKeyMappingsEvent` and
   `EntityRenderersEvent.*` fire before `FMLClientSetupEvent`).
3. Implement every TODO in `net.nerdorg.minehop.neoforge.platform`:
   - `NeoForgePlatformHelper`: ModList/FMLLoader/FMLEnvironment/FMLPaths; `FakePlayerFactory.get`; `instanceof FakePlayer`.
   - `NeoForgeRegistryHelper`: DeferredRegister per registry (registered on the mod bus); BlockEntityType constructor;
     `EntityAttributeCreationEvent`; `CreativeModeTab.builder()`; `BuildCreativeModeTabContentsEvent`.
   - `NeoForgeNetworkHelper`: `RegisterPayloadHandlersEvent` + `optional()` registrar + dispatcher maps; send via
     `PacketDistributor`; INIT/JOIN/DISCONNECT from `PlayerLoggedIn/OutEvent`.
   - `NeoForgeEventHelper`: the event mappings in the table above (Clone+Respawn for `onPlayerRespawn`; a neoforge-only
     mixin for `onGameMessage`).
   - `NeoForgeClientHelper`: `ClientTickEvent`, `RenderLevelStageEvent` (AFTER_ENTITIES / AFTER_LEVEL),
     `RenderGuiEvent.Post`, buffered renderer/layer/key-mapping registration, render type in `FMLClientSetupEvent`.
   - `NeoForgeClientNetworkHelper`: dispatcher map, `PacketDistributor.sendToServer`, `ClientPlayerNetworkEvent`.
4. Fabric registry sync handshake client side (section above) so NeoForge clients can join the Fabric server.
5. HUD hide-self for armor/food via `RenderGuiLayerEvent.Pre` (mixin audit above).
6. Common has no `pack.mcmeta` (the template puts one there, but it would change the Fabric jar's resource pack
   metadata). If NeoForge/Forge want one, add it to the loader module's own resources.
7. Verify: movement harness byte-identical to the Fabric baseline; NeoForge client joins the Fabric server
   (handshake, run timer, finish, replay); Fabric client joins the NeoForge server; registry raw ids match.

### Forge (`forge/`) - done in phase 3 (see "Phase 3 results (Forge)" below)
1. Add `include('forge')` to `settings.gradle` (ForgeGradle 6 + mixingradle, as in the template).
2. Entrypoint: `MinehopForge(FMLJavaModLoadingContext)` stores the mod bus before the common init;
   `MinehopForgeClient` registers the config screen and runs the common client init from the mod constructor.
3. `forge/src/main/resources/META-INF/accesstransformer.cfg` (done in phase 2): Forge's own AT already opens
   `CreativeModeTabs.COMBAT/OP_BLOCKS`, the `BlockEntityType` constructor and the 7-argument `RenderType.create`; the
   file adds the 5-argument `RenderType.create` (`m_173209_`) used by `ModRenderLayer` (Forge ATs use SRG names at
   compile time). Check at runtime that it is applied in production too.
4. Implement every TODO in `net.nerdorg.minehop.forge.platform` (Forge 54 = EventBus 6, `MinecraftForge.EVENT_BUS`):
   - `ForgePlatformHelper`: Forge 54 has **no FakePlayer**: add a forge-module `MinehopFakePlayer extends ServerPlayer`
     modelled on Fabric's `FakePlayer` (dummy connection, cached per level+profile).
   - `ForgeRegistryHelper`: DeferredRegister/RegistryObject, `EntityAttributeCreationEvent`,
     `CreativeModeTab.builder()`, `BuildCreativeModeTabContentsEvent`.
   - `ForgeNetworkHelper`: one optional `PayloadChannel` (see "Forge registration"), `enqueueWork` dispatch.
   - `ForgeEventHelper`: `TickEvent.ServerTickEvent.Pre/Post`, `LevelEvent`, `RegisterCommandsEvent`,
     `PlayerEvent.Clone/PlayerRespawnEvent`, `EntityJoinLevelEvent`, `LivingAttackEvent`, `BlockEvent.BreakEvent`,
     `PlayerInteractEvent.*`, forge-only mixin for `onGameMessage`.
   - `ForgeClientHelper`: `TickEvent.ClientTickEvent`; Forge 54 has **no RenderLevelStageEvent** (only
     `AddFramePassEvent`): add a forge-only `LevelRenderer` mixin at Fabric API's AFTER_ENTITIES and END points;
     `RenderGuiEvent.Post`; buffered `EntityRenderersEvent`/`RegisterKeyMappingsEvent`; render type in
     `FMLClientSetupEvent`.
   - `ForgeClientNetworkHelper`: dispatcher map, `PacketDistributor.SERVER`, `ClientPlayerNetworkEvent`.
5. Fabric registry sync handshake client side (section above).
6. `pack.mcmeta` in the forge module's resources if Forge needs one (not in common, see NeoForge item 6).
7. Runtime verification: harness, anticheat, HUD, cross-loader joins, raw ids (the static mixin audit passed).

## Verification used in phase 2 (Fabric)
- Bytecode: the remapped jar compared with the `cb4118e` jar class by class (`javap -c -p -constants`, constant-pool
  indexes and offsets normalised): only platform indirection differs.
- Movement harness (`-Pmovementtest`, fresh copy of the cleaned 1.21.4 world): byte-identical to the 1.21.4 baseline,
  both in the dev run and with the remapped jar on a production Fabric server (fabric-server-launch, fabric-api,
  cloth-config), which also exercises the refmap.
- Dedicated server + real client, map/zone creation, a run (record, PB, replay written), screens, commands.
- Registry raw ids and datagen output identical to `cb4118e`.

## Phase 3 results (Forge)
- `gradlew build` builds `forge/build/libs/minehop-forge-1.21.4-<version>.jar` (cloth-config is a `mods.toml` dependency,
  JDA not bundled; the Discord bot only touches JDA when a bot token is configured, exactly like on Fabric).
- Movement harness (`:forge:Server -Pmovementtest`, fresh copy of the cleaned 1.21.4 world, `pause-when-empty-seconds=0`):
  byte-identical to the 1.21.4 baseline (1692 lines), in the dev run and on a production Forge 54.1.18 install with the
  built jar.
- Forge client on the Forge server, Forge client on the Fabric server (registry sync, about 3.5 min connected) and Fabric
  client on the Forge server: anticheat `none -> checked`, no flags, HUD, commands, screens (map browser, map creator,
  anticheat console incl. C2S actions, HUD editor, cloth-config screen from the Forge mods list), map runs with records
  and replays saved on both server types.
