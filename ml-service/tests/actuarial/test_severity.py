from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from actuarial import schema as s
from actuarial.data import LargeClaimCap, build_severity_table, split_policies
from actuarial.modeling import benchmark_candidates, select_champion
from actuarial.severity import SEVERITY_TASK, build_severity_candidates, predict_severity


@pytest.fixture(scope="module")
def tables(policies: pd.DataFrame, claims: pd.DataFrame):
    split = split_policies(policies)
    train_claims = claims[claims[s.POLICY_ID].isin(split.train[s.POLICY_ID])]
    cap = LargeClaimCap.fit(train_claims[s.CLAIM_AMOUNT])
    return (
        build_severity_table(split.train, claims, cap),
        build_severity_table(split.test, claims, cap),
        cap,
    )


@pytest.fixture(scope="module")
def results(tables):
    train, test, _ = tables
    return benchmark_candidates(SEVERITY_TASK, train, test, build_severity_candidates())


def _by_name(results, name: str):
    return next(r for r in results if r.candidate.name == name)


def test_cap_is_fitted_on_train_claims_only(tables, claims, policies) -> None:
    _, test, cap = tables
    assert cap.loading > 0
    assert 0 < cap.excess_share < 1
    # Test severities are capped with the train threshold (no leakage).
    assert (test[s.SEVERITY] <= cap.threshold + 1e-9).all()


def test_every_candidate_is_benchmarked(results) -> None:
    assert [r.candidate.name for r in results] == ["baseline", "gamma_glm", "gamma_gbm"]


def test_glm_beats_baseline_out_of_sample(results) -> None:
    baseline = _by_name(results, "baseline").test_metrics
    glm = _by_name(results, "gamma_glm").test_metrics
    assert glm.mean_deviance < baseline.mean_deviance
    assert glm.gini > 0


@pytest.mark.parametrize("name", ["baseline", "gamma_glm", "gamma_gbm"])
def test_models_are_globally_calibrated(results, name: str) -> None:
    assert _by_name(results, name).test_metrics.calibration_ratio == pytest.approx(1.0, abs=0.1)


def test_glm_recovers_true_severity_ranking(results, tables, truth: pd.DataFrame) -> None:
    _, test, _ = tables
    true_severity = test[s.POLICY_ID].map(truth["true_severity"])
    predicted = _by_name(results, "gamma_glm").test_predictions
    assert np.corrcoef(predicted, true_severity)[0, 1] > 0.8


def test_vehicle_power_increases_severity(results, policies: pd.DataFrame) -> None:
    model = _by_name(results, "gamma_glm").model
    profile = policies.iloc[[0, 0]].copy()
    profile[s.VEHICLE_POWER] = [4, 11]
    low, high = predict_severity(model, profile)
    assert high > low


def test_predictions_are_positive(results, tables) -> None:
    _, test, _ = tables
    for result in results:
        assert (predict_severity(result.model, test) > 0).all()


def test_champion_has_lowest_test_deviance(results) -> None:
    champion = select_champion(results)
    assert champion.test_metrics.mean_deviance == min(r.test_metrics.mean_deviance for r in results)
