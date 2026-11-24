"""Evaluation metrics for claim frequency models.

Accuracy or RMSE are not suited to claim counts: 95 % of policies have no
claim and every policy has a different exposure. Insurance pricing models are
judged on three complementary axes:

1. Goodness of fit: exposure-weighted mean Poisson deviance and D^2 (share
   of the deviance explained, the Poisson equivalent of R^2).
2. Ranking power: Gini coefficient of the Lorenz curve. Can the model tell
   risky drivers from safe ones? This is what makes a tariff competitive.
3. Calibration: does the model predict the right total number of claims,
   globally and per risk segment? This is what keeps the insurer solvent.
"""

from __future__ import annotations

from dataclasses import asdict, dataclass

import numpy as np
import pandas as pd
from sklearn.metrics import auc, d2_tweedie_score, mean_poisson_deviance


@dataclass(frozen=True)
class FrequencyMetrics:
    mean_poisson_deviance: float
    d2_explained: float
    gini: float
    observed_claims: float
    predicted_claims: float
    calibration_ratio: float  # predicted / observed claims, ideal = 1

    def to_dict(self) -> dict[str, float]:
        return {key: round(value, 6) for key, value in asdict(self).items()}


def _as_arrays(*values: object) -> list[np.ndarray]:
    return [np.asarray(value, dtype=float) for value in values]


def lorenz_curve(
    frequency_true: np.ndarray,
    frequency_pred: np.ndarray,
    exposure: np.ndarray,
) -> tuple[np.ndarray, np.ndarray]:
    """Cumulative share of exposure vs cumulative share of claims.

    Policies are ranked from the lowest to the highest predicted risk. A model
    that ranks well accumulates few claims at the beginning of the curve.
    """
    frequency_true, frequency_pred, exposure = _as_arrays(frequency_true, frequency_pred, exposure)
    order = np.argsort(frequency_pred, kind="stable")
    claims = np.cumsum(frequency_true[order] * exposure[order])
    exposures = np.cumsum(exposure[order])
    return (
        np.concatenate([[0.0], exposures / exposures[-1]]),
        np.concatenate([[0.0], claims / claims[-1]]),
    )


def gini_coefficient(
    frequency_true: np.ndarray,
    frequency_pred: np.ndarray,
    exposure: np.ndarray,
) -> float:
    """Gini = 1 - 2 x area under the Lorenz curve (0 = random, higher = better)."""
    x, y = lorenz_curve(frequency_true, frequency_pred, exposure)
    return float(1.0 - 2.0 * auc(x, y))


def evaluate_frequency(
    frequency_true: np.ndarray,
    frequency_pred: np.ndarray,
    exposure: np.ndarray,
) -> FrequencyMetrics:
    frequency_true, frequency_pred, exposure = _as_arrays(frequency_true, frequency_pred, exposure)
    if np.any(frequency_pred <= 0):
        raise ValueError("Poisson metrics require strictly positive predictions.")

    observed = float(np.sum(frequency_true * exposure))
    predicted = float(np.sum(frequency_pred * exposure))
    return FrequencyMetrics(
        mean_poisson_deviance=float(
            mean_poisson_deviance(frequency_true, frequency_pred, sample_weight=exposure)
        ),
        d2_explained=float(
            d2_tweedie_score(frequency_true, frequency_pred, sample_weight=exposure, power=1)
        ),
        gini=gini_coefficient(frequency_true, frequency_pred, exposure),
        observed_claims=observed,
        predicted_claims=predicted,
        calibration_ratio=predicted / observed if observed else float("nan"),
    )


def calibration_table(
    frequency_true: np.ndarray,
    frequency_pred: np.ndarray,
    exposure: np.ndarray,
    n_bins: int = 10,
) -> pd.DataFrame:
    """Observed vs predicted frequency per risk decile (exposure-weighted).

    Deciles are built on the predicted frequency, so each row is a risk class
    of the tariff. A well calibrated model has observed ~= predicted on every row.
    """
    frequency_true, frequency_pred, exposure = _as_arrays(frequency_true, frequency_pred, exposure)
    frame = pd.DataFrame(
        {
            "observed_claims": frequency_true * exposure,
            "predicted_claims": frequency_pred * exposure,
            "exposure": exposure,
            "predicted_frequency": frequency_pred,
        }
    )
    frame["risk_class"] = (
        pd.qcut(frame["predicted_frequency"].rank(method="first"), q=n_bins, labels=False) + 1
    )
    table = frame.groupby("risk_class", observed=True).agg(
        exposure=("exposure", "sum"),
        observed_claims=("observed_claims", "sum"),
        predicted_claims=("predicted_claims", "sum"),
    )
    table["observed_frequency"] = table["observed_claims"] / table["exposure"]
    table["predicted_frequency"] = table["predicted_claims"] / table["exposure"]
    return table.reset_index()
