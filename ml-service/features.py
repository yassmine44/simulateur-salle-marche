"""
Feature engineering shared by training and live inference.

Why these features?
-------------------
A Random Forest does not "see" raw OHLCV time series the way an LSTM would.
We must transform each week into a fixed-length numeric vector that encodes
local market regime information:

  - returns / momentum  -> short-term direction of price
  - moving-average ratios -> trend vs mean-reversion context
  - rolling volatility  -> stress / uncertainty regime
  - candle range + volume -> liquidity and intraday amplitude proxies

The classifier then learns non-linear combinations of these signals from
historical crisis weeks (2008, 2020, 2022).
"""

from __future__ import annotations

import pandas as pd

# Longest rolling window used below. Live requests need at least this many rows.
MIN_HISTORY = 10

FEATURE_COLUMNS = [
    "ret_1",
    "ret_2",
    "ret_4",
    "momentum_5",
    "momentum_8",
    "sma5_ratio",
    "sma10_ratio",
    "volatility_5",
    "volatility_10",
    "range_pct",
    "volume_z",
]


def candles_to_dataframe(candles: list[dict]) -> pd.DataFrame:
    """Normalize a list of OHLCV dicts into a typed DataFrame sorted by date."""
    frame = pd.DataFrame(candles).copy()

    required = {"date", "open", "high", "low", "close", "volume"}
    missing = required.difference(frame.columns)
    if missing:
        raise ValueError(f"Missing candle fields: {sorted(missing)}")

    frame["date"] = pd.to_datetime(frame["date"])
    for column in ("open", "high", "low", "close", "volume"):
        frame[column] = pd.to_numeric(frame[column], errors="coerce")

    frame = frame.dropna(subset=["open", "high", "low", "close", "volume"])
    frame = frame.sort_values("date").reset_index(drop=True)
    return frame


def engineer_features(frame: pd.DataFrame) -> pd.DataFrame:
    """
    Build the supervised-learning feature matrix from weekly OHLCV rows.

    Target construction (done by the caller on training data):
      y = 1 if next week's close > current close else 0
    """
    data = frame.copy()
    close = data["close"]

    # Price momentum / returns
    data["ret_1"] = close.pct_change(1)
    data["ret_2"] = close.pct_change(2)
    data["ret_4"] = close.pct_change(4)
    data["momentum_5"] = close / close.shift(5) - 1.0
    data["momentum_8"] = close / close.shift(8) - 1.0

    # Trend context via simple moving averages
    sma5 = close.rolling(5).mean()
    sma10 = close.rolling(10).mean()
    data["sma5_ratio"] = close / sma5 - 1.0
    data["sma10_ratio"] = close / sma10 - 1.0

    # Realized volatility of weekly returns
    weekly_ret = close.pct_change()
    data["volatility_5"] = weekly_ret.rolling(5).std()
    data["volatility_10"] = weekly_ret.rolling(10).std()

    # Candle geometry + volume pressure
    data["range_pct"] = (data["high"] - data["low"]) / close.replace(0, pd.NA)
    vol_mean = data["volume"].rolling(10).mean()
    vol_std = data["volume"].rolling(10).std().replace(0, pd.NA)
    data["volume_z"] = (data["volume"] - vol_mean) / vol_std

    return data


def latest_feature_row(frame: pd.DataFrame) -> pd.Series:
    """Return the most recent non-null feature vector for inference."""
    featured = engineer_features(frame)
    usable = featured.dropna(subset=FEATURE_COLUMNS)
    if usable.empty:
        raise ValueError(
            f"Need at least ~{MIN_HISTORY} weekly candles with complete features."
        )
    return usable.iloc[-1][FEATURE_COLUMNS]
