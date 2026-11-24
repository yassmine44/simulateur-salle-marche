from __future__ import annotations

import numpy as np
import pytest

from actuarial.metrics import (
    GAMMA,
    POISSON,
    calibration_table,
    evaluate,
    gini_coefficient,
    lorenz_curve,
    sampled_lorenz,
)


@pytest.fixture()
def counts() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Poisson frequencies with a known true rate."""
    rng = np.random.default_rng(1)
    exposure = rng.uniform(0.1, 1.0, 5_000)
    rate = rng.gamma(shape=2.0, scale=0.05, size=5_000)
    observed = rng.poisson(rate * exposure) / exposure
    return observed, rate, exposure


@pytest.fixture()
def amounts() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Gamma amounts with a known true mean."""
    rng = np.random.default_rng(2)
    mean = rng.uniform(500, 3_000, 5_000)
    observed = rng.gamma(shape=2.0, scale=mean / 2.0)
    return observed, mean, np.ones_like(mean)


def test_lorenz_curve_bounds(counts) -> None:
    observed, predicted, exposure = counts
    x, y = lorenz_curve(observed, predicted, exposure)
    assert (x[0], y[0], x[-1], y[-1]) == (0.0, 0.0, 1.0, 1.0)
    assert np.all(np.diff(x) >= 0) and np.all(np.diff(y) >= 0)


def test_sampled_lorenz_has_fixed_grid(counts) -> None:
    curve = sampled_lorenz(*counts, points=11)
    assert len(curve["cumulative_weight"]) == len(curve["cumulative_total"]) == 11


def test_gini_ranks_informative_model_above_random(counts) -> None:
    observed, rate, exposure = counts
    random_scores = np.random.default_rng(3).uniform(size=len(observed))
    assert gini_coefficient(observed, rate, exposure) > 0.15
    assert abs(gini_coefficient(observed, random_scores, exposure)) < 0.05
    assert gini_coefficient(observed, -rate, exposure) < 0


@pytest.mark.parametrize(("sample", "power"), [("counts", POISSON), ("amounts", GAMMA)])
def test_weighted_mean_prediction_explains_nothing(sample, power, request) -> None:
    observed, _, weight = request.getfixturevalue(sample)
    mean = np.average(observed, weights=weight)
    metrics = evaluate(observed, np.full_like(observed, mean), weight, power)

    assert metrics.d2_explained == pytest.approx(0.0, abs=1e-9)
    assert metrics.calibration_ratio == pytest.approx(1.0)


@pytest.mark.parametrize(("sample", "power"), [("counts", POISSON), ("amounts", GAMMA)])
def test_true_mean_beats_constant(sample, power, request) -> None:
    observed, truth, weight = request.getfixturevalue(sample)
    constant = np.full_like(observed, np.average(observed, weights=weight))
    assert (
        evaluate(observed, truth, weight, power).mean_deviance
        < evaluate(observed, constant, weight, power).mean_deviance
    )


def test_evaluate_rejects_non_positive_predictions(counts) -> None:
    observed, _, exposure = counts
    with pytest.raises(ValueError, match="positive predictions"):
        evaluate(observed, np.zeros_like(observed), exposure, POISSON)


def test_gamma_rejects_zero_observations(amounts) -> None:
    observed, mean, weight = amounts
    observed = observed.copy()
    observed[0] = 0.0
    with pytest.raises(ValueError, match="positive observations"):
        evaluate(observed, mean, weight, GAMMA)


def test_calibration_table_preserves_totals(counts) -> None:
    observed, predicted, exposure = counts
    table = calibration_table(observed, predicted, exposure, n_bins=10)

    assert list(table["risk_class"]) == list(range(1, 11))
    assert table["weight"].sum() == pytest.approx(exposure.sum())
    assert table["observed_total"].sum() == pytest.approx(np.sum(observed * exposure))
    assert table["predicted_mean"].is_monotonic_increasing
