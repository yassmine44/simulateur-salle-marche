from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from actuarial import config
from actuarial import schema as s
from actuarial.data import (
    DataValidationError,
    LargeClaimCap,
    attach_claims,
    build_severity_table,
    fingerprint,
    portfolio_summary,
    prepare_claims,
    prepare_policies,
    split_policies,
)


# --- policies -------------------------------------------------------------
def test_prepare_strips_arff_quotes(policies: pd.DataFrame) -> None:
    assert set(policies[s.VEHICLE_GAS]) == {"Diesel", "Regular"}


def test_prepare_applies_caps(raw_portfolio: pd.DataFrame) -> None:
    raw = raw_portfolio.copy()
    raw.loc[0, [s.CLAIM_COUNT, s.EXPOSURE, s.VEHICLE_AGE, s.DRIVER_AGE, s.BONUS_MALUS]] = [
        16, 2.01, 100, 100, 230,
    ]
    row = prepare_policies(raw).iloc[0]

    assert row[s.CLAIM_COUNT] == config.CLAIM_COUNT_CAP
    assert row[s.EXPOSURE] == config.EXPOSURE_CAP
    assert row[s.VEHICLE_AGE] == config.VEHICLE_AGE_CAP
    assert row[s.DRIVER_AGE] == config.DRIVER_AGE_CAP
    assert row[s.BONUS_MALUS] == config.BONUS_MALUS_CAP
    assert row[s.FREQUENCY] == pytest.approx(config.CLAIM_COUNT_CAP / config.EXPOSURE_CAP)


def test_prepare_drops_zero_exposure(raw_portfolio: pd.DataFrame) -> None:
    raw = raw_portfolio.copy()
    raw.loc[:9, s.EXPOSURE] = 0.0
    assert len(prepare_policies(raw)) == len(raw) - 10


def test_prepare_does_not_mutate_input(raw_portfolio: pd.DataFrame) -> None:
    before = raw_portfolio.copy()
    prepare_policies(raw_portfolio)
    pd.testing.assert_frame_equal(raw_portfolio, before)


def test_prepare_rejects_missing_columns(raw_portfolio: pd.DataFrame) -> None:
    with pytest.raises(DataValidationError, match=s.BONUS_MALUS):
        prepare_policies(raw_portfolio.drop(columns=[s.BONUS_MALUS]))


def test_prepare_rejects_nulls(raw_portfolio: pd.DataFrame) -> None:
    raw = raw_portfolio.copy()
    raw.loc[3, s.DENSITY] = None
    with pytest.raises((DataValidationError, ValueError)):
        prepare_policies(raw)


def test_prepare_rejects_duplicate_ids(raw_portfolio: pd.DataFrame) -> None:
    raw = raw_portfolio.copy()
    raw.loc[1, s.POLICY_ID] = raw.loc[0, s.POLICY_ID]
    with pytest.raises(DataValidationError, match="unique"):
        prepare_policies(raw)


# --- claims ---------------------------------------------------------------
def test_prepare_claims_drops_non_positive_amounts() -> None:
    raw = pd.DataFrame({s.POLICY_ID: [1.0, 2.0, 3.0], s.CLAIM_AMOUNT: [100.0, 0.0, -5.0]})
    assert prepare_claims(raw)[s.POLICY_ID].tolist() == [1]


def test_prepare_claims_rejects_missing_columns() -> None:
    with pytest.raises(DataValidationError, match=s.CLAIM_AMOUNT):
        prepare_claims(pd.DataFrame({s.POLICY_ID: [1]}))


def test_attach_claims_applies_paid_claim_convention(
    raw_portfolio: pd.DataFrame, claims: pd.DataFrame, policies: pd.DataFrame
) -> None:
    owned = claims[claims[s.POLICY_ID].isin(policies[s.POLICY_ID])]
    paid = owned.groupby(s.POLICY_ID).size()
    counts = policies.set_index(s.POLICY_ID)[s.CLAIM_COUNT]

    # Every claim count equals the number of claims with an amount (capped).
    expected = paid.reindex(counts.index, fill_value=0).clip(upper=config.CLAIM_COUNT_CAP)
    pd.testing.assert_series_equal(counts, expected, check_names=False)
    # Declared claims without amount disappear, orphan claims are ignored.
    assert counts.sum() < raw_portfolio[s.CLAIM_COUNT].sum()
    assert policies[s.CLAIM_AMOUNT].sum() == pytest.approx(owned[s.CLAIM_AMOUNT].sum())
    assert (policies[s.FREQUENCY] == policies[s.CLAIM_COUNT] / policies[s.EXPOSURE]).all()


def test_attach_claims_does_not_mutate_input(raw_portfolio, claims) -> None:
    clean = prepare_policies(raw_portfolio)
    before = clean.copy()
    attach_claims(clean, claims)
    pd.testing.assert_frame_equal(clean, before)


# --- large claims and severity table --------------------------------------
def test_large_claim_cap_preserves_total_cost() -> None:
    amounts = pd.Series([100.0, 200.0, 300.0, 400.0, 10_000.0])
    cap = LargeClaimCap.fit(amounts, quantile=0.8)
    capped = amounts.clip(upper=cap.threshold)

    assert cap.threshold == pytest.approx(np.quantile(amounts, 0.8))
    assert capped.sum() * (1 + cap.loading) == pytest.approx(amounts.sum())
    assert cap.excess_share == pytest.approx(1 - capped.sum() / amounts.sum())


def test_large_claim_cap_rejects_empty_input() -> None:
    with pytest.raises(ValueError):
        LargeClaimCap.fit(pd.Series([], dtype=float))


def test_severity_table(policies: pd.DataFrame, claims: pd.DataFrame) -> None:
    subset = policies.iloc[: len(policies) // 2]
    cap = LargeClaimCap.fit(claims[s.CLAIM_AMOUNT])
    table = build_severity_table(subset, claims, cap)

    own = claims[claims[s.POLICY_ID].isin(subset[s.POLICY_ID])]
    assert set(table[s.POLICY_ID]) == set(own[s.POLICY_ID])
    assert table[s.CLAIM_COUNT].sum() == len(own)
    assert table[s.CAPPED_AMOUNT].sum() == pytest.approx(own[s.CLAIM_AMOUNT].clip(upper=cap.threshold).sum())
    assert (table[s.SEVERITY] <= cap.threshold + 1e-9).all()
    assert (table[s.SEVERITY] > 0).all()


# --- split and lineage ----------------------------------------------------
def test_split_is_reproducible_and_stratified(policies: pd.DataFrame) -> None:
    first, second = split_policies(policies), split_policies(policies)
    pd.testing.assert_frame_equal(first.test, second.test)

    assert len(first.train) + len(first.test) == len(policies)
    assert not set(first.train[s.POLICY_ID]) & set(first.test[s.POLICY_ID])
    rate_train = (first.train[s.CLAIM_COUNT] > 0).mean()
    rate_test = (first.test[s.CLAIM_COUNT] > 0).mean()
    assert rate_train == pytest.approx(rate_test, abs=0.002)


def test_summary_and_fingerprint(policies: pd.DataFrame, claims: pd.DataFrame) -> None:
    summary = portfolio_summary(policies)
    assert summary["policies"] == len(policies)
    assert summary["claim_frequency"] == pytest.approx(
        policies[s.CLAIM_COUNT].sum() / policies[s.EXPOSURE].sum(), rel=1e-5
    )
    assert summary["incurred_amount"] == pytest.approx(policies[s.CLAIM_AMOUNT].sum(), rel=1e-6)
    assert fingerprint(policies, claims) == fingerprint(policies.copy(), claims.copy())
    assert fingerprint(policies, claims) != fingerprint(policies.iloc[1:], claims)
