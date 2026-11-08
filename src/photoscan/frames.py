"""Extract still frames from video for photogrammetry input."""

from __future__ import annotations

from pathlib import Path


def extract_frames(
    video_path: str | Path,
    out_dir: str | Path,
    fps: float = 3.0,
    max_frames: int | None = None,
    jpeg_quality: int = 95,
) -> list[Path]:
    """Save frames from a video at roughly ``fps`` frames per second.

    Returns the paths of the saved images. OpenCV is imported lazily so the
    rest of the package works without it installed.
    """
    import cv2

    if fps <= 0:
        raise ValueError("fps must be positive")

    cap = cv2.VideoCapture(str(video_path))
    if not cap.isOpened():
        raise OSError(f"Could not open video: {video_path}")

    try:
        native_fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        step = max(1, round(native_fps / fps))
        out = Path(out_dir)
        out.mkdir(parents=True, exist_ok=True)

        saved: list[Path] = []
        index = 0
        while True:
            ok, frame = cap.read()
            if not ok:
                break
            if index % step == 0:
                path = out / f"frame_{len(saved):05d}.jpg"
                cv2.imwrite(str(path), frame, [cv2.IMWRITE_JPEG_QUALITY, jpeg_quality])
                saved.append(path)
                if max_frames is not None and len(saved) >= max_frames:
                    break
            index += 1
        return saved
    finally:
        cap.release()
