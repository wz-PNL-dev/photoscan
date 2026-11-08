# CLAUDE.md

Guidance for Claude Code working in this repository. Keep this file short;
detailed rules live in `.claude/rules/`.

## What this project is

`photoscan` is Python tooling for making **true-scale 3D models of vehicle
parts** from phone photos or video. The output is used to design fitted parts
and 3D print them, so **dimensional accuracy is the primary requirement**.
A model that is pretty but the wrong size is a failure.

Pipeline (see `README.md` for the user-facing workflow):

1. `photoscan.frames` — extract frames from video (implemented)
2. Reconstruction via COLMAP / `pycolmap` — **not yet implemented**
3. Mesh export — **not yet implemented**
4. `photoscan.scale` — compute and apply real-world scale from measured
   reference distances (implemented)
5. Phone front end — planned

## Layout

```
src/photoscan/   package code (src layout)
  frames.py      video -> JPEG frames (OpenCV, imported lazily)
  scale.py       scale factor from reference distances; pure Python, no deps
tests/           pytest tests, one test_<module>.py per module
android/         Android app (Kotlin, Compose, SceneView); see android/README.md
tools/           helper scripts, e.g. make_sample_model.py
pyproject.toml   metadata, dependencies, tool config
.claude/         Claude Code settings and rules
```

## Commands

```bash
python -m venv .venv && source .venv/bin/activate
pip install -e ".[dev]"     # install package + pytest + ruff
pytest                      # run tests (must pass before committing)
ruff check . && ruff format --check .   # lint + format check
```

## Units

- Lengths inside `photoscan.scale` are **millimetres** (`real_mm`). Model
  coordinates are unitless until a scale is applied.
- Never mix units silently. If a function takes or returns a length, name the
  unit in the parameter or field name (`_mm`, `_m`, `_px`).

## Working in this repo

- Read the relevant module and its tests before changing it.
- Keep changes small and scoped to the request. Do not refactor unrelated code.
- Add or update tests for any behaviour change. Scale and geometry code needs
  tests with hand-checkable numbers.
- Do not commit large binaries: videos, frames, meshes, and COLMAP databases
  stay out of git (see `.gitignore`).
- Do not add a dependency without saying why in the PR description.

## Git

- Develop on the branch named in the session instructions. Do not push to
  `master` directly.
- Write commit messages in the imperative mood, with a short subject line and
  a body that explains *why* when it is not obvious.
- Do not open a pull request unless asked.
