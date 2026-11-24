from __future__ import annotations

import pandas as pd
import pytest

from actuarial import config
from actuarial import schema as s
from actuarial.data import (
    DataValidationError,
    fingerprint,
    portfolio_summary,
    prepare_policies,
    split_policies,
)


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
    raw[s.AREA] = raw[s.AREA].astype(object)
    raw.loc[3, s.DENSITY] = None
    with pytest.raises((DataValidationError, ValueError)):
        prepare_policies(raw)


def test_prepare_rejects_duplicate_ids(raw_portfolio: pd.DataFrame) -> None:
    raw = raw_portfolio.copy()
    raw.loc[1, s.POLICY_ID] = raw.loc[0, s.POLICY_ID]
    with pytest.raises(DataValidationError, match="unique"):
        prepare_policies(raw)


def test_split_is_reproducible_and_stratified(policies: pd.DataFrame) -> None:
    first, second = split_policies(policies), split_policies(policies)
    pd.testing.assert_frame_equal(first.test, second.test)

    assert len(first.train) + len(first.test) == len(policies)
    rate_train = (first.train[s.CLAIM_COUNT] > 0).mean()
    rate_test = (first.test[s.CLAIM_COUNT] > 0).mean()
    assert rate_train == pytest.approx(rate_test, abs=0.002)


def test_summary_and_fingerprint(policies: pd.DataFrame) -> None:
    summary = portfolio_summary(policies)
    assert summary["policies"] == len(policies)
    assert summary["claim_frequency"] == pytest.approx(
        policies[s.CLAIM_COUNT].sum() / policies[s.EXPOSURE].sum(), rel=1e-5
    )
    assert fingerprint(policies) == fingerprint(policies.copy())
    assert fingerprint(policies) != fingerprint(policies.iloc[1:])
