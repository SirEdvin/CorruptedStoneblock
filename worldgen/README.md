# Corrupted Stoneblock Worldgen

Original, pack-owned Forge 1.20.1 generator. Java sources are MIT-licensed;
see `src/main/resources/META-INF/LICENSE`. The rest of the pack retains its
existing license. No code or assets from the unofficial StoneBlock port are bundled.
The Gradle wrapper comes from Forge's 1.20.1-47.4.10 MDK; Gradle is Apache-2.0 licensed.

## Build

Requires Java 17 (toolchain) and network access for the first Gradle build.

```sh
./gradlew test build installPack
cd ..
packwiz refresh
python3 scripts/check-worldgen.py
```

The production JAR is installed into `mods/` and committed with its source so
Packwiz clients do not need a compiler or an unpublished Maven artifact.
Keep the JAR version, Gradle version, and `mods.toml` version together when releasing.
After changing Java, always rebuild the checked-in JAR and refresh Packwiz.

The generator's complete settings are serialized by its codec. FTB Team Bases
copies the codec from `corruptedstoneblock:ring_template` when creating private
levels. Existing dimensions retain their serialized settings across restarts;
changing the template only changes newly created team dimensions.

## Runtime regression harness (not shipped)

`./gradlew reobfValidationJar` builds a separate `*-validation.jar`. It is NEVER
copied by `installPack`, indexed by Packwiz, or included in the production JAR.
See `../docs/world-generation.md` for installation and test instructions.
