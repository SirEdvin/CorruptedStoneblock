package site.siredvin.corruptedstoneblock.validation;

import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import dev.ftb.mods.ftbteambases.data.bases.BaseInstanceManager;
import dev.ftb.mods.ftbteambases.data.construction.BaseConstructionManager;
import dev.ftb.mods.ftbteambases.data.definition.BaseDefinitionManager;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.TeamManagerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import site.siredvin.corruptedstoneblock.StoneRingGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Separate test-only mod; never installed by the pack or main build artifact. */
@Mod("csbvalidation")
public final class ValidationMod {
    private MinecraftServer server;
    private int ticks;
    private int stage;
    private final List<ServerPlayer> players = new ArrayList<>();
    private final Map<String, Object> results = new LinkedHashMap<>();
    private final String mode = System.getProperty("csb.validation", "off");

    public ValidationMod() {
        if (!mode.equals("off")) {
            MinecraftForge.EVENT_BUS.addListener(this::started);
            MinecraftForge.EVENT_BUS.addListener(this::tick);
        }
    }
    private void started(ServerStartedEvent event) { server = event.getServer(); }
    private void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private void tick(TickEvent.ServerTickEvent event) {
        if (server == null || event.phase != TickEvent.Phase.END || stage == 99 || ++ticks < 20) return;
        try {
            if (stage == 0) {
                if (mode.equals("restart")) { verify(true); finish(null); return; }
                var definition = BaseDefinitionManager.getServerInstance().getBaseDefinition(
                        new ResourceLocation("corruptedstoneblock:stone_rings")).orElseThrow();
                require(definition.dimensionSettings().privateDimension(), "Base is not private");
                for (String name : List.of("CSB_Test_A", "CSB_Test_B")) {
                    UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
                    TeamManagerImpl.INSTANCE.playerLoggedIn(null, id, name);
                    var player = FakePlayerFactory.get(server.overworld(), new GameProfile(id, name));
                    players.add(player);
                    BaseConstructionManager.INSTANCE.begin(player, definition);
                }
                stage = 1;
            } else if (stage == 1) {
                require(ticks < 1200, "Team base construction timed out");
                if (players.stream().anyMatch(BaseConstructionManager.INSTANCE::isConstructing)) return;
                verify(false);
                finish(null);
            }
        } catch (Throwable error) { finish(error); }
    }
    private void verify(boolean restart) throws Exception {
        var manager = BaseInstanceManager.get(server);
        List<ServerLevel> levels = new ArrayList<>();
        List<String> dimensionIds = new ArrayList<>();
        for (String name : List.of("CSB_Test_A", "CSB_Test_B")) {
            UUID id = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
            var team = FTBTeamsAPI.api().getManager().getTeamForPlayerID(id).orElseThrow();
            require(team.isPartyTeam(), "Party was not created for " + name);
            var base = manager.getBaseForTeam(team).orElseThrow();
            require(base.spawnPos().equals(new BlockPos(255, 64, 255)), "Wrong spawn: " + base.spawnPos());
            ServerLevel level = Objects.requireNonNull(server.getLevel(base.dimension()), "Missing saved dimension");
            require(level.getChunkSource().getGenerator() instanceof StoneRingGenerator, "Wrong generator after dimension copy/restart");
            require(level.getBlockState(base.spawnPos()).isAir() && level.getBlockState(base.spawnPos().above()).isAir(), "Unsafe spawn");
            require(level.getBlockState(base.spawnPos().below()).is(Blocks.COBBLESTONE), "Missing chamber floor");
            levels.add(level);
            dimensionIds.add(base.dimension().location().toString());
        }
        require(!levels.get(0).dimension().equals(levels.get(1).dimension()), "Teams share a dimension");
        results.put("dimensions", dimensionIds);
        results.put("two_party_teams", true);
        results.put("safe_spawns", true);
        BlockPos marker = new BlockPos(260, 64, 255);
        if (!restart) levels.get(0).setBlockAndUpdate(marker, Blocks.GOLD_BLOCK.defaultBlockState());
        require(levels.get(0).getBlockState(marker).is(Blocks.GOLD_BLOCK), "Team A marker missing");
        require(levels.get(1).getBlockState(marker).is(Blocks.COBBLESTONE), "Team B was changed by Team A");
        results.put("isolation_and_persistence", true);

        // On restart, sample the OTHER team's previously unexplored terrain too.
        ServerLevel level = levels.get(restart ? 1 : 0);
        StoneRingGenerator generator = (StoneRingGenerator) level.getChunkSource().getGenerator();
        var ops = RegistryOps.create(NbtOps.INSTANCE, server.registryAccess());
        var encoded = ChunkGenerator.CODEC.encodeStart(ops, generator).result().orElseThrow();
        var decoded = (StoneRingGenerator) ChunkGenerator.CODEC.parse(ops, encoded).result().orElseThrow();
        int[] distances = {0, 511, 512, 1023, 1024, 2047, 2048, 3071, 3072, 6144, 65536, 1000000, 29000000};
        Block[] expected = {Blocks.COBBLESTONE, Blocks.COBBLESTONE, Blocks.STONE, Blocks.STONE,
                Blocks.DEEPSLATE, Blocks.DEEPSLATE, Blocks.OBSIDIAN, Blocks.OBSIDIAN,
                Blocks.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN,
                Blocks.CRYING_OBSIDIAN, Blocks.CRYING_OBSIDIAN};
        int checked = 0;
        for (int i = 0; i < distances.length; i++) {
            for (int[] dir : new int[][]{{1,0},{-1,0},{0,1},{0,-1}}) {
                int x = 255 + distances[i] * dir[0], z = 255 + distances[i] * dir[1];
                // Check real generated terrain below the manually placed starting chamber.
                require(level.getBlockState(new BlockPos(x, 20, z)).is(expected[i]), "Wrong ring at " + x + "," + z);
                require(decoded.columnMaterial(x,z).is(expected[i]), "Codec changed ring settings");
                checked++;
            }
        }
        require(level.getBlockState(new BlockPos(65535,20,65535)).is(Blocks.CRYING_OBSIDIAN), "Diagonal overflow");
        results.put("real_boundary_samples", checked + 1);
        results.put("codec_round_trip", true);
        int scanned = 0;
        for (int distance : new int[]{128, 768, 1536, 2560, 4096}) {
            var chunk = level.getChunk((255 + distance) >> 4, 255 >> 4);
            require(chunk.getAllStarts().isEmpty(), "Natural structure start found");
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int ax = chunk.getPos().getMinBlockX() + x, az = chunk.getPos().getMinBlockZ() + z;
                for (int y = -64; y < 320; y++) {
                    BlockPos pos = new BlockPos(ax, y, az);
                    require(chunk.getBlockState(pos).equals(generator.stateAt(ax,y,az)), "Unexpected decoration/ore/cave at " + pos);
                    scanned++;
                }
            }
        }
        results.put("terrain_blocks_scanned", scanned);
        var lobby = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation("ftbteambases:lobby")));
        require(lobby != null, "Lobby dimension missing");
        int portals = 0;
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) for (int y = 63; y <= 70; y++) {
            var block = lobby.getBlockState(new BlockPos(x,y,z));
            if (net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString().equals("ftbteambases:portal")) portals++;
        }
        require(portals > 0, "No base-creation portal in lobby");
        results.put("lobby_portal_blocks", portals);
        results.put("restart", restart);
    }
    private void finish(Throwable error) {
        stage = 99;
        results.put("passed", error == null);
        if (error != null) {
            results.put("error", error.toString());
            LogUtils.getLogger().error("CSB_VALIDATION_FAILED", error);
        } else LogUtils.getLogger().info("CSB_VALIDATION_PASSED {}", results);
        try {
            Files.writeString(Path.of("validation-" + mode + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(results));
        } catch (Exception e) { LogUtils.getLogger().error("Cannot write validation report", e); }
        server.halt(false);
    }
}
