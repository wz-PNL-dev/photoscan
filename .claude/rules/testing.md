# Testing rules

- Every module in `src/photoscan/` has a matching `tests/test_<module>.py`.
- Geometry and scale tests use small inputs whose answers can be checked by
  hand (unit cubes, axis-aligned distances). State the expected value and why.
- Use `pytest.approx` for floats; never compare floats with `==`.
- Tests must not depend on real video or photo files. If a test needs a video,
  generate a tiny one in a `tmp_path` fixture.
- Tests that need an optional heavy dependency (OpenCV, pycolmap) use
  `pytest.importorskip` so the suite still runs without it.
- A bug fix comes with a test that fails before the fix.
