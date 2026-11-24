"""Evaluation metrics for actuarial models.

Accuracy or RMSE are not suited to insurance targets: 95 % of policies have
no claim, claim amounts are heavy-tailed and every observation carries a
different weight (exposure for frequency, number of claims for severity).
Pricing models are judged on three complementary axes:

1. Goodness of fit: weighted mean Tweedie deviance matching the model's
   distribution (power 1 = Poisson for frequency, power 2 = Gamma for
   severity) and D^2, the share of deviance explained (the GLM analogue of R^2).
2. Ranking power: Gini coefficient of the Lorenz curve. Can the model tell
   high risks from low risks? This is what makes a tariff competitive.
3. Calibration: does the model predict the right total, globally and per
   risk class? This is what keeps the insurer solvent.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass

import numpy as np
import pandas as pd
from sklearn.metrics import auc, d2_tweedie_score, mean_tweedie_deviance

POISSON = 1.0
GAMMA = 2.0


@dataclass(frozen=True)
class ModelMetrics:
    deviance_power: float
    mean_deviance: float
    d2_explained: float
    gini: float
    observed_total: float
    predicted_total: float
    calibration_ratio: float  # predicted / observed total, ideal = 1

    def to_dict(self) -> dict[str, float]:
        return {key: round(value, 6) for key, value in asdict(self).items()}


def _as_arrays(*values: object) -> list[np.ndarray]:
    return [np.asarray(value, dtype=float) for value in values]


def lorenz_curve(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    weight: np.ndarray,
) -> tuple[np.ndarray, np.ndarray]:
    """Cumulative share of weight vs cumulative share of the observed total.

    Observations are ranked from the lowest to the highest prediction. A model
    that ranks well accumulates little of the total at the start of the curve.
    """
    y_true, y_pred, weight = _as_arrays(y_true, y_pred, weight)
    order = np.argsort(y_pred, kind="stable")
    totals = np.cumsum(y_true[order] * weight[order])
    weights = np.cumsum(weight[order])
    return (
        np.concatenate([[0.0], weights / weights[-1]]),
        np.concatenate([[0.0], totals / totals[-1]]),
    )


def gini_coefficient(y_true: np.ndarray, y_pred: np.ndarray, weight: np.ndarray) -> float:
    """Gini = 1 - 2 x area under the Lorenz curve (0 = random, higher = better)."""
    x, y = lorenz_curve(y_true, y_pred, weight)
    return float(1.0 - 2.0 * auc(x, y))


def evaluate(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    weight: np.ndarray,
    power: float,
) -> ModelMetrics:
    y_true, y_pred, weight = _as_arrays(y_true, y_pred, weight)
    if np.any(y_pred <= 0):
        raise ValueError("Tweedie metrics require strictly positive predictions.")
    if power >= 2 and np.any(y_true <= 0):
        raise ValueError("Gamma metrics require strictly positive observations.")

    observed = float(np.sum(y_true * weight))
    predicted = float(np.sum(y_pred * weight))
    return ModelMetrics(
        deviance_power=power,
        mean_deviance=float(mean_tweedie_deviance(y_true, y_pred, sample_weight=weight, power=power)),
        d2_explained=float(d2_tweedie_score(y_true, y_pred, sample_weight=weight, power=power)),
        gini=gini_coefficient(y_true, y_pred, weight),
        observed_total=observed,
        predicted_total=predicted,
        calibration_ratio=predicted / observed if observed else float("nan"),
    )


def calibration_table(
    y_true: np.ndarray,
    y_pred: np.ndarray,
    weight: np.ndarray,
    n_bins: int = 10,
) -> pd.DataFrame:
    """Observed vs predicted weighted mean per risk class (deciles of prediction).

    Each row is a risk class of the tariff. A well calibrated model has
    observed_mean ~= predicted_mean on every row.
    """
    y_true, y_pred, weight = _as_arrays(y_true, y_pred, weight)
    frame = pd.DataFrame(
        {
            "observed_total": y_true * weight,
            "predicted_total": y_pred * weight,
            "weight": weight,
            "prediction": y_pred,
        }
    )
    frame["risk_class"] = (
        pd.qcut(frame["prediction"].rank(method="first"), q=n_bins, labels=False) + 1
    )
    table = frame.groupby("risk_class", observed=True).agg(
        weight=("weight", "sum"),
        observed_total=("observed_total", "sum"),
        predicted_total=("predicted_total", "sum"),
    )
    table["observed_mean"] = table["observed_total"] / table["weight"]
    table["predicted_mean"] = table["predicted_total"] / table["weight"]
    return table.reset_index()


def sampled_lorenz(
    y_true: np.ndarray, y_pred: np.ndarray, weight: np.ndarray, points: int = 101
) -> dict[str, list[float]]:
    """Lorenz curve resampled on a fixed grid, light enough for a web chart."""
    x, y = lorenz_curve(y_true, y_pred, weight)
    grid = np.linspace(0.0, 1.0, points)
    return {
        "cumulative_weight": grid.round(4).tolist(),
        "cumulative_total": np.interp(grid, x, y).round(6).tolist(),
    }
