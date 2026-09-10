package site.siredvin.corruptedstoneblock;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

@Mod(CorruptedStoneblock.ID)
public final class CorruptedStoneblock {
    public static final String ID = "corruptedstoneblock";
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, ID);
    static { GENERATORS.register("stone_rings", () -> StoneRingGenerator.CODEC); }
    public CorruptedStoneblock() {
        GENERATORS.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
