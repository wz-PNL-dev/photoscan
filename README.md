# photoscan

Photogrammetry tooling for making true-scale 3D models of vehicle parts from
photos or video, for designing fitted parts and 3D printing.

## Status

Early scaffold. Implemented so far:

- `photoscan.frames.extract_frames`: pull frames from video at a set rate (OpenCV)
- `photoscan.scale`: compute and apply a real-world scale factor from measured
  reference distances, with a consistency check across several references

Planned: reconstruction (COLMAP via pycolmap), mesh export, and a phone front end.

## Setup

```bash
python -m venv .venv
source .venv/bin/activate
pip install -e ".[dev]"
pytest
```

## Workflow

1. Shoot a slow, steady orbit of the car with a ruler or tape laid against the bodywork.
2. Extract frames: `extract_frames("car.mp4", "frames/", fps=3)`
3. Reconstruct the mesh (not yet implemented).
4. Measure several known distances on the mesh and compute the scale with `compute_scale`.
5. Apply the factor with `apply_scale` and check the spread before designing parts.
