from __future__ import annotations

import json

import numpy as np
import pandas as pd
import pytest
from sklearn.dummy import DummyRegressor
from sklearn.pipeline import Pipeline

from actuarial import schema as s
from actuarial.explainability import (
    ShapleyExplainer,
    dependence,
    global_importance,
    is_constant_model,
    load_premium_explainer,
    sample_background,
)
from actuarial.train import ARCHETYPES

# Coefficients of an additive log-linear model used as analytic ground truth.
BM_COEF, AREA_E_COEF, LOG_DENSITY_COEF = 0.02, 0.5, 0.1


def additive_model(frame: pd.DataFrame) -> np.ndarray:
    return np.exp(
        BM_COEF * frame[s.BONUS_MALUS].astype(float)
        + AREA_E_COEF * (frame[s.AREA] == "E")
        + LOG_DENSITY_COEF * np.log(frame[s.DENSITY].astype(float))
    ).to_numpy()


def interaction_model(frame: pd.DataFrame) -> np.ndarray:
    bm = frame[s.BONUS_MALUS].astype(float)
    age = frame[s.DRIVER_AGE].astype(float)
    return np.exp(0.0002 * bm * age + 0.3 * (frame[s.VEHICLE_GAS] == "Diesel")).to_numpy()


@pytest.fixture(scope="module")
def background(policies: pd.DataFrame) -> pd.DataFrame:
    return sample_background(policies, size=50)


@pytest.fixture(scope="module")
def profiles(policies: pd.DataFrame) -> pd.DataFrame:
    return policies.sample(n=8, random_state=3)


# --- Shapley engine -----------------------------------------------------------
def test_matches_analytic_solution_of_additive_model(background, profiles) -> None:
    """For f = exp(sum_j g_j(x_j)), interventional Shapley values are exactly
    phi_j = g_j(x_j) - mean_b g_j(b_j), and zero for unused features."""
    phi = ShapleyExplainer(additive_model, background).shapley_values(profiles)
    column = {feature: j for j, feature in enumerate(s.FEATURES)}

    expected_bm = BM_COEF * (profiles[s.BONUS_MALUS] - background[s.BONUS_MALUS].mean())
    expected_area = AREA_E_COEF * ((profiles[s.AREA] == "E") - (background[s.AREA] == "E").mean())
    expected_density = LOG_DENSITY_COEF * (
        np.log(profiles[s.DENSITY]) - np.log(background[s.DENSITY]).mean()
    )
    np.testing.assert_allclose(phi[:, column[s.BONUS_MALUS]], expected_bm, atol=1e-10)
    np.testing.assert_allclose(phi[:, column[s.AREA]], expected_area, atol=1e-10)
    np.testing.assert_allclose(phi[:, column[s.DENSITY]], expected_density, atol=1e-10)

    unused = [column[f] for f in s.FEATURES if f not in {s.BONUS_MALUS, s.AREA, s.DENSITY}]
    np.testing.assert_allclose(phi[:, unused], 0.0, atol=1e-12)


@pytest.mark.parametrize("model", [additive_model, interaction_model])
def test_efficiency_property(model, background, profiles) -> None:
    explainer = ShapleyExplainer(model, background)
    phi = explainer.shapley_values(profiles)
    np.testing.assert_allclose(
        explainer.base_value + phi.sum(axis=1), np.log(model(profiles)), atol=1e-10
    )


def test_interaction_is_shared_between_both_features(background) -> None:
    """Symmetry: the BM x age interaction is split between the two features."""
    profile = background.iloc[[0]].copy()
    profile[[s.BONUS_MALUS, s.DRIVER_AGE]] = [150, 90]
    phi = ShapleyExplainer(interaction_model, background).shapley_values(profile)[0]
    column = {feature: j for j, feature in enumerate(s.FEATURES)}
    assert phi[column[s.BONUS_MALUS]] > 0 and phi[column[s.DRIVER_AGE]] > 0


def test_constant_model_has_zero_contributions(background, profiles) -> None:
    pipeline = Pipeline([("model", DummyRegressor())])
    assert is_constant_model(pipeline)
    assert is_constant_model(DummyRegressor())
    explainer = ShapleyExplainer(lambda X: np.full(len(X), 3.0), background, constant=True)
    np.testing.assert_array_equal(explainer.shapley_values(profiles), 0.0)


def test_rejects_non_positive_predictions(background) -> None:
    with pytest.raises(ValueError, match="positive"):
        ShapleyExplainer(lambda X: np.zeros(len(X)), background)


def test_rejects_too_many_features(background) -> None:
    features = tuple(f"x{i}" for i in range(13))
    wide = pd.DataFrame(np.ones((5, 13)), columns=features)
    with pytest.raises(ValueError, match="12 features"):
        ShapleyExplainer(lambda X: np.ones(len(X)), wide, features=features)


def test_background_sample(policies: pd.DataFrame) -> None:
    sample = sample_background(policies, size=30)
    assert list(sample.columns) == list(s.FEATURES)
    assert len(sample) == 30
    pd.testing.assert_frame_equal(sample, sample_background(policies, size=30))


# --- global helpers -------------------------------------------------------------
def test_global_importance_and_dependence(background, profiles) -> None:
    phi = ShapleyExplainer(additive_model, background).shapley_values(profiles)
    importance = global_importance(phi)
    assert sum(row["share"] for row in importance) == pytest.approx(1.0, abs=1e-5)
    assert {row["feature"] for row in importance[:3]} == {s.BONUS_MALUS, s.AREA, s.DENSITY}

    curves = dependence(profiles, phi)
    assert set(curves) == set(s.FEATURES)
    assert sum(point["count"] for point in curves[s.AREA]) == len(profiles)


# --- premium explanations on the trained pipeline ------------------------------
@pytest.fixture(scope="module")
def premium_explainer(trained):
    directory, _ = trained
    return load_premium_explainer(directory)


def test_premium_is_reconstructed_from_relativities(premium_explainer, policies) -> None:
    for explanation in premium_explainer.explain(policies.head(5)):
        relativities = np.prod([c["relativity"] for c in explanation.contributions()])
        assert explanation.base_premium * relativities == pytest.approx(
            explanation.pure_premium, rel=1e-4
        )


def test_malus_raises_the_premium_of_the_young_driver(premium_explainer) -> None:
    archetypes = pd.DataFrame.from_dict(ARCHETYPES, orient="index")
    young = premium_explainer.explain(archetypes.iloc[[0]])[0]
    bonus_malus = next(c for c in young.contributions() if c["feature"] == s.BONUS_MALUS)
    assert bonus_malus["direction"] == "hausse"
    assert bonus_malus["relativity"] > 1


def test_comparison_explains_the_premium_ratio(premium_explainer, policies) -> None:
    comparison = premium_explainer.compare(policies.iloc[[0]], policies.iloc[[1]])
    total = sum(row["phi_difference"] for row in comparison["contributions"])
    assert np.exp(total) == pytest.approx(comparison["premium_ratio"], rel=1e-4)


def test_explainability_report(trained) -> None:
    directory, _ = trained
    report = json.loads((directory / "explainability_report.json").read_text(encoding="utf-8"))

    premium_importance = report["global_importance"]["premium"]
    # The synthetic frequency is driven first by the bonus-malus.
    assert premium_importance[0]["feature"] == s.BONUS_MALUS
    assert [e["profile_name"] for e in report["examples"]] == list(ARCHETYPES)
    assert report["comparison"]["premium_ratio"] > 1
