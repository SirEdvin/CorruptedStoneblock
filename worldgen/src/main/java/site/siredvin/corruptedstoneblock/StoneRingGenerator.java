package site.siredvin.corruptedstoneblock;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

/** No post-generation replacement, global mutable settings, or FTB mixins. */
public final class StoneRingGenerator extends ChunkGenerator {
    public record Ring(int width, BlockState block) {
        public static final Codec<Ring> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 30000000).fieldOf("width").forGetter(Ring::width),
                BlockState.CODEC.fieldOf("block").forGetter(Ring::block)
        ).apply(i, Ring::new));
    }
    public static final Codec<StoneRingGenerator> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource),
            Codec.intRange(-30000000, 30000000).fieldOf("center_x").forGetter(g -> g.centerX),
            Codec.intRange(-30000000, 30000000).fieldOf("center_z").forGetter(g -> g.centerZ),
            Codec.intRange(-2032, 2016).fieldOf("min_y").forGetter(g -> g.minY),
            Codec.intRange(16, 4064).fieldOf("height").forGetter(g -> g.height),
            Ring.CODEC.listOf().fieldOf("rings").forGetter(g -> g.rings),
            BlockState.CODEC.fieldOf("outer_block").forGetter(g -> g.outerBlock)
    ).apply(i, StoneRingGenerator::new));

    private final int centerX, centerZ, minY, height;
    private final List<Ring> rings;
    private final int[] widths;
    private final BlockState outerBlock;

    public StoneRingGenerator(BiomeSource biomes, int centerX, int centerZ, int minY, int height,
                              List<Ring> rings, BlockState outerBlock) {
        super(biomes);
        if (minY % 16 != 0 || height % 16 != 0 || minY + height > 2032)
            throw new IllegalArgumentException("Invalid aligned world height");
        long total = rings.stream().mapToLong(Ring::width).sum();
        if (total > 30000000) throw new IllegalArgumentException("Ring radii exceed world coordinate limit");
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.minY = minY;
        this.height = height;
        this.rings = List.copyOf(rings);
        this.widths = rings.stream().mapToInt(Ring::width).toArray();
        this.outerBlock = outerBlock;
    }

    public BlockState columnMaterial(int x, int z) {
        int region = RingLayout.region(x, z, centerX, centerZ, widths);
        return region == rings.size() ? outerBlock : rings.get(region).block();
    }

    public BlockState stateAt(int x, int y, int z) {
        if (y < minY || y >= minY + height) return Blocks.AIR.defaultBlockState();
        if (y == minY || y == minY + height - 1) return Blocks.BEDROCK.defaultBlockState();
        return columnMaterial(x, z);
    }

    @Override protected Codec<? extends ChunkGenerator> codec() { return CODEC; }
    @Override public int getMinY() { return minY; }
    @Override public int getGenDepth() { return height; }
    @Override public int getSeaLevel() { return minY - 1; }
    @Override public int getSpawnHeight(LevelHeightAccessor level) { return 64; }

    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender,
            RandomState random, StructureManager structures, ChunkAccess chunk) {
        // Each generation task exclusively owns this protochunk. Avoid scheduling writes after completion.
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int low = Math.max(minY, chunk.getMinBuildHeight());
        int high = Math.min(minY + height, chunk.getMaxBuildHeight());
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                BlockState material = columnMaterial(chunk.getPos().getMinBlockX() + x,
                        chunk.getPos().getMinBlockZ() + z);
                for (int y = low; y < high; y++) {
                    BlockState block = (y == minY || y == minY + height - 1)
                            ? Blocks.BEDROCK.defaultBlockState() : material;
                    chunk.setBlockState(pos.set(x, y, z), block, false);
                }
            }
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    // Empty structure state plus no decoration/carving: includes mod-added biome features/ores.
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup,
            RandomState random, long seed) {
        return ChunkGeneratorStructureState.createForFlat(random, seed, biomeSource, Stream.empty());
    }
    @Override public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structures) {}
    @Override public void applyCarvers(WorldGenRegion level, long seed, RandomState random,
            BiomeManager biomes, StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}
    @Override public void buildSurface(WorldGenRegion level, StructureManager structures, RandomState random, ChunkAccess chunk) {}
    @Override public void spawnOriginalMobs(WorldGenRegion level) {}

    @Override public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        return Math.min(minY + height, level.getMaxBuildHeight());
    }
    @Override public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int low = Math.max(minY, level.getMinBuildHeight());
        int high = Math.min(minY + height, level.getMaxBuildHeight());
        BlockState[] states = new BlockState[Math.max(0, high - low)];
        for (int i = 0; i < states.length; i++) states[i] = stateAt(x, low + i, z);
        return new NoiseColumn(low, states);
    }
    @Override public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Stone rings: center=" + centerX + "," + centerZ + " material=" + columnMaterial(pos.getX(), pos.getZ()));
    }
}
