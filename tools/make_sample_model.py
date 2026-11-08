"""Write a simple car-shaped GLB for testing the viewer without a reconstruction.

Units are metres, Y up, car nose along +X. Standard library only, so it runs
anywhere: ``python tools/make_sample_model.py [out.glb]``.
"""

from __future__ import annotations

import json
import math
import struct
import sys
from pathlib import Path

Vec3 = tuple[float, float, float]


def _box(x0: float, x1: float, y0: float, y1: float, z0: float, z1: float):
    """Return (positions, normals, indices) for an axis-aligned box, flat-shaded."""
    faces = [
        # (normal, four corners counter-clockwise seen from outside)
        ((1, 0, 0), [(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)]),
        ((-1, 0, 0), [(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)]),
        ((0, 1, 0), [(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)]),
        ((0, -1, 0), [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]),
        ((0, 0, 1), [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)]),
        ((0, 0, -1), [(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)]),
    ]
    return _quads(faces)


def _wheel(
    cx: float, cy: float, cz: float, radius: float, width: float, segs: int = 24
):
    """A cylinder whose axis runs along Z, centred at (cx, cy, cz)."""
    faces = []
    z0, z1 = cz - width / 2, cz + width / 2
    ring = []
    for i in range(segs):
        a = 2 * math.pi * i / segs
        ring.append((math.cos(a), math.sin(a)))
    for i in range(segs):
        ax, ay = ring[i]
        bx, by = ring[(i + 1) % segs]
        # Side quad, outward normal along the average of the two ring points.
        nx, ny = (ax + bx) / 2, (ay + by) / 2
        length = math.hypot(nx, ny)
        nrm = (nx / length, ny / length, 0.0)
        quad = [
            (cx + radius * ax, cy + radius * ay, z0),
            (cx + radius * bx, cy + radius * by, z0),
            (cx + radius * bx, cy + radius * by, z1),
            (cx + radius * ax, cy + radius * ay, z1),
        ]
        faces.append((nrm, quad))
    # Two caps as fans of quads degenerate to triangles: use flat quads per wedge.
    for side, z in ((1, z1), (-1, z0)):
        for i in range(segs):
            ax, ay = ring[i]
            bx, by = ring[(i + 1) % segs]
            quad = [
                (cx, cy, z),
                (cx + radius * ax, cy + radius * ay, z),
                (cx + radius * bx, cy + radius * by, z),
                (cx, cy, z),
            ]
            faces.append(((0.0, 0.0, float(side)), quad[:3] + [quad[2]]))
    return _quads(faces)


def _quads(faces):
    positions: list[Vec3] = []
    normals: list[Vec3] = []
    indices: list[int] = []
    for normal, corners in faces:
        base = len(positions)
        for corner in corners[:4]:
            positions.append(tuple(float(v) for v in corner))
            normals.append(tuple(float(v) for v in normal))
        indices += [base, base + 1, base + 2, base, base + 2, base + 3]
    return positions, normals, indices


def build_car():
    parts = [
        _box(-2.2, 2.2, 0.35, 1.0, -0.9, 0.9),  # body
        _box(-1.2, 1.0, 1.0, 1.6, -0.8, 0.8),  # cabin
    ]
    for x in (-1.4, 1.4):
        for z in (-0.9, 0.9):
            parts.append(_wheel(x, 0.35, z, radius=0.35, width=0.3))
    positions, normals, indices = [], [], []
    for p, n, i in parts:
        offset = len(positions)
        positions += p
        normals += n
        indices += [idx + offset for idx in i]
    return positions, normals, indices


def write_glb(path: Path) -> None:
    positions, normals, indices = build_car()
    pos_bytes = b"".join(struct.pack("<3f", *p) for p in positions)
    nrm_bytes = b"".join(struct.pack("<3f", *n) for n in normals)
    idx_bytes = b"".join(struct.pack("<I", i) for i in indices)
    binary = pos_bytes + nrm_bytes + idx_bytes

    xs, ys, zs = zip(*positions, strict=True)
    gltf = {
        "asset": {
            "version": "2.0",
            "generator": "photoscan tools/make_sample_model.py",
        },
        "scene": 0,
        "scenes": [{"nodes": [0]}],
        "nodes": [{"mesh": 0, "name": "sample_car"}],
        "meshes": [
            {
                "primitives": [
                    {
                        "attributes": {"POSITION": 0, "NORMAL": 1},
                        "indices": 2,
                        "material": 0,
                        "mode": 4,
                    }
                ],
            }
        ],
        "materials": [
            {
                "name": "paint",
                "doubleSided": True,
                "pbrMetallicRoughness": {
                    "baseColorFactor": [0.75, 0.1, 0.1, 1.0],
                    "metallicFactor": 0.0,
                    "roughnessFactor": 0.5,
                },
            }
        ],
        "accessors": [
            {
                "bufferView": 0,
                "componentType": 5126,
                "count": len(positions),
                "type": "VEC3",
                "min": [min(xs), min(ys), min(zs)],
                "max": [max(xs), max(ys), max(zs)],
            },
            {
                "bufferView": 1,
                "componentType": 5126,
                "count": len(normals),
                "type": "VEC3",
            },
            {
                "bufferView": 2,
                "componentType": 5125,
                "count": len(indices),
                "type": "SCALAR",
            },
        ],
        "bufferViews": [
            {
                "buffer": 0,
                "byteOffset": 0,
                "byteLength": len(pos_bytes),
                "target": 34962,
            },
            {
                "buffer": 0,
                "byteOffset": len(pos_bytes),
                "byteLength": len(nrm_bytes),
                "target": 34962,
            },
            {
                "buffer": 0,
                "byteOffset": len(pos_bytes) + len(nrm_bytes),
                "byteLength": len(idx_bytes),
                "target": 34963,
            },
        ],
        "buffers": [{"byteLength": len(binary)}],
    }

    json_bytes = json.dumps(gltf, separators=(",", ":")).encode("utf-8")
    json_bytes += b" " * (-len(json_bytes) % 4)
    binary += b"\x00" * (-len(binary) % 4)

    total = 12 + 8 + len(json_bytes) + 8 + len(binary)
    out = bytearray()
    out += struct.pack("<4sII", b"glTF", 2, total)
    out += struct.pack("<I4s", len(json_bytes), b"JSON") + json_bytes
    out += struct.pack("<I4s", len(binary), b"BIN\x00") + binary
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(bytes(out))


if __name__ == "__main__":
    target = (
        Path(sys.argv[1])
        if len(sys.argv) > 1
        else Path(__file__).resolve().parent.parent
        / "android/app/src/main/assets/sample_car.glb"
    )
    write_glb(target)
    print(f"wrote {target} ({target.stat().st_size} bytes)")
