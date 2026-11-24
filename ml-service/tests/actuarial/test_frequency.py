from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from actuarial import schema as s
from actuarial.data import split_policies
from actuarial.frequency import (
    benchmark_candidates,
    claim_probability,
    predict_frequency,
    select_champion,
)
from actuarial.registry import ModelCard, load_model, save_model


@pytest.fixture(scope="module")
def results(policies: pd.DataFrame):
    return benchmark_candidates(split_policies(policies))


def _by_name(results, name: str):
    return next(r for r in results if r.candidate.name == name)


def test_every_candidate_is_benchmarked(results) -> None:
    assert [r.candidate.name for r in results] == ["baseline", "poisson_glm", "poisson_gbm"]


@pytest.mark.parametrize("name", ["poisson_glm", "poisson_gbm"])
def test_models_beat_baseline_out_of_sample(results, name: str) -> None:
    baseline = _by_name(results, "baseline").test_metrics
    model = _by_name(results, name).test_metrics
    assert model.mean_poisson_deviance < baseline.mean_poisson_deviance
    assert model.gini > 0.1


@pytest.mark.parametrize("name", ["baseline", "poisson_glm", "poisson_gbm"])
def test_models_are_globally_calibrated(results, name: str) -> None:
    assert _by_name(results, name).test_metrics.calibration_ratio == pytest.approx(1.0, abs=0.1)


def test_glm_recovers_true_frequency(
    results, policies: pd.DataFrame, raw_portfolio: pd.DataFrame
) -> None:
    test = split_policies(policies).test
    true_frequency = test[s.POLICY_ID].map(
        raw_portfolio.set_index(raw_portfolio[s.POLICY_ID].astype("int64"))["true_frequency"]
    )
    predicted = _by_name(results, "poisson_glm").test_predictions
    correlation = np.corrcoef(predicted, true_frequency)[0, 1]
    # Not 1.0: the true young-driver effect is a step at 25 years while the GLM
    # bins driver age into deciles whose edges do not fall exactly on 25.
    assert correlation > 0.85


def test_malus_increases_predicted_frequency(results, policies: pd.DataFrame) -> None:
    model = _by_name(results, "poisson_glm").model
    profile = policies.iloc[[0, 0]].copy()
    profile[s.BONUS_MALUS] = [50, 125]
    low, high = predict_frequency(model, profile)
    assert high > low * 2


def test_champion_has_lowest_test_deviance(results) -> None:
    champion = select_champion(results)
    assert champion.test_metrics.mean_poisson_deviance == min(
        r.test_metrics.mean_poisson_deviance for r in results
    )


def test_select_champion_rejects_empty_list() -> None:
    with pytest.raises(ValueError):
        select_champion([])


def test_claim_probability() -> None:
    assert claim_probability(0.0) == 0.0
    assert claim_probability(0.1) == pytest.approx(1 - np.exp(-0.1))
    assert claim_probability(0.1, years=2) > claim_probability(0.1, years=1)
    assert np.all((claim_probability(np.array([0.05, 1.0, 10.0])) < 1))
    with pytest.raises(ValueError):
        claim_probability(-0.1)


def test_registry_round_trip(results, policies: pd.DataFrame, tmp_path) -> None:
    champion = select_champion(results)
    card = ModelCard(
        name="frequency_test",
        task="claim_frequency",
        algorithm=champion.candidate.algorithm,
        target=s.FREQUENCY,
        weight=s.EXPOSURE,
        features=list(s.FEATURES),
        data_source="synthetic",
        data_fingerprint="0",
        train_summary={},
        test_summary={},
        metrics=champion.test_metrics.to_dict(),
        candidates=[r.summary() for r in results],
    )
    save_model(champion.model, card, tmp_path)
    model, loaded_card = load_model("frequency_test", tmp_path)

    sample = policies.head(50)
    np.testing.assert_allclose(
        predict_frequency(model, sample), predict_frequency(champion.model, sample)
    )
    assert loaded_card["metrics"] == card.metrics


def test_load_missing_model_has_actionable_message(tmp_path) -> None:
    with pytest.raises(FileNotFoundError, match="train_frequency"):
        load_model("missing", tmp_path)
