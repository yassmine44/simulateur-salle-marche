"""
Train a genuine scikit-learn RandomForestClassifier on the S&P 500 weekly CSVs.

Random Forest — why this counts as Machine Learning (for the oral defense)
--------------------------------------------------------------------------
1. Supervised learning: each historical week is labeled with a ground-truth
   target y = 1 if next week's close is higher than the current close, else 0.
2. The model is an ensemble of decision trees. During training, each tree is
   grown on a bootstrap sample of rows and a random subset of features
   (bagging + feature randomness). This is classic Breiman Random Forest.
3. At inference time, trees vote. `predict_proba` returns the fraction of trees
   that voted for class 1 — that fraction is our confidence / probability.
4. `feature_importances_` measures how much each engineered indicator reduced
   Gini impurity across splits — i.e. which market signal drove the decision.

This is NOT a hard-coded RSI rule. Weights are learned from data.
"""

from __future__ import annotations

import json
from pathlib import Path

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report
from sklearn.model_selection import train_test_split

from features import FEATURE_COLUMNS, engineer_features

ROOT = Path(__file__).resolve().parent
ARTIFACTS = ROOT / "artifacts"

# Reuse the same weekly CSVs already shipped with the Spring Boot backend.
CSV_FILES = [
    ROOT.parent
    / "backend"
    / "simulateur-backend"
    / "src"
    / "main"
    / "resources"
    / "sp500_weekly_2008_2009.csv",
    ROOT.parent
    / "backend"
    / "simulateur-backend"
    / "src"
    / "main"
    / "resources"
    / "sp500_weekly_2020_covid.csv",
    ROOT.parent
    / "backend"
    / "simulateur-backend"
    / "src"
    / "main"
    / "resources"
    / "sp500_weekly_2022.csv",
]


def load_all_csvs() -> pd.DataFrame:
    frames: list[pd.DataFrame] = []

    for path in CSV_FILES:
        if not path.exists():
            raise FileNotFoundError(f"Missing training CSV: {path}")

        chunk = pd.read_csv(path)
        chunk.columns = [c.strip() for c in chunk.columns]
        # Yahoo-style headers -> lowercase OHLCV names used by features.py
        rename = {
            "Date": "date",
            "Open": "open",
            "High": "high",
            "Low": "low",
            "Close": "close",
            "Adj Close": "adj_close",
            "Volume": "volume",
        }
        chunk = chunk.rename(columns=rename)
        chunk["source"] = path.name
        frames.append(chunk)

    data = pd.concat(frames, ignore_index=True)
    data["date"] = pd.to_datetime(data["date"])
    data = data.sort_values(["source", "date"]).reset_index(drop=True)
    return data


def build_dataset(raw: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    """
    Feature matrix X and binary target y.

    y_t = 1  if Close_{t+1} > Close_t   (next week goes UP)
    y_t = 0  otherwise                  (next week goes DOWN / flat)
    """
    parts: list[pd.DataFrame] = []

    for _, group in raw.groupby("source", sort=False):
        featured = engineer_features(group.reset_index(drop=True))
        # Shift(-1) looks one week ahead — this is the supervised label.
        featured["target"] = (
            featured["close"].shift(-1) > featured["close"]
        ).astype(int)
        parts.append(featured)

    dataset = pd.concat(parts, ignore_index=True)
    dataset = dataset.dropna(subset=FEATURE_COLUMNS + ["target"])

    x = dataset[FEATURE_COLUMNS]
    y = dataset["target"].astype(int)
    return x, y


HISTO_DIR = (
    ROOT.parent
    / "backend"
    / "simulateur-backend"
    / "src"
    / "main"
    / "resources"
)
HISTO_SYMBOL = "BIAT"


def load_histo_daily(symbol: str = HISTO_SYMBOL) -> pd.DataFrame | None:
    """
    Optional BVMT daily series from histo_cotation_*.csv (semicolon format).
    Used to enrich training when those files are present — weekly S&P still primary.
    """
    frames: list[pd.DataFrame] = []
    for path in sorted(HISTO_DIR.glob("histo_cotation_*.csv")):
        if "(" in path.name or ")" in path.name:
            continue
        try:
            chunk = pd.read_csv(path, sep=";")
        except Exception as exc:  # noqa: BLE001
            print(f"Skipping {path.name}: {exc}")
            continue
        chunk.columns = [c.strip() for c in chunk.columns]
        rename = {
            "SEANCE": "date",
            "VALEUR": "symbol",
            "OUVERTURE": "open",
            "PLUS_HAUT": "high",
            "PLUS_BAS": "low",
            "CLOTURE": "close",
            "QUANTITE_NEGOCIEE": "volume",
        }
        chunk = chunk.rename(columns=rename)
        if "symbol" not in chunk.columns:
            continue
        filtered = chunk[
            chunk["symbol"].astype(str).str.strip().str.upper() == symbol.upper()
        ].copy()
        if filtered.empty:
            continue
        for col in ("open", "high", "low", "close", "volume"):
            filtered[col] = pd.to_numeric(filtered[col], errors="coerce")
        filtered["date"] = pd.to_datetime(
            filtered["date"], dayfirst=True, errors="coerce"
        )
        filtered = filtered.dropna(subset=["date", "open", "high", "low", "close"])
        filtered["source"] = f"histo_{symbol}_{path.stem}"
        frames.append(
            filtered[["date", "open", "high", "low", "close", "volume", "source"]]
        )

    if not frames:
        return None
    data = pd.concat(frames, ignore_index=True)
    data = data.sort_values(["source", "date"]).reset_index(drop=True)
    print(f"Loaded {len(data)} daily histo rows for {symbol}")
    return data


def train(
    symbol: str = HISTO_SYMBOL,
    include_sp500: bool = True,
    include_histo: bool = True,
) -> dict:
    """
    Batch-train RandomForest on S&P crisis weeks + BVMT histo 2018–2026.
    Called by CLI and by FastAPI POST /api/retrain (Spring startup warmup).
    """
    frames: list[pd.DataFrame] = []
    sources: list[str] = []

    if include_sp500:
        print("Loading S&P 500 weekly CSVs...")
        frames.append(load_all_csvs())
        sources.extend(path.name for path in CSV_FILES)

    if include_histo:
        print(f"Loading histo cotation 2018–2026 for {symbol}...")
        histo = load_histo_daily(symbol=symbol)
        if histo is not None and not histo.empty:
            frames.append(histo)
            sources.append(f"histo_cotation_{symbol}_2018_2026")
        else:
            print("No histo_cotation_*.csv rows found for symbol; continuing.")

    if not frames:
        raise RuntimeError("No training CSVs available for Random Forest.")

    raw = pd.concat(frames, ignore_index=True)
    x, y = build_dataset(raw)

    print(
        f"Training rows: {len(x)} | class balance "
        f"UP={y.mean():.2%} DOWN={1 - y.mean():.2%}"
    )

    x_train, x_test, y_train, y_test = train_test_split(
        x,
        y,
        test_size=0.25,
        random_state=42,
        stratify=y,
    )

    # RandomForestClassifier:
    # - n_estimators: number of trees in the forest (more trees => stabler votes)
    # - max_depth: limits overfitting on a small weekly sample
    # - class_weight=balanced: crisis weeks can be unbalanced UP/DOWN
    # - random_state: reproducible oral-defense demos
    model = RandomForestClassifier(
        n_estimators=200,
        max_depth=6,
        min_samples_leaf=3,
        class_weight="balanced",
        random_state=42,
        n_jobs=-1,
    )
    model.fit(x_train, y_train)

    y_pred = model.predict(x_test)
    accuracy = accuracy_score(y_test, y_pred)
    print(f"Hold-out accuracy: {accuracy:.3f}")
    report_text = classification_report(
        y_test, y_pred, target_names=["Down", "Up"]
    )
    print(report_text)
    report_dict = classification_report(
        y_test,
        y_pred,
        target_names=["Down", "Up"],
        output_dict=True,
    )

    importances = {
        name: float(score)
        for name, score in sorted(
            zip(FEATURE_COLUMNS, model.feature_importances_),
            key=lambda item: item[1],
            reverse=True,
        )
    }
    print("Feature importances (Gini decrease):")
    for name, score in importances.items():
        print(f"  {name:16s} {score:.4f}")

    # Academic panel metrics:
    # - overall accuracy on hold-out
    # - crash detection = Down recall
    # - rally prediction = Up precision
    crash_detection = float(report_dict.get("Down", {}).get("recall", 0.0))
    rally_prediction = float(report_dict.get("Up", {}).get("precision", 0.0))

    ARTIFACTS.mkdir(parents=True, exist_ok=True)
    model_path = ARTIFACTS / "random_forest_sp500.joblib"
    meta_path = ARTIFACTS / "model_meta.json"

    meta = {
        "model": "RandomForestClassifier",
        "task": "binary_classification_next_week_direction",
        "feature_columns": FEATURE_COLUMNS,
        "holdout_accuracy": accuracy,
        "overall_accuracy": accuracy,
        "historicalPredictionSuccessRate": accuracy,
        "crash_detection_rating": crash_detection,
        "rally_prediction_rating": rally_prediction,
        "down_recall": crash_detection,
        "up_precision": rally_prediction,
        "classification_report": report_dict,
        "feature_importances": importances,
        "n_training_rows": int(len(x)),
        "training_sources": sources,
        "symbol": symbol,
        "include_sp500": include_sp500,
        "include_histo_2018_2026": include_histo,
    }

    joblib.dump(model, model_path)
    meta_path.write_text(
        json.dumps(meta, indent=2),
        encoding="utf-8",
    )

    print(f"Saved model -> {model_path}")
    print(f"Saved meta  -> {meta_path}")
    return meta


if __name__ == "__main__":
    train()
