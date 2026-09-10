package site.siredvin.corruptedstoneblockcore;

import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import org.slf4j.Logger;

/** GTCEu addon entrypoint; world generation is the core's first module. */
@Mod(CorruptedStoneblockCore.MOD_ID)
public final class CorruptedStoneblockCore {

    public static final String MOD_ID = "corruptedstoneblockcore";
    // Preserve existing saved dimension codecs while renaming the owning mod.
    public static final String WORLDGEN_NAMESPACE = "corruptedstoneblock";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final GTRegistrate REGISTRATE = GTRegistrate.create(MOD_ID);
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS = DeferredRegister
            .create(Registries.CHUNK_GENERATOR, WORLDGEN_NAMESPACE);
    static {
        GENERATORS.register("stone_rings", () -> StoneRingGenerator.CODEC);
    }

    public CorruptedStoneblockCore() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        GENERATORS.register(bus);
        REGISTRATE.registerEventListeners(bus);
    }
}
