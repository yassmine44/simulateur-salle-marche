"""Reserving report on the CAS Schedule P portfolios, with backtest.

Usage (from ml-service/):

    python -m actuarial.reserving_report

Writes artifacts/actuarial/reserving_report.json. For every portfolio:
Chain-Ladder factors, ultimates, reserves, Mack standard errors and 95 %
intervals as of 31/12/1997, then the comparison with what was actually paid
during the following years (backtest).
"""

from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

import numpy as np

from actuarial import config
from actuarial.reserving import ChainLadderResult, chain_ladder, within
from actuarial.schedule_p import VALUATION_YEAR, Portfolio, build_portfolios, load_schedule_p

REPORT_NAME = "reserving_report.json"
CONFIDENCE_LEVEL = 0.95

logger = logging.getLogger("actuarial.reserving_report")


def backtest(result: ChainLadderResult, portfolio: Portfolio, level: float = CONFIDENCE_LEVEL) -> dict[str, Any]:
    """Estimated vs actually paid reserves, per origin and in total.

    The actual reserve is what was paid between the valuation date and the
    last development year available (10 years of development).
    """
    n = result.triangle.size
    actual_ultimates = portfolio.actual[:, n - 1]
    actual_reserves = actual_ultimates - result.latest
    low, high = result.confidence_interval(level)

    origins = []
    for i, origin in enumerate(result.triangle.origins):
        estimated = float(result.reserves[i])
        actual = float(actual_reserves[i])
        origins.append(
            {
                "origin": origin,
                "estimated_reserve": round(estimated, 2),
                "actual_reserve": round(actual, 2),
                "error": round(estimated - actual, 2),
                "relative_error": round((estimated - actual) / actual, 6) if actual > 0 else None,
                "within_interval": within(actual, low[i], high[i]) if estimated > 0 else None,
                "premium": round(float(portfolio.premiums[i]), 2),
                "estimated_loss_ratio": round(float(result.ultimates[i] / portfolio.premiums[i]), 6),
                "actual_loss_ratio": round(float(actual_ultimates[i] / portfolio.premiums[i]), 6),
            }
        )

    total_estimated = result.total_reserve
    total_actual = float(actual_reserves.sum())
    total_low, total_high = result.total_confidence_interval(level)
    with_reserve = [o for o in origins if o["within_interval"] is not None]
    return {
        "origins": origins,
        "total": {
            "estimated_reserve": round(total_estimated, 2),
            "actual_reserve": round(total_actual, 2),
            "error": round(total_estimated - total_actual, 2),
            "relative_error": round((total_estimated - total_actual) / total_actual, 6),
            "within_interval": within(total_actual, total_low, total_high),
        },
        "interval_coverage": round(
            sum(o["within_interval"] for o in with_reserve) / len(with_reserve), 6
        ),
    }


def portfolio_report(portfolio: Portfolio) -> dict[str, Any]:
    result = chain_ladder(portfolio.known)
    return {
        "code": portfolio.code,
        "name": portfolio.name,
        "companies": portfolio.companies,
        "chain_ladder": result.to_dict(CONFIDENCE_LEVEL),
        "actual_triangle": [[round(float(v), 2) for v in row] for row in portfolio.actual],
        "backtest": backtest(result, portfolio),
    }


def build_report(portfolios: list[Portfolio]) -> dict[str, Any]:
    reports = [portfolio_report(p) for p in portfolios]
    all_origins = [
        o for r in reports for o in r["backtest"]["origins"] if o["within_interval"] is not None
    ]
    return {
        "source": "CAS Loss Reserve Database — Schedule P, responsabilité civile automobile (ppauto)",
        "unit": "milliers de dollars US",
        "valuation_year": VALUATION_YEAR,
        "confidence_level": CONFIDENCE_LEVEL,
        "method": "Chain-Ladder (facteurs pondérés par les volumes) et erreur standard de Mack (1993)",
        "portfolios": reports,
        "summary": {
            "portfolios": len(reports),
            "origins_backtested": len(all_origins),
            "interval_coverage": round(
                float(np.mean([o["within_interval"] for o in all_origins])), 6
            ),
            "totals_within_interval": sum(r["backtest"]["total"]["within_interval"] for r in reports),
        },
    }


def run(artifacts_dir: Path = config.ARTIFACTS_DIR) -> dict[str, Any]:
    report = build_report(build_portfolios(load_schedule_p()))
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    (artifacts_dir / REPORT_NAME).write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    for portfolio in report["portfolios"]:
        total = portfolio["chain_ladder"]["total"]
        test = portfolio["backtest"]["total"]
        logger.info(
            "%-45s reserve=%12.0f  se=%10.0f (cv %.1f%%)  actual=%12.0f  error=%+6.1f%%  in CI=%s",
            portfolio["name"][:45], total["reserve"], total["mack_se"], 100 * total["cv"],
            test["actual_reserve"], 100 * test["relative_error"], test["within_interval"],
        )
    logger.info("Summary: %s", report["summary"])
    return report


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s | %(message)s")
    run()


if __name__ == "__main__":
    main()
