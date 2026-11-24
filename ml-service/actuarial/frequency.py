"""Claim frequency models (cahier des charges §3.7: survenance des sinistres).

Statistical framing
-------------------
For a policy i observed during an exposure e_i (in years), the number of
claims N_i is modelled as

    N_i ~ Poisson(lambda(x_i) * e_i)

where lambda(x) is the annual claim frequency for a risk profile x. Fitting
the annualised frequency y_i = N_i / e_i with sample weight e_i under a
Poisson deviance is equivalent to maximising this likelihood, and is the
industry standard for frequency modelling.

Once lambda is estimated, the probability that a policy holder files at least
one claim over a period t is

    P(N >= 1) = 1 - exp(-lambda(x) * t)

Candidates
----------
baseline     Single portfolio-wide frequency. Every model must beat it.
poisson_glm  Log-link Poisson GLM, the regulatory and market reference:
             fully interpretable, each coefficient is a multiplicative
             relativity of the tariff.
poisson_gbm  Gradient boosted trees with a Poisson loss: captures
             non-linearities and interactions automatically.
"""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.dummy import DummyRegressor
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.linear_model import PoissonRegressor
from sklearn.pipeline import Pipeline

from actuarial import config
from actuarial import schema as s
from actuarial.data import PortfolioSplit
from actuarial.metrics import FrequencyMetrics, evaluate_frequency
from actuarial.preprocessing import (
    build_glm_preprocessor,
    build_tree_preprocessor,
    tree_categorical_mask,
)

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Candidate:
    name: str
    algorithm: str
    description: str
    build: Callable[[], Pipeline]


@dataclass
class CandidateResult:
    candidate: Candidate
    model: Pipeline
    train_metrics: FrequencyMetrics
    test_metrics: FrequencyMetrics
    fit_seconds: float
    test_predictions: np.ndarray = field(repr=False)

    def summary(self) -> dict[str, object]:
        return {
            "name": self.candidate.name,
            "algorithm": self.candidate.algorithm,
            "description": self.candidate.description,
            "fit_seconds": round(self.fit_seconds, 2),
            "train": self.train_metrics.to_dict(),
            "test": self.test_metrics.to_dict(),
        }


def build_candidates(random_state: int = config.RANDOM_STATE) -> tuple[Candidate, ...]:
    return (
        Candidate(
            name="baseline",
            algorithm="DummyRegressor(mean)",
            description="Fréquence moyenne unique du portefeuille (référence minimale).",
            build=lambda: Pipeline(
                [
                    ("preprocess", build_glm_preprocessor()),
                    ("model", DummyRegressor(strategy="mean")),
                ]
            ),
        ),
        Candidate(
            name="poisson_glm",
            algorithm="PoissonRegressor (GLM, lien log)",
            description="GLM de Poisson : modèle de référence du marché, entièrement interprétable.",
            build=lambda: Pipeline(
                [
                    ("preprocess", build_glm_preprocessor()),
                    (
                        "model",
                        PoissonRegressor(alpha=1e-6, solver="newton-cholesky", max_iter=300),
                    ),
                ]
            ),
        ),
        Candidate(
            name="poisson_gbm",
            algorithm="HistGradientBoostingRegressor (loss=poisson)",
            description="Gradient boosting à perte de Poisson : capte non-linéarités et interactions.",
            build=lambda: Pipeline(
                [
                    ("preprocess", build_tree_preprocessor()),
                    (
                        "model",
                        HistGradientBoostingRegressor(
                            loss="poisson",
                            learning_rate=0.05,
                            max_iter=1000,
                            max_leaf_nodes=31,
                            min_samples_leaf=200,
                            l2_regularization=1.0,
                            categorical_features=tree_categorical_mask(),
                            early_stopping=True,
                            validation_fraction=0.1,
                            n_iter_no_change=30,
                            random_state=random_state,
                        ),
                    ),
                ]
            ),
        ),
    )


def fit_frequency_model(model: Pipeline, policies: pd.DataFrame) -> Pipeline:
    model.fit(
        policies[list(s.FEATURES)],
        policies[s.FREQUENCY],
        model__sample_weight=policies[s.EXPOSURE],
    )
    return model


def predict_frequency(model: Pipeline, profiles: pd.DataFrame) -> np.ndarray:
    """Expected number of claims per policy-year for each risk profile."""
    return np.asarray(model.predict(profiles[list(s.FEATURES)]), dtype=float)


def claim_probability(frequency: np.ndarray | float, years: np.ndarray | float = 1.0) -> np.ndarray:
    """P(at least one claim during `years`) under the Poisson assumption."""
    frequency = np.asarray(frequency, dtype=float)
    years = np.asarray(years, dtype=float)
    if np.any(frequency < 0) or np.any(years < 0):
        raise ValueError("Frequency and duration must be non-negative.")
    return -np.expm1(-frequency * years)


def benchmark_candidates(
    split: PortfolioSplit,
    candidates: tuple[Candidate, ...] | None = None,
) -> list[CandidateResult]:
    """Fit every candidate on the train set and score it on the held-out test set."""
    results: list[CandidateResult] = []
    for candidate in candidates or build_candidates():
        logger.info("Fitting %s", candidate.name)
        started = time.perf_counter()
        model = fit_frequency_model(candidate.build(), split.train)
        elapsed = time.perf_counter() - started

        train_pred = predict_frequency(model, split.train)
        test_pred = predict_frequency(model, split.test)
        result = CandidateResult(
            candidate=candidate,
            model=model,
            train_metrics=evaluate_frequency(
                split.train[s.FREQUENCY], train_pred, split.train[s.EXPOSURE]
            ),
            test_metrics=evaluate_frequency(
                split.test[s.FREQUENCY], test_pred, split.test[s.EXPOSURE]
            ),
            fit_seconds=elapsed,
            test_predictions=test_pred,
        )
        logger.info(
            "%-12s test deviance=%.5f  D2=%.4f  Gini=%.4f  calibration=%.4f  (%.1fs)",
            candidate.name,
            result.test_metrics.mean_poisson_deviance,
            result.test_metrics.d2_explained,
            result.test_metrics.gini,
            result.test_metrics.calibration_ratio,
            elapsed,
        )
        results.append(result)
    return results


def select_champion(results: list[CandidateResult]) -> CandidateResult:
    """The champion minimises the out-of-sample Poisson deviance (the
    quantity the models are trained to minimise)."""
    if not results:
        raise ValueError("No candidate to select from.")
    return min(results, key=lambda r: r.test_metrics.mean_poisson_deviance)
