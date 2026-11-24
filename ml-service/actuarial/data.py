"""Loading, cleaning and validation of the insurance portfolio."""

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


def load_policies(data_home: Path = config.DATA_HOME) -> pd.DataFrame:
    """Download (or read from cache) freMTPL2freq and return a clean portfolio."""
    logger.info("Loading freMTPL2freq (OpenML id %s)", config.FREMTPL2_FREQ_OPENML_ID)
    raw = fetch_openml(
        data_id=config.FREMTPL2_FREQ_OPENML_ID,
        as_frame=True,
        data_home=str(data_home),
        parser="pandas",
    ).frame
    return prepare_policies(raw)


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


def split_policies(
    policies: pd.DataFrame,
    test_size: float = config.TEST_SIZE,
    random_state: int = config.RANDOM_STATE,
) -> PortfolioSplit:
    """Reproducible train / test split, stratified on 'has at least one claim'.

    Only ~5 % of policies have a claim, so stratification keeps the claim rate
    identical in both sets and makes the test metrics more stable.
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
    return {
        "policies": int(len(policies)),
        "exposure_years": round(exposure, 2),
        "claims": int(claims),
        "claim_frequency": round(claims / exposure, 6) if exposure else float("nan"),
        "share_with_claim": round(float((policies[s.CLAIM_COUNT] > 0).mean()), 6),
    }


def fingerprint(policies: pd.DataFrame) -> str:
    """Content hash of a portfolio, used to trace which data trained a model."""
    row_hashes = pd.util.hash_pandas_object(policies, index=False).to_numpy(dtype=np.uint64)
    return hashlib.sha256(row_hashes.tobytes()).hexdigest()[:16]
