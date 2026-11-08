# Python conventions

- Target Python 3.10+. Use `from __future__ import annotations` and modern
  union syntax (`str | Path`).
- Type-annotate public functions and dataclass fields.
- Use `dataclass(frozen=True)` for value objects (see `ScaleReference`).
- Raise `ValueError` for bad arguments and `OSError` for I/O failures, with a
  message that includes the offending value.
- Import heavy optional dependencies (OpenCV, pycolmap) lazily inside the
  function that needs them, so the rest of the package imports without them.
- No print statements in library code; return data or raise. CLI or script
  output is the caller's job.
- Format and lint with `ruff` (configured in `pyproject.toml`).
