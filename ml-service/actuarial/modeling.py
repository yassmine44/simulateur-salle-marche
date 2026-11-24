"""Generic model benchmarking shared by the frequency and severity models.

A `Task` describes *what* is predicted (target, weight, distribution); a
`Candidate` describes *how* (a scikit-learn pipeline). Every candidate is fit
on the same train set and scored on the same held-out test set, and the
champion is the one with the lowest out-of-sample deviance, i.e. the quantity
the models are trained to minimise.
"""

from __future__ import annotations

import logging
import time
from collections.abc import Callable
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from sklearn.pipeline import Pipeline

from actuarial import schema as s
from actuarial.metrics import ModelMetrics, evaluate

logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class Task:
    name: str
    target: str
    weight: str
    deviance_power: float


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
    train_metrics: ModelMetrics
    test_metrics: ModelMetrics
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


def fit(model: Pipeline, task: Task, data: pd.DataFrame) -> Pipeline:
    model.fit(
        data[list(s.FEATURES)],
        data[task.target],
        model__sample_weight=data[task.weight],
    )
    return model


def predict(model: Pipeline, profiles: pd.DataFrame) -> np.ndarray:
    return np.asarray(model.predict(profiles[list(s.FEATURES)]), dtype=float)


def score(model: Pipeline, task: Task, data: pd.DataFrame) -> tuple[ModelMetrics, np.ndarray]:
    predictions = predict(model, data)
    metrics = evaluate(data[task.target], predictions, data[task.weight], task.deviance_power)
    return metrics, predictions


def benchmark_candidates(
    task: Task,
    train: pd.DataFrame,
    test: pd.DataFrame,
    candidates: tuple[Candidate, ...],
) -> list[CandidateResult]:
    results: list[CandidateResult] = []
    for candidate in candidates:
        logger.info("[%s] fitting %s", task.name, candidate.name)
        started = time.perf_counter()
        model = fit(candidate.build(), task, train)
        elapsed = time.perf_counter() - started

        train_metrics, _ = score(model, task, train)
        test_metrics, test_predictions = score(model, task, test)
        logger.info(
            "[%s] %-12s test deviance=%.5f  D2=%.4f  Gini=%.4f  calibration=%.4f  (%.1fs)",
            task.name,
            candidate.name,
            test_metrics.mean_deviance,
            test_metrics.d2_explained,
            test_metrics.gini,
            test_metrics.calibration_ratio,
            elapsed,
        )
        results.append(
            CandidateResult(
                candidate=candidate,
                model=model,
                train_metrics=train_metrics,
                test_metrics=test_metrics,
                fit_seconds=elapsed,
                test_predictions=test_predictions,
            )
        )
    return results


def select_champion(results: list[CandidateResult]) -> CandidateResult:
    if not results:
        raise ValueError("No candidate to select from.")
    return min(results, key=lambda r: r.test_metrics.mean_deviance)
