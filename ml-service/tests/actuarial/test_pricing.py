from __future__ import annotations

import json

import numpy as np
import pandas as pd
import pytest

from actuarial import schema as s
from actuarial.pricing import (
    PricingAssumptions,
    PricingEngine,
    load_pricing_engine,
    portfolio_margin,
)
from actuarial.train import ARCHETYPES, train_pipeline
from tests.actuarial.conftest import SHARE_WITHOUT_AMOUNT, TAIL_FACTOR, TAIL_PROBABILITY


class ConstantModel:
    """Stand-in for a fitted pipeline, to test pricing formulas exactly."""

    def __init__(self, value: float) -> None:
        self.value = value

    def predict(self, X: pd.DataFrame) -> np.ndarray:
        return np.full(len(X), self.value)


# --- assumptions ------------------------------------------------------------
def test_commercial_factor_formula() -> None:
    assumptions = PricingAssumptions(expense_ratio=0.25, profit_ratio=0.05, safety_loading=0.03)
    assert assumptions.commercial_factor == pytest.approx(1.03 / 0.70)


@pytest.mark.parametrize(
    "kwargs",
    [
        {"expense_ratio": 0.7, "profit_ratio": 0.3},
        {"expense_ratio": -0.1},
        {"safety_loading": -0.01},
    ],
)
def test_invalid_assumptions_are_rejected(kwargs) -> None:
    with pytest.raises(ValueError):
        PricingAssumptions(**kwargs)


# --- quotes -----------------------------------------------------------------
@pytest.fixture()
def engine() -> PricingEngine:
    return PricingEngine(
        frequency_model=ConstantModel(0.1),
        severity_model=ConstantModel(2_000.0),
        large_claim_loading=0.2,
        assumptions=PricingAssumptions(expense_ratio=0.2, profit_ratio=0.0, safety_loading=0.0),
    )


def test_quote_formulas(engine: PricingEngine, policies: pd.DataFrame) -> None:
    quote = engine.quote(policies.head(3)).iloc[0]

    assert quote["pure_premium"] == pytest.approx(0.1 * 2_000 * 1.2)
    assert quote["commercial_premium"] == pytest.approx(240 / 0.8)
    assert quote["premium_margin"] == pytest.approx(300 - 240)
    assert quote["expected_loss_ratio"] == pytest.approx(0.8)
    assert quote["claim_probability"] == pytest.approx(1 - np.exp(-0.1))


def test_quote_keeps_profile_index(engine: PricingEngine, policies: pd.DataFrame) -> None:
    profiles = policies.iloc[[5, 7]]
    assert list(engine.quote(profiles).index) == list(profiles.index)


def test_portfolio_margin_totals(engine: PricingEngine, policies: pd.DataFrame) -> None:
    quotes = engine.quote(policies)
    total = portfolio_margin(policies, quotes).iloc[0]
    by_area = portfolio_margin(policies, quotes, by=s.AREA)

    assert total["premiums"] == pytest.approx(300 * policies[s.EXPOSURE].sum())
    assert total["expected_margin"] == pytest.approx(total["premiums"] - total["expected_claims"])
    assert total["actual_claims"] == pytest.approx(policies[s.CLAIM_AMOUNT].sum())
    assert by_area["premiums"].sum() == pytest.approx(total["premiums"])
    assert by_area["policies"].sum() == len(policies)


# --- end-to-end pipeline ----------------------------------------------------
@pytest.fixture(scope="module")
def trained(policies: pd.DataFrame, claims: pd.DataFrame, tmp_path_factory):
    directory = tmp_path_factory.mktemp("artifacts")
    summary = train_pipeline(policies, claims, directory)
    return directory, summary


def test_pipeline_writes_every_artifact(trained) -> None:
    directory, _ = trained
    expected = {
        "frequency_model.joblib", "frequency_model.card.json", "frequency_report.json",
        "severity_model.joblib", "severity_model.card.json", "severity_report.json",
        "pricing_config.json", "pricing_report.json",
    }
    assert expected <= {path.name for path in directory.iterdir()}


def test_pipeline_pure_premium_matches_true_expected_cost(
    trained, policies: pd.DataFrame, truth: pd.DataFrame
) -> None:
    """Compare with the *expected* cost known by construction.

    The realised claims of a 5 000-policy test set contain only a couple of
    the x50 tail claims, so they are too volatile to validate a premium; the
    true expected cost is not.
    """
    directory, summary = trained
    quotes = load_pricing_engine(directory).quote(policies)

    true = truth.loc[policies[s.POLICY_ID]]
    tail_multiplier = 1 + TAIL_PROBABILITY * (TAIL_FACTOR - 1)
    expected_cost = (
        true["true_frequency"] * (1 - SHARE_WITHOUT_AMOUNT) * true["true_severity"] * tail_multiplier
    ).to_numpy()

    assert np.corrcoef(quotes["pure_premium"], expected_cost)[0, 1] > 0.8
    total_ratio = np.sum(quotes["pure_premium"] * policies[s.EXPOSURE]) / np.sum(
        expected_cost * policies[s.EXPOSURE]
    )
    assert total_ratio == pytest.approx(1.0, abs=0.25)
    assert summary["pure_premium_metrics"]["gini"] > 0


def test_pricing_report_contents(trained) -> None:
    directory, _ = trained
    report = json.loads((directory / "pricing_report.json").read_text(encoding="utf-8"))

    assert len(report["margin"]["by_risk_class"]) == 10
    assert [q["profile"] for q in report["example_quotes"]] == list(ARCHETYPES)
    young, experienced, _ = report["example_quotes"]
    assert young["pure_premium"] > experienced["pure_premium"]


def test_engine_reloads_identically(trained, policies: pd.DataFrame) -> None:
    directory, _ = trained
    engine = load_pricing_engine(directory)
    quotes = engine.quote(policies.head(20))
    assert (quotes["commercial_premium"] > quotes["pure_premium"]).all()

    cheaper = load_pricing_engine(
        directory, PricingAssumptions(expense_ratio=0.1, profit_ratio=0.0, safety_loading=0.0)
    ).quote(policies.head(20))
    np.testing.assert_allclose(cheaper["pure_premium"], quotes["pure_premium"])
    assert (cheaper["commercial_premium"] < quotes["commercial_premium"]).all()
