#!/usr/bin/env python3
"""Cheap regression checks for pack data, reproducible templates, and Packwiz inventory."""
import argparse
import gzip
import io
import hashlib
import importlib.util
import json
from pathlib import Path
import tomllib
import zipfile
from urllib.request import urlopen

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--core-jar', type=Path, help='Verify a local release JAR against the pinned hash')
    group.add_argument('--download-core', action='store_true', help='Download and verify the published core in memory')
    args = parser.parse_args()
    data = ROOT / 'kubejs/data/corruptedstoneblock'
    dimension = json.loads((data / 'dimension/ring_template.json').read_text())
    generator = dimension['generator']
    assert generator['type'] == 'corruptedstoneblock:stone_rings'
    assert [r['width'] for r in generator['rings']] == [512, 512, 1024, 1024]
    assert [r['block']['Name'] for r in generator['rings']] == [
        'minecraft:cobblestone', 'minecraft:stone', 'minecraft:deepslate', 'minecraft:obsidian']
    assert generator['outer_block']['Name'] == 'minecraft:crying_obsidian'
    dtype = json.loads((data / 'dimension_type/underground.json').read_text())
    assert (dtype['min_y'], dtype['height']) == (generator['min_y'], generator['height']) == (-64, 384)
    biome = json.loads((data / 'worldgen/biome/barren.json').read_text())
    assert biome['features'] == [] and biome['carvers'] == {} and biome['spawners'] == {}
    base = json.loads((data / 'ftb_base_definitions/stone_rings.json').read_text())
    assert base['dimension']['private'] is True
    assert base['absolute_spawn'] == [generator['center_x'], 64, generator['center_z']] == [255, 64, 255]
    assert base['extents'] == {'x': 1, 'z': 1}  # FTB's single-region placement center is 255,255
    assert base['construction']['y_pos'] == 63
    spec = importlib.util.spec_from_file_location('templates', ROOT / 'scripts/generate-starting-structures.py')
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    for name, lobby in [('starting_chamber', False), ('lobby', True)]:
        expected = b'\x0a\x00\x00' + module.payload(10, module.structure(lobby))
        assert gzip.decompress((data / f'structures/{name}.nbt').read_bytes()) == expected
    pack = tomllib.loads((ROOT / 'pack.toml').read_text())
    index_bytes = (ROOT / 'index.toml').read_bytes()
    assert hashlib.sha256(index_bytes).hexdigest() == pack['index']['hash']
    index = tomllib.loads(index_bytes.decode())
    names = set()
    for entry in index['files']:
        name = entry['file']
        assert name not in names, f'Duplicate index entry: {name}'
        names.add(name)
        assert not name.startswith(('CorruptedStoneblockCore/', 'scripts/', 'docs/')), name
        assert hashlib.sha256((ROOT / name).read_bytes()).hexdigest() == entry['hash'], name
    for path in data.rglob('*'):
        if path.is_file():
            assert str(path.relative_to(ROOT)) in names, f'Datapack file excluded: {path}'
    core_path = 'mods/corrupted-stoneblock-core.pw.toml'
    assert core_path in names
    assert next(e for e in index['files'] if e['file'] == core_path)['metafile'] is True
    core = tomllib.loads((ROOT / core_path).read_text())
    assert core['name'] == 'CorruptedStoneblockCore' and core['side'] == 'both'
    assert core['filename'] == 'CorruptedStoneblockCore-0.0.1.jar'
    assert core['download']['url'] == (
        'https://github.com/SirEdvin/CorruptedStoneblockCore/releases/download/0.0.1/' + core['filename'])
    assert core['download']['hash-format'] == 'sha256'
    assert len(core['download']['hash']) == 64 and all(c in '0123456789abcdef' for c in core['download']['hash'])
    assert not any(n.startswith('mods/') and n.endswith('.jar') for n in names), 'Mod binaries must use metadata'
    assert not list((ROOT / 'mods').glob('*.jar')), 'Keep downloaded JARs outside the pack source tree'
    payload = None
    if args.core_jar:
        payload = args.core_jar.read_bytes()
    elif args.download_core:
        with urlopen(core['download']['url'], timeout=60) as response:
            payload = response.read()
    if payload is not None:
        assert hashlib.sha256(payload).hexdigest() == core['download']['hash'], 'Core release hash mismatch'
        with zipfile.ZipFile(io.BytesIO(payload)) as archive:
            assert not any('/validation/' in n for n in archive.namelist()), 'Test harness shipped!'
            assert 'site/siredvin/corruptedstoneblockcore/StoneRingGenerator.class' in archive.namelist()
            metadata = tomllib.loads(archive.read('META-INF/mods.toml').decode())
            assert metadata['mods'][0]['version'] == '0.0.1'
            assert metadata['mods'][0]['modId'] == 'corruptedstoneblockcore'
            assert metadata['mods'][0]['displayName'] == 'CorruptedStoneblockCore'
            assert any(d['modId'] == 'gtceu' and d['mandatory'] for d in metadata['dependencies']['corruptedstoneblockcore'])
            assert 'site/siredvin/corruptedstoneblockcore/CorruptedStoneblockGTAddon.class' in archive.namelist()
        print('PASS: production release JAR contents and SHA-256')
    assert not list((ROOT / '.github/workflows').glob('*.yml')), 'Validation must be local only'
    assert not list((ROOT / '.github/workflows').glob('*.yaml')), 'Validation must be local only'
    assert not (ROOT / 'mods/corrupted-stoneblock-worldgen-0.1.0.jar').exists(), 'Obsolete generator JAR remains'
    print(f'PASS: ring data, private base, templates, release metadata, and {len(names)} indexed hashes')


if __name__ == '__main__':
    main()
