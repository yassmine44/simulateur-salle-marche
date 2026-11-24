"""Claims reserving: Chain-Ladder with Mack's standard errors (cahier §3.7).

Vocabulary
----------
A run-off triangle C[i, k] gives the cumulative amount paid for claims of
origin (accident) year i after k development years. At the valuation date
only the upper-left part is known; the lower-right part is the future.

    reserve (provision) of origin i = ultimate C[i, I] - latest known C[i, I+1-i]

is the money the insurer still has to pay for claims that already occurred.

Chain-Ladder (deterministic)
----------------------------
Volume-weighted development factors

    f_k = sum_i C[i, k+1] / sum_i C[i, k]      over origins where both are known

project every origin to ultimate: C[i, I] = C[i, latest] x prod f_k.

Mack (1993): distribution-free standard error
---------------------------------------------
Under Mack's assumptions (E[C_{k+1} | C_k] = f_k C_k, Var = sigma_k^2 C_k,
independent origins), the mean squared error of each reserve splits into a
process part (randomness of future payments) and a parameter part
(uncertainty of the estimated f_k):

    mse(R_i) = C_iI^2 sum_k sigma_k^2 / f_k^2 (1 / C_ik + 1 / sum_j C_jk)

The total adds the covariance induced by the shared f_k. The last sigma is
extrapolated with Mack's rule min(s_{n-2}^4 / s_{n-3}^2, s_{n-3}^2, s_{n-2}^2).

Confidence intervals use a log-normal distribution matched to the reserve and
its standard error (reserves are positive and right-skewed).

Reference: T. Mack, "Distribution-free calculation of the standard error of
chain ladder reserve estimates", ASTIN Bulletin 23(2), 1993.
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from statistics import NormalDist
from typing import Any

import numpy as np


NUMERICAL_TOLERANCE = 1e-9  # relative floating-point tolerance


def within(value: float, low: float, high: float) -> bool:
    """low <= value <= high, tolerant to floating-point noise."""
    slack = NUMERICAL_TOLERANCE * max(abs(low), abs(high), 1.0)
    return bool(low - slack <= value <= high + slack)  # plain bool: JSON-serialisable


class TriangleError(ValueError):
    """Raised when a run-off triangle does not have the expected shape."""


@dataclass(frozen=True)
class Triangle:
    """Square cumulative run-off triangle; NaN marks unknown (future) cells."""

    origins: tuple[int, ...]
    values: np.ndarray

    def __post_init__(self) -> None:
        values = np.asarray(self.values, dtype=float)
        n = len(self.origins)
        if values.shape != (n, n):
            raise TriangleError(f"Le triangle doit être carré ({n}×{n}), reçu {values.shape}.")
        if n < 3:
            raise TriangleError("Il faut au moins 3 années d'origine.")
        for i in range(n):
            known, future = values[i, : n - i], values[i, n - i:]
            if np.isnan(known).any():
                raise TriangleError(f"Ligne {i + 1} : les {n - i} premières valeurs doivent être connues.")
            if not np.isnan(future).all():
                raise TriangleError(f"Ligne {i + 1} : les valeurs après la diagonale doivent être vides.")
        if not np.all(np.isfinite(values[~np.isnan(values)])):
            raise TriangleError("Les montants doivent être des nombres finis.")
        # Strictly positive: the link ratios C[k+1] / C[k] divide by every known value.
        if np.any(values[~np.isnan(values)] <= 0):
            raise TriangleError("Les montants cumulés connus doivent être strictement positifs.")
        object.__setattr__(self, "values", values)

    @classmethod
    def from_incremental(cls, origins: list[int], incremental: list[list[float]]) -> "Triangle":
        n = len(origins)
        values = np.full((n, n), np.nan)
        for i, row in enumerate(incremental):
            values[i, : len(row)] = np.cumsum(row)
        return cls(tuple(origins), values)

    @property
    def size(self) -> int:
        return len(self.origins)

    @property
    def latest(self) -> np.ndarray:
        """Latest known diagonal (one value per origin)."""
        n = self.size
        return np.array([self.values[i, n - 1 - i] for i in range(n)])

    def to_rows(self) -> list[list[float | None]]:
        return [[None if np.isnan(v) else round(float(v), 2) for v in row] for row in self.values]


@dataclass(frozen=True)
class ChainLadderResult:
    triangle: Triangle
    development_factors: np.ndarray  # f_1 .. f_{n-1}
    sigma2: np.ndarray  # Mack variance parameters sigma_1^2 .. sigma_{n-1}^2
    projected: np.ndarray  # full triangle, lower part projected
    ultimates: np.ndarray
    reserves: np.ndarray
    mack_se: np.ndarray
    total_reserve: float
    total_se: float

    @property
    def latest(self) -> np.ndarray:
        return self.triangle.latest

    @property
    def cumulative_factors(self) -> np.ndarray:
        """Factor from each development age to ultimate (1 for the last age)."""
        return np.append(np.cumprod(self.development_factors[::-1])[::-1], 1.0)

    def confidence_interval(self, level: float = 0.95) -> tuple[np.ndarray, np.ndarray]:
        return lognormal_interval(self.reserves, self.mack_se, level)

    def total_confidence_interval(self, level: float = 0.95) -> tuple[float, float]:
        low, high = lognormal_interval(np.array([self.total_reserve]), np.array([self.total_se]), level)
        return float(low[0]), float(high[0])

    def to_dict(self, level: float = 0.95) -> dict[str, Any]:
        low, high = self.confidence_interval(level)
        total_low, total_high = self.total_confidence_interval(level)
        n = self.triangle.size
        origins = []
        for i, origin in enumerate(self.triangle.origins):
            origins.append(
                {
                    "origin": origin,
                    "latest": round(float(self.latest[i]), 2),
                    "development_to_date": round(float(self.latest[i] / self.ultimates[i]), 6),
                    "ultimate": round(float(self.ultimates[i]), 2),
                    "reserve": round(float(self.reserves[i]), 2),
                    "mack_se": round(float(self.mack_se[i]), 2),
                    "cv": round(float(self.mack_se[i] / self.reserves[i]), 6) if self.reserves[i] > 0 else None,
                    "ci_low": round(float(low[i]), 2),
                    "ci_high": round(float(high[i]), 2),
                }
            )
        return {
            "confidence_level": level,
            "development_factors": [round(float(f), 6) for f in self.development_factors],
            "cumulative_factors": [round(float(f), 6) for f in self.cumulative_factors],
            "sigma2": [round(float(s), 6) for s in self.sigma2],
            "triangle": self.triangle.to_rows(),
            "projected": [[round(float(v), 2) for v in row] for row in self.projected],
            "origins": origins,
            "total": {
                "latest": round(float(self.latest.sum()), 2),
                "ultimate": round(float(self.ultimates.sum()), 2),
                "reserve": round(self.total_reserve, 2),
                "mack_se": round(self.total_se, 2),
                "cv": round(self.total_se / self.total_reserve, 6) if self.total_reserve > 0 else None,
                "ci_low": round(total_low, 2),
                "ci_high": round(total_high, 2),
            },
            "size": n,
        }


def lognormal_interval(
    mean: np.ndarray, se: np.ndarray, level: float = 0.95
) -> tuple[np.ndarray, np.ndarray]:
    """Interval of a log-normal with the given mean and standard deviation.

    A zero reserve (fully developed origin) gives the degenerate interval [0, 0].
    """
    mean = np.asarray(mean, dtype=float)
    se = np.asarray(se, dtype=float)
    # A standard error at floating-point noise level means "no uncertainty".
    se = np.where(se <= NUMERICAL_TOLERANCE * np.abs(mean), 0.0, se)
    z = NormalDist().inv_cdf(0.5 + level / 2)
    low = np.zeros_like(mean)
    high = np.zeros_like(mean)
    positive = (mean > 0) & (se > 0)
    s2 = np.log1p((se[positive] / mean[positive]) ** 2)
    mu = np.log(mean[positive]) - s2 / 2
    low[positive] = np.exp(mu - z * np.sqrt(s2))
    high[positive] = np.exp(mu + z * np.sqrt(s2))
    exact = (mean > 0) & ~(se > 0)
    low[exact] = high[exact] = mean[exact]
    return low, high


def chain_ladder(triangle: Triangle) -> ChainLadderResult:
    """Chain-Ladder reserves with Mack's standard errors."""
    c = triangle.values
    n = triangle.size

    # Development factors f_k (0-based k: from age k to age k+1).
    f = np.empty(n - 1)
    for k in range(n - 1):
        rows = n - 1 - k  # origins known at age k+1
        denominator = c[:rows, k].sum()
        if denominator <= 0:
            raise TriangleError(f"Colonne {k + 1} : somme nulle, facteur de développement indéfini.")
        f[k] = c[:rows, k + 1].sum() / denominator

    # Mack sigma_k^2.
    sigma2 = np.zeros(n - 1)
    for k in range(n - 2):
        rows = n - 1 - k
        if rows > 1:
            ratios = c[:rows, k + 1] / c[:rows, k]
            sigma2[k] = np.sum(c[:rows, k] * (ratios - f[k]) ** 2) / (rows - 1)
    if n >= 4:
        a, b = sigma2[n - 3], sigma2[n - 4]
        sigma2[n - 2] = min(a**2 / b if b > 0 else 0.0, min(a, b))
    else:
        sigma2[n - 2] = sigma2[n - 3] if n == 3 else 0.0

    # Projection of the lower triangle.
    projected = c.copy()
    for i in range(n):
        for k in range(n - i, n):
            projected[i, k] = projected[i, k - 1] * f[k - 1]
    ultimates = projected[:, n - 1]
    reserves = ultimates - triangle.latest

    # Column sums of known values used by the parameter-error term.
    column_sums = np.array([c[: n - 1 - k, k].sum() for k in range(n - 1)])

    # Mack mean squared error per origin.
    mse = np.zeros(n)
    for i in range(1, n):
        terms = 0.0
        for k in range(n - 1 - i, n - 1):
            terms += sigma2[k] / f[k] ** 2 * (1.0 / projected[i, k] + 1.0 / column_sums[k])
        mse[i] = ultimates[i] ** 2 * terms

    # Total: per-origin mse plus covariance through the shared factors.
    total_mse = mse.sum()
    for i in range(1, n - 1):
        later_ultimates = ultimates[i + 1:].sum()
        shared = sum(
            2.0 * sigma2[k] / f[k] ** 2 / column_sums[k] for k in range(n - 1 - i, n - 1)
        )
        total_mse += ultimates[i] * later_ultimates * shared

    return ChainLadderResult(
        triangle=triangle,
        development_factors=f,
        sigma2=sigma2,
        projected=projected,
        ultimates=ultimates,
        reserves=reserves,
        mack_se=np.sqrt(mse),
        total_reserve=float(reserves.sum()),
        total_se=float(math.sqrt(total_mse)),
    )
