package site.siredvin.corruptedstoneblockcore;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

/** Official GTCEu addon discovery hook; future core content can use IGTAddon hooks. */
@GTAddon
public final class CorruptedStoneblockGTAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return CorruptedStoneblockCore.REGISTRATE;
    }

    @Override
    public void initializeAddon() {
        CorruptedStoneblockCore.LOGGER.info("CorruptedStoneblockCore GTCEu addon initialized");
    }

    @Override
    public String addonModId() {
        return CorruptedStoneblockCore.MOD_ID;
    }
}
