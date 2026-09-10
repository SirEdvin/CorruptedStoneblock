# Private team worlds: initial stone-ring generation

## Implemented layout

Every FTB party team receives a separate dynamic dimension. There are no shared-world
islands and no radial repetition. Rings use **horizontal Euclidean distance**, not
Manhattan distance, squares, or spherical shells. Widths are radial, not diameters.

| Material | Width | Distance from the center |
|---|---:|---|
| Cobblestone (placeholder) | 512 | `0 <= r < 512` |
| Stone | 512 | `512 <= r < 1024` |
| Deepslate | 1024 | `1024 <= r < 2048` |
| Obsidian | 1024 | `2048 <= r < 3072` |
| Crying obsidian | Unbounded | `r >= 3072` |

The center is **X=255, Z=255** in every team dimension. This is FTB Team Bases'
normal center for a one-region private base; choosing it avoids a mixin just to
move structure placement to the origin. The player starts at **255, 64, 255**.
The generator's center is serialized in the template JSON. Moving it also requires
coordinating the base's structure placement and absolute spawn, not just changing
one JSON value.

Terrain fills Y=-64 through Y=319. Y=-64 and Y=319 are bedrock caps. Between the
caps each column is entirely its ring material except for the deliberately placed
starting chamber. There is no sky access, weather, cave carving, aquifer fill,
blending, random ore generation, biome decoration, or natural structure generation.
The custom barren biome also has no configured mob spawn lists at this stage.

The outer ring never loops back. Minecraft's normal coordinate/world-border limits
still apply; no additional finite map edge or outer-ring boundary is imposed.
Distance calculations use `long` intermediates to avoid the common far-coordinate
integer-overflow bug.

## Components and data ownership

- **FTB Team Bases 20.1.3**: party/base creation, private dimensions, homes, lobby,
  and FTB membership lifecycle. Existing FTB Teams/Library/Chunks dependencies are retained.
- **CorruptedStoneblockCore 0.0.1**: a GTCEu addon based on the official 1.20.1 addon template, with the generator registered
  as `corruptedstoneblock:stone_rings`. No FTB mixins and no unofficial StoneBlock fork.
- **Skyblock Builder 1.20.1-5.1.33**: retained from the requested mod addition, but it
  does not own this pack's bases. Do not select its separate Skyblock world type
  when testing this FTB workflow. The normal/default world type is supported.
- `kubejs/data/corruptedstoneblock/dimension/ring_template.json`: reusable generator
  settings, widths, center, height, and block states. FTB copies this generator into
  each private dimension using `copy_generator_from_dimension`.
- `dimension_type/underground.json`: build height, sky, beds, weather-related properties.
- `worldgen/biome/barren.json`: explicitly empty features, carvers, and spawn lists.
- `ftb_base_definitions/stone_rings.json`: the selectable private base and safe spawn.
- `structures/starting_chamber.nbt`: authored cobblestone room with air and lighting.
- `structures/lobby.nbt`: authored room with spawn marker and FTB base-creation portal.
- `defaultconfigs/ftbteambases/ftbteambases-server.snbt`: FTB lobby/generator settings.

The room/lobby are intentionally placed templates, the only exceptions to “no
structures.” The generator excludes **all natural structure sets**, skips biome
feature decoration entirely (including GTCEu/mod-added ores), and skips carvers.
This remains true even with `generate-structures=true` on the dedicated server.

New players enter the FTB lobby in adventure mode. Walking into its portal opens
FTB's base-selection interface. Select the Corrupted Stoneblock base; FTB creates
both a party and its private world. Joining that party uses the same base.
The standard `/ftbteambases home` and `/ftbteambases lobby` commands remain available.
The test covers FTB's server-side creation path; the graphical selection interface
still requires human client smoke testing.

This change configures the team worlds, not the entire vanilla dimension registry.
Vanilla Overworld/Nether/End and the inherited JAvD mod still exist. Normal Nether
portal creation in team dimensions is disabled for now to avoid escaping into
unconfigured terrain; other mods' dimension-travel mechanics have not been
progression-audited. No recipes, quests, ore/resource bootstrap, or mining-tier
progression is implemented here.

## Updating or extending generation

This is intended for **new worlds/new bases**. A new team's copied generator settings
are saved in the world's dimension registry. Editing the static template changes
future team dimensions, not the generator settings serialized for existing teams.
Existing chunks are never rewritten. Retrofitting existing team dimensions requires
an explicit migration plan and backup; do not simply delete the level data.

Adding later ores/structures requires an intentional generator/biome change. Adding
an ore to a vanilla biome or enabling server structures alone will not bypass the
current no-decoration/no-structure generator. Keep ring-material changes in the JSON;
no Java rebuild is needed to replace the placeholder cobblestone for future bases.

## Core identity and template basis

The source project is maintained separately at
https://github.com/SirEdvin/CorruptedStoneblockCore. Its display/artifact name is
`CorruptedStoneblockCore`, and the Forge mod ID is `corruptedstoneblockcore`.
Its `@GTAddon` implementation registers with GTCEu and provides the core's
`GTRegistrate`. The official addon template supplies ModDevGradle Legacy Forge,
Parchment, property-expanded metadata, Gradle wrapper, and Spotless configuration.
See the core README for pinned template provenance and GT8 adaptations.

The **generator registry ID remains `corruptedstoneblock:stone_rings`**, intentionally:
saved private dimensions refer to it. Renaming the owning mod does not rename the
codec or datapack IDs. The local regression harness also verifies actual GTCEu
addon discovery; old-core saved-world compatibility is checked separately.

## Core release and cheap pack checks

Core [release 0.0.1](https://github.com/SirEdvin/CorruptedStoneblockCore/releases/tag/0.0.1)
is installed through `mods/corrupted-stoneblock-core.pw.toml`, using a fixed release
asset URL and SHA-256. Neither the core source nor its production JAR is tracked
in this pack's current tree. All Java sources, unit tests, the test-only mod, and
its runner live in the separate core repository. Pack-owned JSON, templates,
structure-generation script, and static pack checks stay here.

```sh
# In the CorruptedStoneblock pack repository:
python3 scripts/generate-starting-structures.py
packwiz refresh
python3 scripts/check-worldgen.py                   # offline pack/metadata checks
python3 scripts/check-worldgen.py --download-core   # also verify the release JAR
# Or: --core-jar /path/to/CorruptedStoneblockCore-0.0.1.jar
git -c core.whitespace=cr-at-eol diff --check
```

Core development occurs in a separate sibling checkout:

```sh
cd ../CorruptedStoneblockCore
./gradlew spotlessCheck test build reobfValidationJar stageRelease
```

Publish new core versions as GitHub release assets before changing the Packwiz
URL/hash; never overwrite a published version. Production artifacts are staged
under `build/release/` in the core repository. `/mods/*.jar` is excluded from Git
and Packwiz input. Metadata-referenced downloads are still installed normally;
CurseForge export may include the externally hosted JAR in archive overrides.
Root-anchored `/scripts/**` and `/docs/**` exclusions leave nested datapack data intact.

## Dedicated-server regression procedure

Use a disposable directory, never an existing server or launcher instance.
Install the pack through Packwiz installer with side `server`, using a locally
served repository URL or the PR branch's raw `pack.toml`. Install Forge
`1.20.1-47.4.10` in that directory, use Java 17, and accept the Minecraft EULA
only if you agree to it. Bind the server to localhost for this test.

```sh
cd ../CorruptedStoneblockCore
./gradlew reobfValidationJar
cp build/libs/CorruptedStoneblockCore-0.0.1-validation.jar /path/to/disposable-server/mods/
python3 scripts/run-worldgen-validation.py /path/to/disposable-server --mode fresh --java /path/to/java17/bin/java
python3 scripts/run-worldgen-validation.py /path/to/disposable-server --mode restart --java /path/to/java17/bin/java
```

The separate validation mod is **not shipped**. It is inactive unless the runner
supplies `-Dcsb.validation=fresh` or `restart`. It creates two known fake-player
identities and drives FTB's actual base-construction/party-creation logic, with the
full installed pack loaded. This tests server behavior, not real account networking.

Checks include:
- Two real FTB party records mapped to distinct generated private dimensions.
- Safe air at both spawns, solid starting floors, lobby portal presence.
- Team A edits do not change Team B; the edit and both bases survive restart.
- Real terrain on both sides of every ring boundary, in all cardinal directions,
  plus diagonal/far-coordinate checks up to 29 million blocks from the center.
- Generator codec round-trip and full-chunk block scans across all five regions.
- Empty natural structure starts and no unexpected ore/feature/carver blocks.
- On restart, fresh generation in Team B's previously unexplored regions.
- Successful server shutdown and saving all dimensions.

Reports are `validation-fresh.json` and `validation-restart.json` in the disposable
server directory. The runner refuses existing reports to avoid stale-pass mistakes.
All compilation, formatting, unit tests, pack-data/hash checks, exports, and Minecraft
server tests run **locally only**. No GitHub Actions workflows are present, including
the template's inherited build workflow. A graphical client test is still manual.

The full-pack runs passed but were not log-error-free: YACL reports a missing
mixin `minVersion`, ExtendedAE reports an `ex_emc_interface` loot-table parse
error, and GTCEu/Rhino sometimes logs reflective access to client-only classes.
These diagnostics are outside the new worldgen code and were not fixed here.
KubeJS reported zero startup/server script errors. Machine-readable results are
in `worldgen-validation.json`.

## Upstream index cleanup

The template's checked-in Packwiz index was stale at the fork revision. The first
refresh removed 46 entries whose files were already absent and corrected three
existing config hashes. No config files were deleted to add world generation.
This PR includes that necessary generated-index reconciliation.

## Reference implementation contracts

- FTB Team Bases 1.20.1 documentation:
  https://github.com/FTBTeam/FTB-Team-Bases/tree/1.20.1/main
- Generator-copy implementation:
  https://github.com/FTBTeam/FTB-Team-Bases/blob/1.20.1/main/common/src/main/java/dev/ftb/mods/ftbteambases/worldgen/chunkgen/CustomChunkGenerator.java
- Single-structure placement:
  https://github.com/FTBTeam/FTB-Team-Bases/blob/1.20.1/main/common/src/main/java/dev/ftb/mods/ftbteambases/data/construction/workers/SingleStructureWorker.java

AI disclosure: implementation, scripts, documentation, and validation were produced
with Hermes Agent (Chrono); human PR review is still required.
