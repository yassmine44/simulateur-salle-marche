"""Train, benchmark and register the claim frequency model.

Usage (from ml-service/):

    python -m actuarial.train_frequency
    python -m actuarial.train_frequency --sample 100000   # quick run

Outputs in artifacts/actuarial/:

    frequency_model.joblib       champion pipeline
    frequency_model.card.json    model card (lineage, metrics, environment)
    frequency_report.json        candidate comparison, Lorenz curves and
                                 calibration table, ready to be plotted by
                                 the Angular dashboard
"""

from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

import numpy as np

from actuarial import config
from actuarial import schema as s
from actuarial.data import fingerprint, load_policies, portfolio_summary, split_policies
from actuarial.frequency import benchmark_candidates, select_champion
from actuarial.metrics import calibration_table, lorenz_curve
from actuarial.registry import ModelCard, save_model

MODEL_NAME = "frequency_model"
REPORT_NAME = "frequency_report.json"
LORENZ_POINTS = 101

logger = logging.getLogger("actuarial.train_frequency")


def _sampled_lorenz(frequency_true, frequency_pred, exposure) -> dict[str, list[float]]:
    """Lorenz curve resampled on a fixed grid, light enough for a web chart."""
    x, y = lorenz_curve(frequency_true, frequency_pred, exposure)
    grid = np.linspace(0.0, 1.0, LORENZ_POINTS)
    return {
        "cumulative_exposure": grid.round(4).tolist(),
        "cumulative_claims": np.interp(grid, x, y).round(6).tolist(),
    }


def run(sample: int | None = None, artifacts_dir: Path = config.ARTIFACTS_DIR) -> dict:
    policies = load_policies()
    if sample:
        policies = policies.sample(n=min(sample, len(policies)), random_state=config.RANDOM_STATE)
    logger.info("Portfolio: %s", portfolio_summary(policies))

    split = split_policies(policies)
    results = benchmark_candidates(split)
    champion = select_champion(results)
    logger.info("Champion: %s", champion.candidate.name)

    test = split.test
    calibration = calibration_table(test[s.FREQUENCY], champion.test_predictions, test[s.EXPOSURE])

    card = ModelCard(
        name=MODEL_NAME,
        task="claim_frequency",
        algorithm=champion.candidate.algorithm,
        target=f"{s.FREQUENCY} = {s.CLAIM_COUNT} / {s.EXPOSURE} (sinistres par an)",
        weight=s.EXPOSURE,
        features=list(s.FEATURES),
        data_source=f"freMTPL2freq (OpenML {config.FREMTPL2_FREQ_OPENML_ID})",
        data_fingerprint=fingerprint(policies),
        train_summary=portfolio_summary(split.train),
        test_summary=portfolio_summary(test),
        metrics=champion.test_metrics.to_dict(),
        candidates=[result.summary() for result in results],
    )
    model_path, card_path = save_model(champion.model, card, artifacts_dir)

    report = {
        "model_version": card.version,
        "champion": champion.candidate.name,
        "candidates": card.candidates,
        "lorenz_curves": {
            "oracle": _sampled_lorenz(test[s.FREQUENCY], test[s.FREQUENCY], test[s.EXPOSURE]),
            **{
                result.candidate.name: _sampled_lorenz(
                    test[s.FREQUENCY], result.test_predictions, test[s.EXPOSURE]
                )
                for result in results
            },
        },
        "calibration": calibration.round(6).to_dict(orient="records"),
    }
    report_path = artifacts_dir / REPORT_NAME
    report_path.write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")

    logger.info("Saved %s, %s and %s", model_path.name, card_path.name, report_path.name)
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--sample", type=int, default=None, help="Train on a random subsample.")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s | %(message)s")
    run(sample=args.sample)


if __name__ == "__main__":
    main()
