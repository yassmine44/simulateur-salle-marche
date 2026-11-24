"""Synthetic portfolio with a known data-generating process.

Because the true frequency and severity are known, tests can check that the
models learn the right signal instead of only checking that the code runs.
The generator also reproduces the defects of freMTPL2 (declared claims
without amount, claims without policy, heavy-tailed amounts).
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from actuarial import schema as s
from actuarial.data import attach_claims, prepare_claims, prepare_policies

N_POLICIES = 20_000
N_ORPHAN_CLAIMS = 5
SHARE_WITHOUT_AMOUNT = 0.10
TAIL_PROBABILITY = 0.005  # share of claims that are catastrophic
TAIL_FACTOR = 50  # cost multiplier of a catastrophic claim


def true_frequency(bonus_malus, driver_age, density) -> np.ndarray:
    """Malus and young drivers are riskier."""
    return np.exp(
        -2.6 + 0.015 * (bonus_malus - 50) + 0.6 * (driver_age < 25) + 0.08 * np.log(density)
    )


def true_severity(vehicle_power, driver_age) -> np.ndarray:
    """Powerful cars cost more per claim, older drivers slightly less."""
    return np.exp(7.0 + 0.08 * (vehicle_power - 6) - 0.006 * (driver_age - 45))


def make_raw_tables(n: int = N_POLICIES, seed: int = 0) -> tuple[pd.DataFrame, pd.DataFrame]:
    rng = np.random.default_rng(seed)
    bonus_malus = rng.choice([50, 60, 80, 100, 125], size=n, p=[0.6, 0.15, 0.1, 0.1, 0.05])
    driver_age = rng.integers(18, 85, size=n)
    density = rng.lognormal(mean=6.0, sigma=1.5, size=n).clip(1, 27_000).astype(int)
    vehicle_power = rng.integers(4, 12, size=n)
    exposure = rng.uniform(0.05, 1.0, size=n).round(2)

    frequency = true_frequency(bonus_malus, driver_age, density)
    severity = true_severity(vehicle_power, driver_age)
    claim_counts = rng.poisson(frequency * exposure)
    policy_ids = np.arange(1, n + 1)

    policies = pd.DataFrame(
        {
            s.POLICY_ID: policy_ids.astype(float),
            s.CLAIM_COUNT: claim_counts,
            s.EXPOSURE: exposure,
            s.AREA: rng.choice(list("ABCDEF"), size=n),
            s.VEHICLE_POWER: vehicle_power,
            s.VEHICLE_AGE: rng.integers(0, 20, size=n),
            s.DRIVER_AGE: driver_age,
            s.BONUS_MALUS: bonus_malus,
            s.VEHICLE_BRAND: rng.choice(["B1", "B2", "B12"], size=n),
            s.VEHICLE_GAS: rng.choice(["'Diesel'", "'Regular'"], size=n),
            s.DENSITY: density,
            s.REGION: rng.choice(["R11", "R24", "R82"], size=n),
            "true_frequency": frequency,
            "true_severity": severity,
        }
    )

    # One row per claim: Gamma amounts (shape 2) plus a heavy tail.
    owners = np.repeat(policy_ids, claim_counts)
    means = np.repeat(severity, claim_counts)
    amounts = rng.gamma(shape=2.0, scale=means / 2.0)
    amounts[rng.uniform(size=amounts.size) < TAIL_PROBABILITY] *= TAIL_FACTOR
    claims = pd.DataFrame({s.POLICY_ID: owners, s.CLAIM_AMOUNT: amounts.round(2)})

    # Defect 1: some declared claims have no recorded amount.
    with_claims = np.unique(owners)
    no_amount = rng.choice(with_claims, size=int(SHARE_WITHOUT_AMOUNT * with_claims.size), replace=False)
    claims = claims[~claims[s.POLICY_ID].isin(no_amount)]
    # Defect 2: a few claims belong to no policy.
    orphans = pd.DataFrame(
        {s.POLICY_ID: n + 1 + np.arange(N_ORPHAN_CLAIMS), s.CLAIM_AMOUNT: 1000.0}
    )
    return policies, pd.concat([claims, orphans], ignore_index=True)


@pytest.fixture(scope="session")
def raw_tables() -> tuple[pd.DataFrame, pd.DataFrame]:
    return make_raw_tables()


@pytest.fixture(scope="session")
def raw_portfolio(raw_tables) -> pd.DataFrame:
    return raw_tables[0]


@pytest.fixture(scope="session")
def raw_claims(raw_tables) -> pd.DataFrame:
    return raw_tables[1]


@pytest.fixture(scope="session")
def claims(raw_claims: pd.DataFrame) -> pd.DataFrame:
    return prepare_claims(raw_claims)


@pytest.fixture(scope="session")
def policies(raw_portfolio: pd.DataFrame, claims: pd.DataFrame) -> pd.DataFrame:
    """Clean policies following the paid-claim convention."""
    return attach_claims(prepare_policies(raw_portfolio), claims)


@pytest.fixture(scope="session")
def trained(policies: pd.DataFrame, claims: pd.DataFrame, tmp_path_factory):
    """Full training pipeline run once on the synthetic portfolio."""
    from actuarial.train import train_pipeline

    directory = tmp_path_factory.mktemp("artifacts")
    # Small explanation samples keep the suite fast; the method is identical.
    return directory, train_pipeline(
        policies, claims, directory, background_size=50, explain_sample_size=40
    )


@pytest.fixture(scope="session")
def truth(raw_portfolio: pd.DataFrame) -> pd.DataFrame:
    """True frequency and severity indexed by policy id."""
    return raw_portfolio.set_index(raw_portfolio[s.POLICY_ID].astype("int64"))[
        ["true_frequency", "true_severity"]
    ]
