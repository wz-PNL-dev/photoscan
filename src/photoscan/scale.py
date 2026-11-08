"""Scale calibration from known real-world distances.

A photogrammetry reconstruction only knows relative proportions. Given one or
more pairs of points whose real distance is known (for example a tape measure
laid against the bodywork) and the same pairs measured on the mesh, these
functions compute the factor that brings the model to true size.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from statistics import mean, pstdev
from typing import Sequence

Point = tuple[float, float, float]


@dataclass(frozen=True)
class ScaleReference:
    """A pair of mesh points and the real distance between them in millimetres."""

    point_a: Point
    point_b: Point
    real_mm: float

    def model_distance(self) -> float:
        return euclidean(self.point_a, self.point_b)


@dataclass(frozen=True)
class ScaleResult:
    factor: float
    mean_factor: float
    spread_pct: float
    per_reference: tuple[float, ...]

    @property
    def consistent(self) -> bool:
        """True when individual references agree within 1 percent."""
        return self.spread_pct <= 1.0


def euclidean(a: Point, b: Point) -> float:
    return math.dist(a, b)


def compute_scale(references: Sequence[ScaleReference]) -> ScaleResult:
    """Return the scale factor (real / model) from one or more references.

    Several references are averaged, and the spread between them is reported
    so warping or a bad measurement can be spotted before printing.
    """
    if not references:
        raise ValueError("At least one scale reference is required")

    factors = []
    for ref in references:
        model = ref.model_distance()
        if model <= 0:
            raise ValueError("Reference points coincide; cannot compute a scale")
        if ref.real_mm <= 0:
            raise ValueError("Real distance must be positive")
        factors.append(ref.real_mm / model)

    avg = mean(factors)
    spread = (pstdev(factors) / avg * 100.0) if len(factors) > 1 else 0.0
    return ScaleResult(
        factor=avg,
        mean_factor=avg,
        spread_pct=spread,
        per_reference=tuple(factors),
    )


def apply_scale(vertices: Sequence[Point], factor: float) -> list[Point]:
    """Scale mesh vertices uniformly about the origin."""
    return [(x * factor, y * factor, z * factor) for x, y, z in vertices]
