import pytest

from photoscan.scale import ScaleReference, apply_scale, compute_scale


def test_single_reference_gives_real_over_model():
    ref = ScaleReference((0, 0, 0), (0, 0, 0.5), real_mm=1000.0)
    result = compute_scale([ref])
    assert result.factor == pytest.approx(2000.0)
    assert result.consistent


def test_multiple_references_are_averaged_and_spread_reported():
    refs = [
        ScaleReference((0, 0, 0), (1, 0, 0), 1000.0),
        ScaleReference((0, 0, 0), (0, 2, 0), 2000.0),
    ]
    result = compute_scale(refs)
    assert result.factor == pytest.approx(1000.0)
    assert result.spread_pct == pytest.approx(0.0)


def test_inconsistent_references_are_flagged():
    refs = [
        ScaleReference((0, 0, 0), (1, 0, 0), 1000.0),
        ScaleReference((0, 0, 0), (1, 0, 0), 1100.0),
    ]
    assert not compute_scale(refs).consistent


def test_coincident_points_rejected():
    with pytest.raises(ValueError):
        compute_scale([ScaleReference((1, 1, 1), (1, 1, 1), 500.0)])


def test_apply_scale():
    assert apply_scale([(1, 2, 3)], 10) == [(10, 20, 30)]
