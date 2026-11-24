"""Premium computation and premium margin (cahier des charges §3.7).

From model outputs to a tariff
------------------------------
    lambda(x)            expected paid claims per year       (frequency model)
    mu(x)                expected capped cost per claim       (severity model)
    L                    large-claim loading                  (data.LargeClaimCap)

    pure premium         P0 = lambda(x) * mu(x) * (1 + L)
    commercial premium   P  = P0 * (1 + safety) / (1 - expense_ratio - profit_ratio)
    premium margin       M  = P - P0

The pure premium is the expected cost of claims (the actuarially fair
price). The commercial premium is what the policyholder pays: it adds a
safety margin and is grossed up so that expenses and profit represent fixed
shares of it. The premium margin is the difference between the premiums
collected and the expected claims, which is exactly the profitability
indicator requested by the specification.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.pipeline import Pipeline

from actuarial import config
from actuarial import schema as s
from actuarial.frequency import claim_probability, predict_frequency
from actuarial.registry import load_model
from actuarial.severity import predict_severity

FREQUENCY_MODEL_NAME = "frequency_model"
SEVERITY_MODEL_NAME = "severity_model"
PRICING_CONFIG_NAME = "pricing_config.json"


@dataclass(frozen=True)
class PricingAssumptions:
    expense_ratio: float = config.EXPENSE_RATIO
    profit_ratio: float = config.PROFIT_RATIO
    safety_loading: float = config.SAFETY_LOADING

    def __post_init__(self) -> None:
        for name in ("expense_ratio", "profit_ratio", "safety_loading"):
            if getattr(self, name) < 0:
                raise ValueError(f"{name} must be non-negative.")
        if self.expense_ratio + self.profit_ratio >= 1:
            raise ValueError("expense_ratio + profit_ratio must be strictly below 1.")

    @property
    def commercial_factor(self) -> float:
        """Multiplier from the pure premium to the commercial premium."""
        return (1 + self.safety_loading) / (1 - self.expense_ratio - self.profit_ratio)

    def to_dict(self) -> dict[str, float]:
        return {**asdict(self), "commercial_factor": round(self.commercial_factor, 6)}


@dataclass(frozen=True)
class PricingEngine:
    frequency_model: Pipeline
    severity_model: Pipeline
    large_claim_loading: float
    assumptions: PricingAssumptions = PricingAssumptions()

    def quote(self, profiles: pd.DataFrame, years: float | np.ndarray = 1.0) -> pd.DataFrame:
        """Annual tariff of each risk profile, and its claim probability over `years`."""
        frequency = predict_frequency(self.frequency_model, profiles)
        severity = predict_severity(self.severity_model, profiles)
        pure = frequency * severity * (1 + self.large_claim_loading)
        commercial = pure * self.assumptions.commercial_factor
        return pd.DataFrame(
            {
                "frequency": frequency,
                "claim_probability": claim_probability(frequency, years),
                "severity": severity,
                "pure_premium": pure,
                "commercial_premium": commercial,
                "premium_margin": commercial - pure,
                "expected_loss_ratio": pure / commercial,
            },
            index=profiles.index,
        )


def load_pricing_engine(
    directory: Path = config.ARTIFACTS_DIR,
    assumptions: PricingAssumptions | None = None,
) -> PricingEngine:
    """Rebuild the engine from the registered models and pricing config.

    `assumptions` overrides the stored commercial assumptions, which lets an
    administrator simulate another expense or profit policy without retraining.
    """
    frequency_model, _ = load_model(FREQUENCY_MODEL_NAME, directory)
    severity_model, _ = load_model(SEVERITY_MODEL_NAME, directory)
    stored = json.loads((directory / PRICING_CONFIG_NAME).read_text(encoding="utf-8"))
    return PricingEngine(
        frequency_model=frequency_model,
        severity_model=severity_model,
        large_claim_loading=stored["large_claim_cap"]["loading"],
        assumptions=assumptions
        or PricingAssumptions(
            expense_ratio=stored["assumptions"]["expense_ratio"],
            profit_ratio=stored["assumptions"]["profit_ratio"],
            safety_loading=stored["assumptions"]["safety_loading"],
        ),
    )


def portfolio_margin(
    policies: pd.DataFrame,
    quotes: pd.DataFrame,
    by: str | pd.Series | None = None,
) -> pd.DataFrame:
    """Premiums collected vs expected and actual claims, by segment.

    Premiums and expected claims are pro-rated by exposure. Actual claims are
    the uncapped incurred amounts, so the realised margin includes the
    volatility of large claims that the loading is meant to absorb.
    """
    exposure = policies[s.EXPOSURE]
    frame = pd.DataFrame(
        {
            "segment": "portfolio" if by is None else (policies[by] if isinstance(by, str) else by),
            "policies": 1,
            "exposure": exposure,
            "premiums": quotes["commercial_premium"] * exposure,
            "expected_claims": quotes["pure_premium"] * exposure,
            "actual_claims": policies[s.CLAIM_AMOUNT],
        }
    )
    table = frame.groupby("segment", observed=True).sum(numeric_only=True)
    table["expected_margin"] = table["premiums"] - table["expected_claims"]
    table["actual_margin"] = table["premiums"] - table["actual_claims"]
    table["expected_loss_ratio"] = table["expected_claims"] / table["premiums"]
    table["actual_loss_ratio"] = table["actual_claims"] / table["premiums"]
    return table.reset_index()
