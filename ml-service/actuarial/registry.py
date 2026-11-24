"""Versioned persistence of trained models.

Each model is stored as two files sharing the same stem:

    <name>.joblib     the fitted scikit-learn pipeline (preprocessing + model)
    <name>.card.json  its model card: data lineage, metrics, environment

The model card makes every prediction served by the platform traceable to
the exact data and code that produced the model (cahier des charges §4,
traçabilité).
"""

from __future__ import annotations

import json
import platform
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import sklearn

from actuarial import config


@dataclass
class ModelCard:
    name: str
    task: str
    algorithm: str
    target: str
    weight: str
    features: list[str]
    data_source: str
    data_fingerprint: str
    train_summary: dict[str, Any]
    test_summary: dict[str, Any]
    metrics: dict[str, Any]
    candidates: list[dict[str, Any]]
    parameters: dict[str, Any] = field(default_factory=dict)
    random_state: int = config.RANDOM_STATE
    version: str = field(
        default_factory=lambda: datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    )
    created_at: str = field(
        default_factory=lambda: datetime.now(timezone.utc).isoformat(timespec="seconds")
    )
    environment: dict[str, str] = field(
        default_factory=lambda: {
            "python": platform.python_version(),
            "scikit_learn": sklearn.__version__,
        }
    )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def model_paths(name: str, directory: Path = config.ARTIFACTS_DIR) -> tuple[Path, Path]:
    return directory / f"{name}.joblib", directory / f"{name}.card.json"


def save_model(
    model: Any,
    card: ModelCard,
    directory: Path = config.ARTIFACTS_DIR,
) -> tuple[Path, Path]:
    directory.mkdir(parents=True, exist_ok=True)
    model_path, card_path = model_paths(card.name, directory)
    joblib.dump(model, model_path, compress=3)
    card_path.write_text(
        json.dumps(card.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8"
    )
    return model_path, card_path


def load_model(name: str, directory: Path = config.ARTIFACTS_DIR) -> tuple[Any, dict[str, Any]]:
    model_path, card_path = model_paths(name, directory)
    if not model_path.exists() or not card_path.exists():
        raise FileNotFoundError(
            f"Model '{name}' not found in {directory}. Train it first "
            f"(python -m actuarial.train)."
        )
    card = json.loads(card_path.read_text(encoding="utf-8"))
    if card.get("environment", {}).get("scikit_learn") != sklearn.__version__:
        raise RuntimeError(
            f"Model '{name}' was trained with scikit-learn "
            f"{card['environment']['scikit_learn']} but {sklearn.__version__} is installed. "
            "Retrain the model or install the matching version."
        )
    return joblib.load(model_path), card
