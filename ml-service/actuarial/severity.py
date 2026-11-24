"""Claim severity models (cahier des charges §3.7: montant des indemnisations).

Statistical framing
-------------------
Given that a claim occurs, its (capped) cost X is modelled with a Gamma
distribution whose mean depends on the risk profile:

    E[X | x] = mu(x),   Var[X | x] = phi * mu(x)^2

The Gamma family fits claim amounts because they are positive, right-skewed
and their dispersion grows with their mean. The target is the average capped
cost per claim of each policy, weighted by its number of claims.

Very large claims are capped beforehand (see `data.LargeClaimCap`); their
excess is added back to the pure premium as a portfolio-wide loading.

Candidates
----------
baseline   Single average cost per claim.
gamma_glm  Log-link Gamma GLM, the market reference for severity.
gamma_gbm  Gradient boosted trees with a Gamma loss.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from sklearn.dummy import DummyRegressor
from sklearn.ensemble import HistGradientBoostingRegressor
from sklearn.linear_model import GammaRegressor
from sklearn.pipeline import Pipeline

from actuarial import config
from actuarial import schema as s
from actuarial.metrics import GAMMA
from actuarial.modeling import Candidate, Task, predict
from actuarial.preprocessing import (
    build_glm_preprocessor,
    build_tree_preprocessor,
    tree_categorical_mask,
)

SEVERITY_TASK = Task(
    name="severity",
    target=s.SEVERITY,
    weight=s.CLAIM_COUNT,
    deviance_power=GAMMA,
)


def build_severity_candidates(random_state: int = config.RANDOM_STATE) -> tuple[Candidate, ...]:
    return (
        Candidate(
            name="baseline",
            algorithm="DummyRegressor(mean)",
            description="Coût moyen unique d'un sinistre (référence minimale).",
            build=lambda: Pipeline(
                [
                    ("preprocess", build_glm_preprocessor()),
                    ("model", DummyRegressor(strategy="mean")),
                ]
            ),
        ),
        Candidate(
            name="gamma_glm",
            algorithm="GammaRegressor (GLM, lien log)",
            description="GLM Gamma : modèle de référence du marché pour le coût des sinistres.",
            build=lambda: Pipeline(
                [
                    ("preprocess", build_glm_preprocessor()),
                    # alpha selected by 5-fold cross-validation on the train
                    # set among {0.01, 0.1, 1, 10, 100} (best mean D^2).
                    # Much stronger than for frequency: ~25x fewer
                    # observations and a far noisier target.
                    ("model", GammaRegressor(alpha=0.1, solver="newton-cholesky", max_iter=300)),
                ]
            ),
        ),
        Candidate(
            name="gamma_gbm",
            algorithm="HistGradientBoostingRegressor (loss=gamma)",
            description="Gradient boosting à perte Gamma : capte non-linéarités et interactions.",
            build=lambda: Pipeline(
                [
                    ("preprocess", build_tree_preprocessor()),
                    (
                        "model",
                        HistGradientBoostingRegressor(
                            loss="gamma",
                            learning_rate=0.03,
                            max_iter=1000,
                            max_leaf_nodes=15,
                            min_samples_leaf=300,
                            l2_regularization=1.0,
                            categorical_features=tree_categorical_mask(),
                            early_stopping=True,
                            validation_fraction=0.15,
                            n_iter_no_change=30,
                            random_state=random_state,
                        ),
                    ),
                ]
            ),
        ),
    )


def predict_severity(model: Pipeline, profiles: pd.DataFrame) -> np.ndarray:
    """Expected capped cost of one claim for each risk profile."""
    return predict(model, profiles)
