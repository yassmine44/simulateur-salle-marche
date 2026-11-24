"""Claim frequency models (cahier des charges §3.7: survenance des sinistres).

Statistical framing
-------------------
For a policy i observed during an exposure e_i (in years), the number of
paid claims N_i is modelled as

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

import numpy as np
import pandas as pd
from sklearn.dummy import DummyRegressor
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.linear_model import PoissonRegressor
from sklearn.pipeline import Pipeline

from actuarial import config
from actuarial import schema as s
from actuarial.metrics import POISSON
from actuarial.modeling import Candidate, Task, predict
from actuarial.preprocessing import (
    build_glm_preprocessor,
    build_tree_preprocessor,
    tree_categorical_mask,
)

FREQUENCY_TASK = Task(
    name="frequency",
    target=s.FREQUENCY,
    weight=s.EXPOSURE,
    deviance_power=POISSON,
)


def build_frequency_candidates(random_state: int = config.RANDOM_STATE) -> tuple[Candidate, ...]:
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


def predict_frequency(model: Pipeline, profiles: pd.DataFrame) -> np.ndarray:
    """Expected number of paid claims per policy-year for each risk profile."""
    return predict(model, profiles)


def claim_probability(frequency: np.ndarray | float, years: np.ndarray | float = 1.0) -> np.ndarray:
    """P(at least one claim during `years`) under the Poisson assumption."""
    frequency = np.asarray(frequency, dtype=float)
    years = np.asarray(years, dtype=float)
    if np.any(frequency < 0) or np.any(years < 0):
        raise ValueError("Frequency and duration must be non-negative.")
    return -np.expm1(-frequency * years)
