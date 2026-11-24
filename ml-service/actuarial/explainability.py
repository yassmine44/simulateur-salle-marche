"""Explainable AI for the actuarial models (cahier des charges §3.6).

Method: exact interventional Shapley values
-------------------------------------------
For a profile x and a model f, the contribution of feature j is its Shapley
value

    phi_j(x) = sum over S subset of N\\{j} of  |S|! (n-|S|-1)! / n!  [v(S u {j}) - v(S)]

with the value function

    v(S) = E_b[ log f(x_S, b_notS) ]

i.e. features in S take the profile's values and the others are drawn from a
background sample b of the training portfolio. This is the "interventional"
SHAP definition (the one of shap's ExactExplainer and of TreeExplainer with
feature_perturbation="interventional").

Why exact and not approximate: the models have 9 business features, hence
2^9 = 512 coalitions, which can be enumerated. The result is exact, works for
any model (GLM, gradient boosting with native categorical splits, constant
baseline) and is computed at the level of business features, not of encoded
columns.

Why the log scale: both models use a log link, so contributions are additive
on the log scale and multiplicative on the premium:

    premium(x) = base premium x prod_j exp(phi_j(x))

exp(phi_j) is a relativity ("le bonus-malus multiplie la prime par 1.85"),
the vocabulary actuaries and regulators use for tariffs. Since
log(pure premium) = log(lambda) + log(mu) + log(1 + L), Shapley values of the
premium are the sum of those of the frequency and severity models (Shapley
values are linear in the value function).

Properties guaranteed (and tested):
* efficiency: base value + sum_j phi_j = log f(x) exactly;
* dummy: a feature the model does not use gets phi_j = 0;
* a pairwise comparison phi(a) - phi(b) sums to log(f(a) / f(b)): it explains
  why two policyholders pay different premiums.
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from math import factorial
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd
from sklearn.dummy import DummyRegressor
from sklearn.pipeline import Pipeline

from actuarial import config
from actuarial import schema as s
from actuarial.pricing import PricingEngine, load_pricing_engine

PredictFn = Callable[[pd.DataFrame], np.ndarray]

BACKGROUND_NAME = "explainer_background.csv"
BACKGROUND_SIZE = 200
GLOBAL_SAMPLE_SIZE = 200
NEUTRAL_TOLERANCE = 0.01  # |phi| below this (i.e. relativity within ~1 %) is "neutre"


def sample_background(
    policies: pd.DataFrame,
    size: int = BACKGROUND_SIZE,
    random_state: int = config.RANDOM_STATE,
) -> pd.DataFrame:
    """Reference portfolio against which contributions are measured.

    Drawn with probability proportional to exposure, so that the base value
    represents an average policy-year of the portfolio.
    """
    weights = policies[s.EXPOSURE] if s.EXPOSURE in policies else None
    sample = policies.sample(
        n=min(size, len(policies)), weights=weights, random_state=random_state
    )
    return sample[list(s.FEATURES)].reset_index(drop=True)


class ShapleyExplainer:
    """Exact interventional Shapley values on the log scale of a positive model."""

    def __init__(
        self,
        predict_fn: PredictFn,
        background: pd.DataFrame,
        features: tuple[str, ...] = s.FEATURES,
        constant: bool = False,
    ) -> None:
        """`constant=True` declares a model that ignores its inputs (e.g. the
        baseline): by the dummy axiom all its contributions are zero, so the
        512 coalitions need not be evaluated."""
        if len(features) > 12:
            raise ValueError("Exact enumeration is limited to 12 features (4 096 coalitions).")
        self.predict_fn = predict_fn
        self.features = features
        self.constant = constant
        self.background = background[list(features)].reset_index(drop=True)

        n = len(features)
        coalitions = np.arange(2**n)
        self._masks = ((coalitions[:, None] >> np.arange(n)) & 1).astype(bool)
        sizes = self._masks.sum(axis=1)
        weight_by_size = np.array(
            [factorial(k) * factorial(n - k - 1) / factorial(n) for k in range(n)]
        )
        self._without = [np.flatnonzero(~self._masks[:, j]) for j in range(n)]
        self._weights = [weight_by_size[sizes[idx]] for idx in self._without]

        self.base_value = float(np.mean(self._log_predict(self.background)))

    def _log_predict(self, frame: pd.DataFrame) -> np.ndarray:
        predictions = np.asarray(self.predict_fn(frame), dtype=float)
        if np.any(predictions <= 0):
            raise ValueError("Log-scale explanations require strictly positive predictions.")
        return np.log(predictions)

    def _coalition_values(self, profile: pd.Series) -> np.ndarray:
        """v(S) for every coalition S, evaluated in a single batch prediction."""
        n_coalitions, n_background = len(self._masks), len(self.background)
        columns = {}
        for j, feature in enumerate(self.features):
            background_values = self.background[feature].to_numpy()
            combined = np.where(
                self._masks[:, j][:, None], profile[feature], background_values[None, :]
            )
            columns[feature] = combined.ravel()
        frame = pd.DataFrame(columns)
        return self._log_predict(frame).reshape(n_coalitions, n_background).mean(axis=1)

    def shapley_values(self, profiles: pd.DataFrame) -> np.ndarray:
        """Matrix (n_profiles, n_features) of contributions on the log scale."""
        if self.constant:
            return np.zeros((len(profiles), len(self.features)))
        result = np.empty((len(profiles), len(self.features)))
        for row, (_, profile) in enumerate(profiles.iterrows()):
            values = self._coalition_values(profile)
            for j in range(len(self.features)):
                idx = self._without[j]
                result[row, j] = np.sum(self._weights[j] * (values[idx | (1 << j)] - values[idx]))
        return result


# ----------------------------------------------------------------------------
# Premium explanations
# ----------------------------------------------------------------------------
def is_constant_model(model: Any) -> bool:
    """True for a pipeline ending with a DummyRegressor (the baseline)."""
    final = model[-1] if isinstance(model, Pipeline) else model
    return isinstance(final, DummyRegressor)


def _direction(phi: float) -> str:
    if phi > NEUTRAL_TOLERANCE:
        return "hausse"
    if phi < -NEUTRAL_TOLERANCE:
        return "baisse"
    return "neutre"


def _json_value(value: Any) -> Any:
    return value.item() if isinstance(value, np.generic) else value


@dataclass(frozen=True)
class PremiumExplanation:
    """Decomposition of one pure premium into feature relativities."""

    profile: dict[str, Any]
    base_premium: float
    pure_premium: float
    frequency_phi: dict[str, float]
    severity_phi: dict[str, float]

    @property
    def premium_phi(self) -> dict[str, float]:
        return {f: self.frequency_phi[f] + self.severity_phi[f] for f in self.frequency_phi}

    def contributions(self) -> list[dict[str, Any]]:
        """One row per feature, sorted by absolute impact."""
        rows = [
            {
                "feature": feature,
                "label": s.FEATURE_LABELS[feature],
                "value": _json_value(self.profile[feature]),
                "phi": round(phi, 6),
                "relativity": round(float(np.exp(phi)), 6),
                "frequency_phi": round(self.frequency_phi[feature], 6),
                "severity_phi": round(self.severity_phi[feature], 6),
                "direction": _direction(phi),
            }
            for feature, phi in self.premium_phi.items()
        ]
        return sorted(rows, key=lambda row: abs(row["phi"]), reverse=True)

    def to_dict(self, top: int | None = None) -> dict[str, Any]:
        contributions = self.contributions()
        return {
            "base_premium": round(self.base_premium, 4),
            "pure_premium": round(self.pure_premium, 4),
            "contributions": contributions[:top] if top else contributions,
        }


class PremiumExplainer:
    """Explains the pure premium of a pricing engine, feature by feature."""

    def __init__(self, engine: PricingEngine, background: pd.DataFrame) -> None:
        self.engine = engine
        self.frequency = ShapleyExplainer(
            engine.frequency_model.predict, background, constant=is_constant_model(engine.frequency_model)
        )
        self.severity = ShapleyExplainer(
            engine.severity_model.predict, background, constant=is_constant_model(engine.severity_model)
        )
        self.features = self.frequency.features

    @property
    def base_premium(self) -> float:
        log_base = self.frequency.base_value + self.severity.base_value
        return float(np.exp(log_base) * (1 + self.engine.large_claim_loading))

    def explain(self, profiles: pd.DataFrame) -> list[PremiumExplanation]:
        frequency_phi = self.frequency.shapley_values(profiles)
        severity_phi = self.severity.shapley_values(profiles)
        pure_premiums = self.engine.quote(profiles)["pure_premium"].to_numpy()
        return [
            PremiumExplanation(
                profile=profiles.iloc[i][list(self.features)].to_dict(),
                base_premium=self.base_premium,
                pure_premium=float(pure_premiums[i]),
                frequency_phi=dict(zip(self.features, frequency_phi[i].tolist())),
                severity_phi=dict(zip(self.features, severity_phi[i].tolist())),
            )
            for i in range(len(profiles))
        ]

    def compare(self, profile_a: pd.DataFrame, profile_b: pd.DataFrame) -> dict[str, Any]:
        """Why does A pay more (or less) than B? Differences of contributions
        sum exactly to log(premium A / premium B)."""
        a, b = self.explain(pd.concat([profile_a, profile_b], ignore_index=True))
        rows = [
            {
                "feature": feature,
                "label": s.FEATURE_LABELS[feature],
                "value_a": _json_value(a.profile[feature]),
                "value_b": _json_value(b.profile[feature]),
                "phi_difference": round(a.premium_phi[feature] - b.premium_phi[feature], 6),
                "relativity": round(
                    float(np.exp(a.premium_phi[feature] - b.premium_phi[feature])), 6
                ),
            }
            for feature in self.features
        ]
        return {
            "premium_a": round(a.pure_premium, 4),
            "premium_b": round(b.pure_premium, 4),
            "premium_ratio": round(a.pure_premium / b.pure_premium, 6),
            "contributions": sorted(rows, key=lambda r: abs(r["phi_difference"]), reverse=True),
        }


def save_background(background: pd.DataFrame, directory: Path = config.ARTIFACTS_DIR) -> Path:
    path = directory / BACKGROUND_NAME
    background.to_csv(path, index=False)
    return path


def load_premium_explainer(
    directory: Path = config.ARTIFACTS_DIR, engine: PricingEngine | None = None
) -> PremiumExplainer:
    """Rebuild the explainer from the registered models and background sample."""
    path = directory / BACKGROUND_NAME
    if not path.exists():
        raise FileNotFoundError(f"{path} not found. Train the models first (python -m actuarial.train).")
    background = pd.read_csv(path, dtype={feature: str for feature in s.CATEGORICAL_FEATURES})
    return PremiumExplainer(engine or load_pricing_engine(directory), background)


# ----------------------------------------------------------------------------
# Global explanations
# ----------------------------------------------------------------------------
def global_importance(phi: np.ndarray, features: tuple[str, ...] = s.FEATURES) -> list[dict[str, Any]]:
    """Mean absolute contribution per feature, and its share of the total."""
    mean_abs = np.abs(phi).mean(axis=0)
    total = mean_abs.sum()
    rows = [
        {
            "feature": feature,
            "label": s.FEATURE_LABELS[feature],
            "mean_abs_phi": round(float(value), 6),
            "share": round(float(value / total), 6) if total > 0 else 0.0,
        }
        for feature, value in zip(features, mean_abs)
    ]
    return sorted(rows, key=lambda row: row["mean_abs_phi"], reverse=True)


def dependence(
    profiles: pd.DataFrame, phi: np.ndarray, features: tuple[str, ...] = s.FEATURES
) -> dict[str, list[dict[str, Any]]]:
    """How each feature's value drives its contribution (dependence plot data).

    Numeric features: mean contribution per observed value. Categorical
    features: mean contribution and count per category.
    """
    result: dict[str, list[dict[str, Any]]] = {}
    for j, feature in enumerate(features):
        frame = pd.DataFrame({"value": profiles[feature].to_numpy(), "phi": phi[:, j]})
        grouped = frame.groupby("value", observed=True)["phi"].agg(["mean", "size"]).reset_index()
        result[feature] = [
            {
                "value": _json_value(row["value"]),
                "mean_phi": round(float(row["mean"]), 6),
                "relativity": round(float(np.exp(row["mean"])), 6),
                "count": int(row["size"]),
            }
            for _, row in grouped.iterrows()
        ]
    return result


def build_explainability_report(
    explainer: PremiumExplainer,
    sample: pd.DataFrame,
    archetypes: pd.DataFrame,
) -> dict[str, Any]:
    """Global and local explanations, ready to be plotted by the dashboard."""
    frequency_phi = explainer.frequency.shapley_values(sample)
    severity_phi = explainer.severity.shapley_values(sample)
    premium_phi = frequency_phi + severity_phi

    local = explainer.explain(archetypes)
    comparison = (
        explainer.compare(archetypes.iloc[[0]], archetypes.iloc[[1]])
        if len(archetypes) >= 2
        else None
    )
    return {
        "method": "Valeurs de Shapley exactes (interventionnelles), échelle logarithmique",
        "background_size": len(explainer.frequency.background),
        "global_sample_size": len(sample),
        "base_premium": round(explainer.base_premium, 4),
        "global_importance": {
            "premium": global_importance(premium_phi, explainer.features),
            "frequency": global_importance(frequency_phi, explainer.features),
            "severity": global_importance(severity_phi, explainer.features),
        },
        "dependence": dependence(sample, premium_phi, explainer.features),
        "examples": [
            {"profile_name": name, **explanation.to_dict()}
            for name, explanation in zip(archetypes.index, local)
        ],
        "comparison": (
            {"profile_a": archetypes.index[0], "profile_b": archetypes.index[1], **comparison}
            if comparison
            else None
        ),
    }
