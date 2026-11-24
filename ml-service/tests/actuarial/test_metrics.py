from __future__ import annotations

import numpy as np
import pytest

from actuarial.metrics import calibration_table, evaluate_frequency, gini_coefficient, lorenz_curve


@pytest.fixture()
def sample() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    rng = np.random.default_rng(1)
    exposure = rng.uniform(0.1, 1.0, 5_000)
    true_frequency = rng.gamma(shape=2.0, scale=0.05, size=5_000)
    observed = rng.poisson(true_frequency * exposure) / exposure
    return observed, true_frequency, exposure


def test_lorenz_curve_bounds(sample) -> None:
    observed, predicted, exposure = sample
    x, y = lorenz_curve(observed, predicted, exposure)
    assert (x[0], y[0], x[-1], y[-1]) == (0.0, 0.0, 1.0, 1.0)
    assert np.all(np.diff(x) >= 0) and np.all(np.diff(y) >= 0)


def test_gini_ranks_informative_model_above_random(sample) -> None:
    observed, true_frequency, exposure = sample
    random_scores = np.random.default_rng(2).uniform(size=len(observed))
    assert gini_coefficient(observed, true_frequency, exposure) > 0.15
    assert abs(gini_coefficient(observed, random_scores, exposure)) < 0.05
    assert gini_coefficient(observed, -true_frequency, exposure) < 0


def test_constant_mean_prediction_explains_nothing(sample) -> None:
    observed, _, exposure = sample
    portfolio_frequency = np.sum(observed * exposure) / np.sum(exposure)
    metrics = evaluate_frequency(observed, np.full_like(observed, portfolio_frequency), exposure)

    assert metrics.d2_explained == pytest.approx(0.0, abs=1e-9)
    assert metrics.calibration_ratio == pytest.approx(1.0)


def test_true_frequency_beats_constant(sample) -> None:
    observed, true_frequency, exposure = sample
    constant = np.full_like(observed, np.average(observed, weights=exposure))
    assert (
        evaluate_frequency(observed, true_frequency, exposure).mean_poisson_deviance
        < evaluate_frequency(observed, constant, exposure).mean_poisson_deviance
    )


def test_evaluate_rejects_non_positive_predictions(sample) -> None:
    observed, _, exposure = sample
    with pytest.raises(ValueError, match="positive"):
        evaluate_frequency(observed, np.zeros_like(observed), exposure)


def test_calibration_table_preserves_totals(sample) -> None:
    observed, predicted, exposure = sample
    table = calibration_table(observed, predicted, exposure, n_bins=10)

    assert list(table["risk_class"]) == list(range(1, 11))
    assert table["exposure"].sum() == pytest.approx(exposure.sum())
    assert table["observed_claims"].sum() == pytest.approx(np.sum(observed * exposure))
    assert table["predicted_frequency"].is_monotonic_increasing
