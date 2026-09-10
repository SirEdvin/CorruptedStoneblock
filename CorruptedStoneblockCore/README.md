# CorruptedStoneblockCore

GTCEu addon and pack core for Corrupted Stoneblock, Minecraft 1.20.1 / Forge 47.4.10.
World generation is its first module.

## Template provenance

Based on [GregTechCEu/1.20.1-Addon-Template](https://github.com/GregTechCEu/1.20.1-Addon-Template),
pinned revision `19cc9032b50b378033f95e1e8e0de1ab4e3bf062`.
Original template by screret, maintained by JuiceyBeans. The upstream LGPL-3.0
license is retained in `LICENSE.MD` and included in the JAR. The original ring-generator
implementation retains its MIT notice in `src/main/resources/META-INF/LICENSE`.

Retained template foundations:
- ModDevGradle Legacy Forge `2.0.91`, Gradle `8.10`, Java 17, Parchment `2023.09.03`.
- Property-driven mod identity/dependencies and client/server/data development runs.
- `@Mod` entrypoint with `GTRegistrate`, plus a discoverable `@GTAddon` / `IGTAddon`.
- Spotless `7.0.2` with the template's Eclipse formatting and import-order files.

Pack-specific adaptations:
- Name/artifact `CorruptedStoneblockCore`; mod ID `corruptedstoneblockcore`.
- Exact installed GTCEu dependency `8.0.0-20260826.220408-269`, not the template's
  older GT7 line or a floating snapshot. Runtime metadata accepts the compatible
  GT8 snapshot line. No GTCEu version change in the modpack.
- GT8 uses `registerEventListeners` rather than the template's old `registerRegistrate`.
  Removed obsolete GT7 material-manager callbacks and unused example/mixin stubs.
- Use the full GT8 artifact with its embedded runtime libraries; do not add the
  template's obsolete LDLib/configuration versions. Registrate is a compile-only
  remapped dependency because GT8 provides it at runtime.
- No GitHub workflows copied or retained. **All validation runs locally only.**

## Build locally

```sh
./gradlew spotlessApply
./gradlew spotlessCheck test build installPack
cd ..
packwiz refresh
python3 scripts/check-worldgen.py
```

`installPack` copies the **reobfuscated** production artifact
`mods/CorruptedStoneblockCore-0.1.0.jar`; it never installs `build/devlibs` or the
test mod. Commit the built JAR together with source and refreshed Packwiz hashes.
Keep `gradle.properties` and the test descriptor's core dependency compatible when
bumping versions. There are no remote validation or publishing workflows.

## Saved-world compatibility

The generator codec remains registered as `corruptedstoneblock:stone_rings`, and
all datapack IDs remain unchanged. Its complete settings round-trip through the
codec used by FTB Team Bases, so existing team dimensions can still be loaded after
the owning mod is renamed. New team dimensions copy the static template settings;
existing ones retain their serialized settings.

## Local runtime harness (not shipped)

`./gradlew reobfValidationJar` builds `CorruptedStoneblockCore-0.1.0-validation.jar`
as a separate test-only mod. It verifies GT addon discovery, team creation, ring
geometry, ore/structure exclusion, and restart persistence with the full pack loaded.
It is never installed by `installPack` or indexed by Packwiz.
See `../docs/world-generation.md` for the local dedicated-server procedure.
