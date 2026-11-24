"""Synthetic portfolio with a known data-generating process.

Because the true frequency is known, tests can check that the models learn
the right signal instead of only checking that the code runs.
"""

from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from actuarial import schema as s
from actuarial.data import prepare_policies


def make_raw_portfolio(n: int = 20_000, seed: int = 0) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    bonus_malus = rng.choice([50, 60, 80, 100, 125], size=n, p=[0.6, 0.15, 0.1, 0.1, 0.05])
    driver_age = rng.integers(18, 85, size=n)
    density = rng.lognormal(mean=6.0, sigma=1.5, size=n).clip(1, 27_000).astype(int)
    area = rng.choice(list("ABCDEF"), size=n)
    exposure = rng.uniform(0.05, 1.0, size=n).round(2)

    # True annual frequency: malus and young drivers are riskier.
    true_frequency = np.exp(
        -2.6
        + 0.015 * (bonus_malus - 50)
        + 0.6 * (driver_age < 25)
        + 0.08 * np.log(density)
    )
    claims = rng.poisson(true_frequency * exposure)

    return pd.DataFrame(
        {
            s.POLICY_ID: np.arange(1, n + 1, dtype=float),
            s.CLAIM_COUNT: claims,
            s.EXPOSURE: exposure,
            s.AREA: area,
            s.VEHICLE_POWER: rng.integers(4, 12, size=n),
            s.VEHICLE_AGE: rng.integers(0, 20, size=n),
            s.DRIVER_AGE: driver_age,
            s.BONUS_MALUS: bonus_malus,
            s.VEHICLE_BRAND: rng.choice(["B1", "B2", "B12"], size=n),
            s.VEHICLE_GAS: rng.choice(["'Diesel'", "'Regular'"], size=n),
            s.DENSITY: density,
            s.REGION: rng.choice(["R11", "R24", "R82"], size=n),
            "true_frequency": true_frequency,
        }
    )


@pytest.fixture(scope="session")
def raw_portfolio() -> pd.DataFrame:
    return make_raw_portfolio()


@pytest.fixture(scope="session")
def policies(raw_portfolio: pd.DataFrame) -> pd.DataFrame:
    return prepare_policies(raw_portfolio)
