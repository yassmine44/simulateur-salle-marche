"""CAS Loss Reserve Database: real run-off triangles with their actual outcome.

Source: Casualty Actuarial Society, Schedule P data (Meyers & Shi, 2011),
line "private passenger auto liability" (ppauto_pos.csv). For 146 US insurer
groups, it gives for accident years 1988-1997 the cumulative paid losses over
10 development years (through 2006) and the earned premiums.

At the valuation date 31/12/1997 only the upper triangle was known. The data
also contains what was actually paid afterwards, so the reserves estimated in
1997 can be compared with reality (backtest). This is why the dataset is the
standard benchmark of the reserving literature.
"""

from __future__ import annotations

import logging
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

from actuarial import config
from actuarial.reserving import Triangle

logger = logging.getLogger(__name__)

SCHEDULE_P_URL = "https://www.casact.org/sites/default/files/2021-04/ppauto_pos.csv"
SCHEDULE_P_PATH = config.SERVICE_ROOT / "data" / "cas" / "ppauto_pos.csv"
VALUATION_YEAR = 1997
REQUIRED_COLUMNS = frozenset(
    {"GRCODE", "GRNAME", "AccidentYear", "DevelopmentYear", "DevelopmentLag", "CumPaidLoss_B", "EarnedPremNet_B"}
)
LARGEST_COMPANIES = 5


@dataclass(frozen=True)
class Portfolio:
    code: str
    name: str
    companies: int
    known: Triangle  # upper triangle as of the valuation date
    actual: np.ndarray  # complete triangle, actual payments of the following years
    premiums: np.ndarray  # net earned premium per accident year


def load_schedule_p(path: Path = SCHEDULE_P_PATH) -> pd.DataFrame:
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        logger.info("Downloading CAS Schedule P (private passenger auto) to %s", path)
        urllib.request.urlretrieve(SCHEDULE_P_URL, path)  # noqa: S310 - fixed https URL
    data = pd.read_csv(path)
    missing = REQUIRED_COLUMNS - set(data.columns)
    if missing:
        raise ValueError(f"Colonnes manquantes dans Schedule P : {sorted(missing)}")
    return data


def clean_companies(data: pd.DataFrame) -> list[int]:
    """Groups with usable data: complete 10x10 grid, positive premiums,
    positive first-year payments and no negative cumulative payment."""
    def usable(group: pd.DataFrame) -> bool:
        return (
            len(group) == 100
            and (group["EarnedPremNet_B"] > 0).all()
            and (group["CumPaidLoss_B"] >= 0).all()
            and (group.loc[group["DevelopmentLag"] == 1, "CumPaidLoss_B"] > 0).all()
        )

    return sorted(code for code, group in data.groupby("GRCODE") if usable(group))


def _portfolio(data: pd.DataFrame, code: str, name: str, groups: list[int]) -> Portfolio:
    subset = data[data["GRCODE"].isin(groups)]
    grid = subset.pivot_table(
        index="AccidentYear", columns="DevelopmentLag", values="CumPaidLoss_B", aggfunc="sum"
    ).sort_index()
    actual = grid.to_numpy(dtype=float)
    origins = tuple(int(year) for year in grid.index)

    known = actual.copy()
    calendar = np.add.outer(np.array(origins), np.arange(actual.shape[1]))  # accident year + lag - 1
    known[calendar > VALUATION_YEAR] = np.nan

    premiums = (
        subset[subset["DevelopmentLag"] == 1]
        .groupby("AccidentYear")["EarnedPremNet_B"]
        .sum()
        .reindex(grid.index)
        .to_numpy(dtype=float)
    )
    return Portfolio(code, name, len(groups), Triangle(origins, known), actual, premiums)


def build_portfolios(data: pd.DataFrame) -> list[Portfolio]:
    """The market aggregate of clean groups, then the largest clean groups."""
    clean = clean_companies(data)
    portfolios = [_portfolio(data, "market", f"Marché américain ({len(clean)} assureurs)", clean)]

    premiums = (
        data[(data["DevelopmentLag"] == 1) & data["GRCODE"].isin(clean)]
        .groupby(["GRCODE", "GRNAME"])["EarnedPremNet_B"]
        .sum()
        .sort_values(ascending=False)
    )
    for (grcode, grname), _ in premiums.head(LARGEST_COMPANIES).items():
        portfolios.append(_portfolio(data, str(grcode), grname.strip(), [grcode]))
    return portfolios
