"""
Lightweight FastAPI microservice that serves the trained Random Forest model.

Spring Boot posts the latest visible weekly candles to POST /api/predict.
The service engineers the same features used at training time, asks the forest
to vote, and returns:

  - prediction: "Up" | "Down"
  - probability: P(next week close is higher) in [0, 1]
  - feature_importance: which engineered signals matter most globally
    (RandomForest feature_importances_), plus the live feature values used

Run:
  pip install -r requirements.txt
  python train.py
  uvicorn app:app --host 0.0.0.0 --port 5000
"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from sklearn.ensemble import RandomForestClassifier

from features import (
    FEATURE_COLUMNS,
    MIN_HISTORY,
    candles_to_dataframe,
    latest_feature_row,
)
from train import train as run_training

ROOT = Path(__file__).resolve().parent
MODEL_PATH = ROOT / "artifacts" / "random_forest_sp500.joblib"
META_PATH = ROOT / "artifacts" / "model_meta.json"

app = FastAPI(
    title="Salle de Marché — Gamification ML Service",
    description=(
        "Supervised RandomForestClassifier predicting next-week S&P 500 "
        "direction from engineered weekly OHLCV features."
    ),
    version="1.0.0",
)

_model: RandomForestClassifier | None = None


class CandlePayload(BaseModel):
    date: str
    open: float
    high: float
    low: float
    close: float
    volume: float


class PredictRequest(BaseModel):
    candles: list[CandlePayload] = Field(
        ...,
        min_length=MIN_HISTORY,
        description="Recent weekly OHLCV candles (oldest -> newest).",
    )


class PredictResponse(BaseModel):
    prediction: str
    probability: float
    feature_importance: dict[str, float]
    live_features: dict[str, float]
    model: str = "RandomForestClassifier"


def load_model() -> RandomForestClassifier:
    global _model
    if _model is not None:
        return _model

    if not MODEL_PATH.exists():
        raise RuntimeError(
            f"Model artifact missing at {MODEL_PATH}. Run `python train.py` first."
        )

    _model = joblib.load(MODEL_PATH)
    return _model


@app.on_event("startup")
def startup() -> None:
    load_model()


@app.get("/health")
def health() -> dict[str, Any]:
    ready = MODEL_PATH.exists()
    return {
        "status": "ok" if ready else "model_missing",
        "model_loaded": _model is not None,
        "model_path": str(MODEL_PATH),
    }


@app.get("/api/metrics")
def metrics() -> dict[str, Any]:
    """
    Real hold-out metrics for the ML Prediction Model panel:
    overall accuracy, Down recall (crash), Up precision (rally).
    """
    if not META_PATH.exists():
        raise HTTPException(
            status_code=503,
            detail=f"Model meta missing at {META_PATH}. Run `python train.py` first.",
        )

    import json

    meta = json.loads(META_PATH.read_text(encoding="utf-8"))
    report = meta.get("classification_report") or {}
    down = report.get("Down") or {}
    up = report.get("Up") or {}
    accuracy = float(
        meta.get("overall_accuracy")
        or meta.get("holdout_accuracy")
        or meta.get("historicalPredictionSuccessRate")
        or 0.0
    )
    crash = float(
        meta.get("crash_detection_rating")
        or meta.get("down_recall")
        or down.get("recall")
        or 0.0
    )
    rally = float(
        meta.get("rally_prediction_rating")
        or meta.get("up_precision")
        or up.get("precision")
        or up.get("recall")
        or 0.0
    )

    return {
        "holdout_accuracy": accuracy,
        "overall_accuracy": accuracy,
        "historicalPredictionSuccessRate": accuracy,
        "crash_detection_rating": crash,
        "crashDetectionRating": crash,
        "rally_prediction_rating": rally,
        "rallyPredictionRating": rally,
        "down_recall": crash,
        "up_precision": rally,
        "classification_report": report,
        "n_training_rows": meta.get("n_training_rows"),
        "model": meta.get("model", "RandomForestClassifier"),
        "available": True,
        "explainer": (
            "Hold-out evaluation on 2018–2026 historical data: overall accuracy, "
            "Down-trend recall (crash detection), and Up-trend precision (rally)."
        ),
    }


class RetrainRequest(BaseModel):
    symbol: str = "BIAT"
    include_sp500: bool = True
    include_histo_2018_2026: bool = True


@app.post("/api/retrain")
def retrain(request: RetrainRequest = RetrainRequest()) -> dict[str, Any]:
    """
    Batch pre-training used by Spring Boot on application startup.
    Trains on BVMT histo 2018–2026 (+ optional S&P crisis weeks), then
    hot-reloads the in-memory RandomForestClassifier for instant inference.
    """
    global _model
    try:
        meta = run_training(
            symbol=request.symbol,
            include_sp500=request.include_sp500,
            include_histo=request.include_histo_2018_2026,
        )
        _model = None
        load_model()
        return {
            "status": "ready",
            "model_loaded": _model is not None,
            "meta": meta,
        }
    except Exception as exc:  # noqa: BLE001
        raise HTTPException(status_code=500, detail=str(exc)) from exc


@app.post("/api/predict", response_model=PredictResponse)
def predict(request: PredictRequest) -> PredictResponse:
    """
    Inference path explained for the oral defense:

    1. Convert the JSON candle window into a DataFrame.
    2. Recompute the exact training features (no leakage: we never look ahead).
    3. Feed the latest feature row to RandomForestClassifier.predict_proba.
    4. Class 1 probability = share of trees voting "next week Up".
    5. Return that probability + global Gini feature importances.
    """
    try:
        model = load_model()
        frame = candles_to_dataframe([c.model_dump() for c in request.candles])
        features = latest_feature_row(frame)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 — surface load errors to Spring fallback
        raise HTTPException(status_code=503, detail=str(exc)) from exc

    # predict_proba[:, 1] = estimated P(y=1) = P(next close higher)
    proba_up = float(model.predict_proba(features.to_frame().T)[0][1])
    prediction = "Up" if proba_up >= 0.5 else "Down"

    # Global importances learned during training (which metric drove splits most).
    importance = {
        name: float(score)
        for name, score in sorted(
            zip(FEATURE_COLUMNS, model.feature_importances_),
            key=lambda item: item[1],
            reverse=True,
        )
    }

    return PredictResponse(
        prediction=prediction,
        probability=round(proba_up if prediction == "Up" else 1.0 - proba_up, 4),
        feature_importance=importance,
        live_features={name: float(features[name]) for name in FEATURE_COLUMNS},
        model="RandomForestClassifier",
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("app:app", host="0.0.0.0", port=5000, reload=False)
