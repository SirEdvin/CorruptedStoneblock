#!/usr/bin/env python3
"""Generate authored, deterministic structure NBT without third-party assets/dependencies."""
import gzip
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def text(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data

def payload(kind, value):
    if kind == 3:
        return struct.pack('>i', value)
    if kind == 8:
        return text(value)
    if kind == 9:
        item_kind, items = value
        return bytes([item_kind]) + struct.pack('>i', len(items)) + b''.join(payload(item_kind, item) for item in items)
    if kind == 10:
        return b''.join(bytes([t]) + text(k) + payload(t, v) for k, (t, v) in value.items()) + b'\0'
    raise ValueError(kind)

def structure(lobby=False):
    palette = [{'Name': (8, name)} for name in [
        'minecraft:cobblestone', 'minecraft:air', 'minecraft:torch',
        'ftbteambases:portal', 'minecraft:structure_block']]
    blocks = []
    for x in range(9):
        for y in range(7):
            for z in range(9):
                state = 0 if x in (0, 8) or y in (0, 6) or z in (0, 8) else 1
                if y == 1 and (x, z) in ((1, 1), (1, 7), (7, 1), (7, 7)):
                    state = 2
                if lobby and x == 4 and z == 7 and y in (1, 2, 3):
                    state = 3
                block = {'pos': (9, (3, [x, y, z])), 'state': (3, state)}
                if lobby and (x, y, z) == (4, 1, 4):
                    block['state'] = (3, 4)
                    block['nbt'] = (10, {'id': (8, 'minecraft:structure_block'),
                                       'mode': (8, 'DATA'), 'metadata': (8, 'spawn_point')})
                blocks.append(block)
    return {'DataVersion': (3, 3465), 'size': (9, (3, [9, 7, 9])),
            'palette': (9, (10, palette)), 'blocks': (9, (10, blocks)), 'entities': (9, (10, []))}

def main():
    target = ROOT / 'kubejs/data/corruptedstoneblock/structures'
    target.mkdir(parents=True, exist_ok=True)
    for name, lobby in [('starting_chamber', False), ('lobby', True)]:
        data = b'\x0a\x00\x00' + payload(10, structure(lobby))
        path = target / (name + '.nbt')
        path.write_bytes(gzip.compress(data, mtime=0))
        print(path.relative_to(ROOT))

if __name__ == '__main__':
    main()
