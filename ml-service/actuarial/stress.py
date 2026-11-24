"""Stress test of the insurance portfolio (lien avec le module de stress testing).

Question: what happens to the premium margin if claims are more frequent or
more expensive than observed, while premiums stay fixed for the year?

Base of the stress: the claims actually observed on the test portfolio, not
the expected claims. With a risk-based tariff the commercial premium is
proportional to the pure premium, so the *expected* loss ratio is the same in
every segment (1 / commercial factor, i.e. 68 % with the default
assumptions): stressing it would move every segment identically and teach
nothing. The *realised* loss ratio differs by segment (41 % to 83 % on the
test portfolio), which is where the resilience of each segment shows.

Under the frequency x severity decomposition, a shock multiplies claims by

    (1 + frequency shock) x (1 + severity shock)

and the break-even shock is the combined increase that brings the loss
ratio (claims / premiums) to 100 %.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

MIN_SHOCK = -0.5
MAX_SHOCK = 2.0


@dataclass(frozen=True)
class Shock:
    frequency: float = 0.0  # +0.20 = 20 % more claims
    severity: float = 0.0  # +0.10 = claims 10 % more expensive

    def __post_init__(self) -> None:
        for name in ("frequency", "severity"):
            value = getattr(self, name)
            if not MIN_SHOCK <= value <= MAX_SHOCK:
                raise ValueError(f"Le choc de {name} doit être compris entre {MIN_SHOCK:+.0%} et {MAX_SHOCK:+.0%}.")

    @property
    def claims_multiplier(self) -> float:
        return (1 + self.frequency) * (1 + self.severity)


def _stress_row(row: dict[str, Any], shock: Shock) -> dict[str, Any]:
    premiums = float(row["premiums"])
    expected = float(row["expected_claims"])
    actual = float(row["actual_claims"])
    stressed = actual * shock.claims_multiplier
    return {
        "segment": row["segment"],
        "policies": row["policies"],
        "premiums": round(premiums, 2),
        "expected_claims": round(expected, 2),
        "actual_claims": round(actual, 2),
        "stressed_claims": round(stressed, 2),
        "actual_margin": round(premiums - actual, 2),
        "stressed_margin": round(premiums - stressed, 2),
        "expected_loss_ratio": round(expected / premiums, 6),
        "actual_loss_ratio": round(actual / premiums, 6),
        "stressed_loss_ratio": round(stressed / premiums, 6),
        # Combined claims increase that brings the realised S/P to 100 %.
        "break_even_shock": round(premiums / actual - 1, 6) if actual > 0 else None,
    }


def stress_margins(
    segments: list[dict[str, Any]],
    portfolio: dict[str, Any],
    shock: Shock,
) -> dict[str, Any]:
    """Margins of each segment and of the portfolio under the shock."""
    stressed_segments = [_stress_row(row, shock) for row in segments]
    return {
        "shock": {
            "frequency": shock.frequency,
            "severity": shock.severity,
            "claims_multiplier": round(shock.claims_multiplier, 6),
        },
        "portfolio": _stress_row(portfolio, shock),
        "segments": stressed_segments,
        "segments_in_loss": sum(1 for row in stressed_segments if row["stressed_margin"] < 0),
    }
