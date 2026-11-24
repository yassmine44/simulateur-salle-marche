"""Loading, cleaning and validation of the insurance portfolio.

Two raw tables are combined:

* policies (freMTPL2freq): one row per contract, with its exposure and
  rating factors;
* claims (freMTPL2sev): one row per claim, with its amount.

Paid-claim convention
---------------------
About 9 000 contracts declare claims that have no recorded amount (a known
defect of freMTPL2). Counting them in the frequency while the severity only
sees amounts would overstate the pure premium (frequency x severity) by about
a quarter. The claim count of a policy is therefore recomputed as its number
of claims *with an amount*: the frequency model predicts paid claims, the
severity model their cost, and both are consistent by construction.
"""

from __future__ import annotations

import hashlib
import logging
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.datasets import fetch_openml
from sklearn.model_selection import train_test_split

from actuarial import config
from actuarial import schema as s

logger = logging.getLogger(__name__)


class DataValidationError(ValueError):
    """Raised when a portfolio does not satisfy the expected contract."""


@dataclass(frozen=True)
class PortfolioSplit:
    train: pd.DataFrame
    test: pd.DataFrame


# ----------------------------------------------------------------------------
# Loading
# ----------------------------------------------------------------------------
def _fetch(openml_id: int, data_home: Path) -> pd.DataFrame:
    return fetch_openml(
        data_id=openml_id, as_frame=True, data_home=str(data_home), parser="pandas"
    ).frame


def load_policies(data_home: Path = config.DATA_HOME) -> pd.DataFrame:
    """freMTPL2freq, cleaned, with the claim counts as declared in the file."""
    logger.info("Loading freMTPL2freq (OpenML id %s)", config.FREMTPL2_FREQ_OPENML_ID)
    return prepare_policies(_fetch(config.FREMTPL2_FREQ_OPENML_ID, data_home))


def load_claims(data_home: Path = config.DATA_HOME) -> pd.DataFrame:
    """freMTPL2sev, cleaned: one row per claim with a positive amount."""
    logger.info("Loading freMTPL2sev (OpenML id %s)", config.FREMTPL2_SEV_OPENML_ID)
    return prepare_claims(_fetch(config.FREMTPL2_SEV_OPENML_ID, data_home))


def load_portfolio(data_home: Path = config.DATA_HOME) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Policies with paid-claim counts and amounts, plus the claims they own."""
    policies = load_policies(data_home)
    claims = load_claims(data_home)
    return attach_claims(policies, claims), claims[claims[s.POLICY_ID].isin(policies[s.POLICY_ID])]


# ----------------------------------------------------------------------------
# Cleaning
# ----------------------------------------------------------------------------
def prepare_policies(raw: pd.DataFrame) -> pd.DataFrame:
    """Clean a raw policy table and enforce the feature contract.

    The function is pure (the input frame is not modified) so that it can be
    reused on any portfolio uploaded later by the platform administrator.
    """
    missing = s.REQUIRED_POLICY_COLUMNS - set(raw.columns)
    if missing:
        raise DataValidationError(f"Missing columns: {sorted(missing)}")

    df = raw.loc[:, sorted(s.REQUIRED_POLICY_COLUMNS)].copy()

    # OpenML stores some ARFF nominal values with their quotes ("'Diesel'").
    for column in s.CATEGORICAL_FEATURES:
        df[column] = df[column].astype(str).str.strip().str.strip("'\"")

    for column in (s.POLICY_ID, s.CLAIM_COUNT, *s.NUMERIC_FEATURES):
        df[column] = pd.to_numeric(df[column], errors="raise").astype("int64")
    df[s.EXPOSURE] = pd.to_numeric(df[s.EXPOSURE], errors="raise").astype("float64")

    if df.isna().any().any():
        bad = df.columns[df.isna().any()].tolist()
        raise DataValidationError(f"Null values found in columns: {bad}")
    if not df[s.POLICY_ID].is_unique:
        raise DataValidationError("Policy identifiers must be unique.")
    if (df[s.CLAIM_COUNT] < 0).any():
        raise DataValidationError("Claim counts must be non-negative.")

    n_before = len(df)
    df = df[df[s.EXPOSURE] > 0]
    if len(df) < n_before:
        logger.warning("Dropped %d policies with non-positive exposure", n_before - len(df))

    df[s.CLAIM_COUNT] = df[s.CLAIM_COUNT].clip(upper=config.CLAIM_COUNT_CAP)
    df[s.EXPOSURE] = df[s.EXPOSURE].clip(upper=config.EXPOSURE_CAP)
    df[s.VEHICLE_AGE] = df[s.VEHICLE_AGE].clip(upper=config.VEHICLE_AGE_CAP)
    df[s.DRIVER_AGE] = df[s.DRIVER_AGE].clip(upper=config.DRIVER_AGE_CAP)
    df[s.BONUS_MALUS] = df[s.BONUS_MALUS].clip(upper=config.BONUS_MALUS_CAP)

    df[s.FREQUENCY] = df[s.CLAIM_COUNT] / df[s.EXPOSURE]
    return df.reset_index(drop=True)


def prepare_claims(raw: pd.DataFrame) -> pd.DataFrame:
    missing = s.REQUIRED_CLAIM_COLUMNS - set(raw.columns)
    if missing:
        raise DataValidationError(f"Missing columns: {sorted(missing)}")

    df = raw.loc[:, sorted(s.REQUIRED_CLAIM_COLUMNS)].copy()
    df[s.POLICY_ID] = pd.to_numeric(df[s.POLICY_ID], errors="raise").astype("int64")
    df[s.CLAIM_AMOUNT] = pd.to_numeric(df[s.CLAIM_AMOUNT], errors="raise").astype("float64")
    if df.isna().any().any():
        raise DataValidationError("Null values found in the claims table.")

    non_positive = df[s.CLAIM_AMOUNT] <= 0
    if non_positive.any():
        logger.warning("Dropped %d claims with a non-positive amount", int(non_positive.sum()))
    return df[~non_positive].reset_index(drop=True)


def attach_claims(policies: pd.DataFrame, claims: pd.DataFrame) -> pd.DataFrame:
    """Recompute claim counts as paid claims and attach the incurred amounts."""
    orphans = ~claims[s.POLICY_ID].isin(policies[s.POLICY_ID])
    if orphans.any():
        logger.warning("Ignored %d claims that match no policy", int(orphans.sum()))

    per_policy = claims[~orphans].groupby(s.POLICY_ID)[s.CLAIM_AMOUNT].agg(["count", "sum"])
    df = policies.copy()
    declared = df[s.CLAIM_COUNT].sum()

    paid_count = df[s.POLICY_ID].map(per_policy["count"]).fillna(0).astype("int64")
    df[s.CLAIM_COUNT] = paid_count.clip(upper=config.CLAIM_COUNT_CAP)
    df[s.CLAIM_AMOUNT] = df[s.POLICY_ID].map(per_policy["sum"]).fillna(0.0)
    df[s.FREQUENCY] = df[s.CLAIM_COUNT] / df[s.EXPOSURE]

    logger.info(
        "Paid-claim convention: %d declared claims -> %d paid claims",
        declared,
        df[s.CLAIM_COUNT].sum(),
    )
    return df


# ----------------------------------------------------------------------------
# Large claims and severity table
# ----------------------------------------------------------------------------
@dataclass(frozen=True)
class LargeClaimCap:
    """Capping threshold and the loading that redistributes the excess.

    loading = sum(max(X - threshold, 0)) / sum(min(X, threshold))

    so that  E[uncapped cost] = E[capped cost] x (1 + loading)  on the
    portfolio the cap was fitted on.
    """

    quantile: float
    threshold: float
    loading: float
    excess_share: float  # share of the total cost removed by the cap

    @classmethod
    def fit(cls, claim_amounts: pd.Series, quantile: float = config.LARGE_CLAIM_QUANTILE) -> "LargeClaimCap":
        amounts = np.asarray(claim_amounts, dtype=float)
        if amounts.size == 0:
            raise ValueError("Cannot fit a large-claim cap on an empty claim set.")
        threshold = float(np.quantile(amounts, quantile))
        capped = np.minimum(amounts, threshold)
        excess = amounts - capped
        return cls(
            quantile=quantile,
            threshold=round(threshold, 2),
            loading=float(excess.sum() / capped.sum()),
            excess_share=float(excess.sum() / amounts.sum()),
        )

    def to_dict(self) -> dict[str, float]:
        return {key: round(value, 6) for key, value in self.__dict__.items()}


def build_severity_table(
    policies: pd.DataFrame,
    claims: pd.DataFrame,
    cap: LargeClaimCap,
) -> pd.DataFrame:
    """One row per policy with at least one paid claim.

    Target: average capped cost per claim. Weight: number of claims, so that
    a policy with three claims counts three times as much as a single claim.
    """
    own_claims = claims[claims[s.POLICY_ID].isin(policies[s.POLICY_ID])].copy()
    own_claims[s.CAPPED_AMOUNT] = own_claims[s.CLAIM_AMOUNT].clip(upper=cap.threshold)
    per_policy = own_claims.groupby(s.POLICY_ID).agg(
        n_claims=(s.CAPPED_AMOUNT, "size"),
        capped=(s.CAPPED_AMOUNT, "sum"),
    )

    table = policies.loc[
        policies[s.POLICY_ID].isin(per_policy.index), [s.POLICY_ID, *s.FEATURES]
    ].copy()
    table[s.CLAIM_COUNT] = table[s.POLICY_ID].map(per_policy["n_claims"]).astype("int64")
    table[s.CAPPED_AMOUNT] = table[s.POLICY_ID].map(per_policy["capped"])
    table[s.SEVERITY] = table[s.CAPPED_AMOUNT] / table[s.CLAIM_COUNT]
    return table.reset_index(drop=True)


# ----------------------------------------------------------------------------
# Split and lineage
# ----------------------------------------------------------------------------
def split_policies(
    policies: pd.DataFrame,
    test_size: float = config.TEST_SIZE,
    random_state: int = config.RANDOM_STATE,
) -> PortfolioSplit:
    """Reproducible train / test split, stratified on 'has at least one claim'.

    The split is made on policies, and every downstream table (claims,
    severity) is derived from it, so no policy ever leaks from train to test.
    """
    has_claim = (policies[s.CLAIM_COUNT] > 0).astype(int)
    train, test = train_test_split(
        policies,
        test_size=test_size,
        random_state=random_state,
        stratify=has_claim,
    )
    return PortfolioSplit(train=train.reset_index(drop=True), test=test.reset_index(drop=True))


def portfolio_summary(policies: pd.DataFrame) -> dict[str, float]:
    """Key figures of a portfolio, stored in the model card."""
    exposure = float(policies[s.EXPOSURE].sum())
    claims = float(policies[s.CLAIM_COUNT].sum())
    summary = {
        "policies": int(len(policies)),
        "exposure_years": round(exposure, 2),
        "claims": int(claims),
        "claim_frequency": round(claims / exposure, 6) if exposure else float("nan"),
        "share_with_claim": round(float((policies[s.CLAIM_COUNT] > 0).mean()), 6),
    }
    if s.CLAIM_AMOUNT in policies:
        total = float(policies[s.CLAIM_AMOUNT].sum())
        summary["incurred_amount"] = round(total, 2)
        summary["loss_cost_per_year"] = round(total / exposure, 4) if exposure else float("nan")
    return summary


def fingerprint(*frames: pd.DataFrame) -> str:
    """Content hash of the training data, used to trace which data built a model."""
    digest = hashlib.sha256()
    for frame in frames:
        row_hashes = pd.util.hash_pandas_object(frame, index=False).to_numpy(dtype=np.uint64)
        digest.update(row_hashes.tobytes())
    return digest.hexdigest()[:16]
