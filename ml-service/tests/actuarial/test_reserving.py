from __future__ import annotations

import json

import numpy as np
import pandas as pd
import pytest

from actuarial.reserving import Triangle, TriangleError, chain_ladder, lognormal_interval
from actuarial.reserving_report import backtest, build_report
from actuarial.schedule_p import build_portfolios, clean_companies

# Taylor & Ashe (1983) incremental triangle, the reference example of
# Mack (1993) and of the R package ChainLadder (dataset GenIns).
TAYLOR_ASHE = [
    [357848, 766940, 610542, 482940, 527326, 574398, 146342, 139950, 227229, 67948],
    [352118, 884021, 933894, 1183289, 445745, 320996, 527804, 266172, 425046],
    [290507, 1001799, 926219, 1016654, 750816, 146923, 495992, 280405],
    [310608, 1108250, 776189, 1562400, 272482, 352053, 206286],
    [443160, 693190, 991983, 769488, 504851, 470639],
    [396132, 937085, 847498, 805037, 705960],
    [440832, 847631, 1131398, 1063269],
    [359480, 1061648, 1443370],
    [376686, 986608],
    [344014],
]


@pytest.fixture(scope="module")
def taylor_ashe():
    return chain_ladder(Triangle.from_incremental(list(range(1, 11)), TAYLOR_ASHE))


# --- known answer -----------------------------------------------------------------
def test_matches_published_chain_ladder_and_mack_results(taylor_ashe) -> None:
    """Published values (Mack 1993; R ChainLadder::MackChainLadder(GenIns))."""
    assert round(taylor_ashe.total_reserve) == 18_680_856
    assert round(taylor_ashe.total_se) == 2_447_095
    assert [round(u) for u in taylor_ashe.ultimates] == [
        3_901_463, 5_433_719, 5_378_826, 5_297_906, 4_858_200,
        5_111_171, 5_660_771, 6_784_799, 5_642_266, 4_969_825,
    ]
    assert round(taylor_ashe.mack_se[9]) == 1_363_155
    assert round(taylor_ashe.development_factors[0], 4) == 3.4906


def test_projection_follows_the_development_factors(taylor_ashe) -> None:
    projected, f, n = taylor_ashe.projected, taylor_ashe.development_factors, 10
    for i in range(1, n):
        for k in range(n - i, n):
            assert projected[i, k] == pytest.approx(projected[i, k - 1] * f[k - 1])
    assert taylor_ashe.reserves[0] == 0
    assert taylor_ashe.mack_se[0] == 0
    np.testing.assert_allclose(taylor_ashe.cumulative_factors[-1], 1.0)


def test_report_dictionary_is_consistent(taylor_ashe) -> None:
    report = taylor_ashe.to_dict()
    assert report["total"]["reserve"] == pytest.approx(sum(o["reserve"] for o in report["origins"]), abs=0.1)
    assert report["triangle"][9][1] is None and report["projected"][9][9] > 0
    for origin in report["origins"][1:]:
        assert origin["ci_low"] < origin["reserve"] < origin["ci_high"]


def test_deterministic_triangle_has_exact_reserves_and_no_uncertainty() -> None:
    """Every origin develops with the same pattern: reserves are exact, sigma is 0."""
    pattern = np.array([1.0, 1.8, 2.3, 2.5, 2.55])
    first = np.array([100.0, 120.0, 90.0, 150.0, 110.0])
    full = np.outer(first, pattern)
    known = full.copy()
    for i in range(5):
        known[i, 5 - i:] = np.nan
    result = chain_ladder(Triangle(tuple(range(2001, 2006)), known))

    np.testing.assert_allclose(result.ultimates, full[:, -1])
    np.testing.assert_allclose(result.mack_se, 0.0, atol=1e-9)
    assert result.total_se == pytest.approx(0.0, abs=1e-9)


# --- validation -------------------------------------------------------------------
@pytest.mark.parametrize(
    ("values", "message"),
    [
        (np.ones((3, 4)), "carré"),
        (np.array([[1, 2, 3], [1, np.nan, np.nan], [1, np.nan, np.nan]], dtype=float), "Ligne 2"),
        (np.array([[1, 2, 3], [1, 2, 5], [1, np.nan, np.nan]], dtype=float), "après la diagonale"),
        (np.array([[0, 2, 3], [1, 2, np.nan], [1, np.nan, np.nan]], dtype=float), "strictement positifs"),
        (np.array([[1, -2, 3], [1, 2, np.nan], [1, np.nan, np.nan]], dtype=float), "strictement positifs"),
        (np.array([[1, 2, np.inf], [1, 2, np.nan], [1, np.nan, np.nan]], dtype=float), "finis"),
        (np.ones((2, 2)), "3 années"),
    ],
)
def test_invalid_triangles_are_rejected(values, message) -> None:
    with pytest.raises(TriangleError, match=message):
        Triangle(tuple(range(len(values))), values)


def test_lognormal_interval() -> None:
    low, high = lognormal_interval(np.array([100.0, 0.0, 50.0]), np.array([20.0, 0.0, 0.0]))
    assert low[0] < 100 < high[0]
    assert (low[1], high[1]) == (0.0, 0.0)
    assert (low[2], high[2]) == (50.0, 50.0)
    wide_low, wide_high = lognormal_interval(np.array([100.0]), np.array([20.0]), level=0.99)
    assert wide_low[0] < low[0] and wide_high[0] > high[0]


# --- Schedule P portfolios and backtest (synthetic, no network) --------------------
def _schedule_p(companies: dict[int, float], dirty: int | None = None) -> pd.DataFrame:
    """10x10 grids following an exact development pattern, scaled per company."""
    pattern = np.array([0.3, 0.55, 0.72, 0.83, 0.9, 0.94, 0.97, 0.985, 0.995, 1.0])
    rows = []
    for code, scale in companies.items():
        for a, accident in enumerate(range(1988, 1998)):
            for lag in range(1, 11):
                paid = scale * (1 + 0.05 * a) * pattern[lag - 1]
                if code == dirty and lag == 3:
                    paid = -1.0
                rows.append({
                    "GRCODE": code, "GRNAME": f"Company {code} ", "AccidentYear": accident,
                    "DevelopmentYear": accident + lag - 1, "DevelopmentLag": lag,
                    "CumPaidLoss_B": paid, "EarnedPremNet_B": scale * 1.4,
                })
    return pd.DataFrame(rows)


def test_clean_companies_excludes_unusable_groups() -> None:
    data = _schedule_p({1: 1000.0, 2: 500.0, 3: 200.0}, dirty=3)
    assert clean_companies(data) == [1, 2]


def test_portfolios_hide_the_future_and_keep_the_actual_outcome() -> None:
    data = _schedule_p({1: 1000.0, 2: 500.0})
    market, largest, *_ = build_portfolios(data)

    assert market.code == "market" and market.companies == 2
    assert largest.name == "Company 1"  # names are stripped, largest premium first
    assert np.isnan(market.known.values[9, 1]) and not np.isnan(market.actual[9, 9])
    np.testing.assert_allclose(market.actual[:, 0], largest.actual[:, 0] * 1.5)


def test_backtest_on_an_exact_pattern_has_no_error() -> None:
    data = _schedule_p({1: 1000.0, 2: 500.0})
    portfolio = build_portfolios(data)[0]
    result = chain_ladder(portfolio.known)
    test = backtest(result, portfolio)

    assert test["total"]["relative_error"] == pytest.approx(0.0, abs=1e-9)
    assert test["total"]["within_interval"]
    assert test["interval_coverage"] == 1.0
    assert test["origins"][0]["within_interval"] is None  # fully developed origin
    assert test["origins"][5]["actual_loss_ratio"] == pytest.approx(
        test["origins"][5]["estimated_loss_ratio"]
    )


def test_report_summary_and_serialisation() -> None:
    report = build_report(build_portfolios(_schedule_p({1: 1000.0, 2: 500.0})))
    assert report["summary"]["portfolios"] == 3  # market + 2 companies
    assert report["summary"]["origins_backtested"] == 27
    assert report["unit"] == "milliers de dollars US"
    # The report is written as JSON: no NumPy scalar may leak into it.
    assert json.loads(json.dumps(report)) == report
