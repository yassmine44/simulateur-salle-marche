"""End-to-end training pipeline of the actuarial pricing models.

Usage (from ml-service/):

    python -m actuarial.train
    python -m actuarial.train --sample 100000   # quick run

Stages, all on the same policy-level train/test split:

1. frequency  benchmark Poisson models, register the champion
2. severity   fit the large-claim cap on train claims, benchmark Gamma
              models on capped severities, register the champion
3. pricing    combine both champions into pure and commercial premiums,
              evaluate them against the actual claims of the test portfolio
              and compute premium margins by segment
4. explain    exact Shapley values of the premium: global importance,
              dependence, example explanations and a pairwise comparison

Outputs in artifacts/actuarial/:

    frequency_model.joblib / .card.json   champion + model card
    severity_model.joblib  / .card.json   champion + model card
    pricing_config.json                   large-claim cap and commercial assumptions
    frequency_report.json                 candidates, Lorenz curves, calibration
    severity_report.json                  idem
    pricing_report.json                   premium accuracy, margins, example quotes
    explainer_background.csv              reference sample of the Shapley explanations
    explainability_report.json            global and local explanations (XAI)
"""

from __future__ import annotations

import argparse
import json
import logging
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from actuarial import config
from actuarial import schema as s
from actuarial.data import (
    LargeClaimCap,
    build_severity_table,
    fingerprint,
    load_portfolio,
    portfolio_summary,
    split_policies,
)
from actuarial.explainability import (
    BACKGROUND_SIZE,
    GLOBAL_SAMPLE_SIZE,
    PremiumExplainer,
    build_explainability_report,
    sample_background,
    save_background,
)
from actuarial.frequency import FREQUENCY_TASK, build_frequency_candidates
from actuarial.metrics import calibration_table, evaluate, sampled_lorenz
from actuarial.modeling import CandidateResult, Task, benchmark_candidates, select_champion
from actuarial.pricing import (
    FREQUENCY_MODEL_NAME,
    PRICING_CONFIG_NAME,
    SEVERITY_MODEL_NAME,
    PricingAssumptions,
    PricingEngine,
    portfolio_margin,
)
from actuarial.registry import ModelCard, save_model
from actuarial.severity import SEVERITY_TASK, build_severity_candidates

logger = logging.getLogger("actuarial.train")

# Tweedie power used to score the pure premium against the observed loss cost
# (compound Poisson-Gamma: many zeros and a positive skewed part).
PURE_PREMIUM_POWER = 1.9

ARCHETYPES: dict[str, dict[str, Any]] = {
    "Jeune conducteur urbain, malus": {
        s.DRIVER_AGE: 20, s.BONUS_MALUS: 100, s.DENSITY: 5000, s.AREA: "E",
        s.VEHICLE_POWER: 7, s.VEHICLE_AGE: 2, s.VEHICLE_BRAND: "B12",
        s.VEHICLE_GAS: "Regular", s.REGION: "R11",
    },
    "Conducteur expérimenté rural, bonus maximal": {
        s.DRIVER_AGE: 50, s.BONUS_MALUS: 50, s.DENSITY: 50, s.AREA: "A",
        s.VEHICLE_POWER: 6, s.VEHICLE_AGE: 8, s.VEHICLE_BRAND: "B1",
        s.VEHICLE_GAS: "Diesel", s.REGION: "R24",
    },
    "Conducteur senior, ville moyenne": {
        s.DRIVER_AGE: 72, s.BONUS_MALUS: 50, s.DENSITY: 800, s.AREA: "C",
        s.VEHICLE_POWER: 5, s.VEHICLE_AGE: 5, s.VEHICLE_BRAND: "B2",
        s.VEHICLE_GAS: "Regular", s.REGION: "R82",
    },
}


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def _records(frame: pd.DataFrame) -> list[dict[str, Any]]:
    return json.loads(frame.round(6).to_json(orient="records", force_ascii=False))


def _model_report(
    task: Task, results: list[CandidateResult], champion: CandidateResult, test: pd.DataFrame
) -> dict[str, Any]:
    y, w = test[task.target], test[task.weight]
    return {
        "task": task.name,
        "champion": champion.candidate.name,
        "candidates": [r.summary() for r in results],
        "lorenz_curves": {
            "oracle": sampled_lorenz(y, y, w),
            **{r.candidate.name: sampled_lorenz(y, r.test_predictions, w) for r in results},
        },
        "calibration": _records(calibration_table(y, champion.test_predictions, w)),
    }


def _register(
    task: Task,
    champion: CandidateResult,
    results: list[CandidateResult],
    train: pd.DataFrame,
    test: pd.DataFrame,
    data_source: str,
    data_fingerprint: str,
    artifacts_dir: Path,
    name: str,
    parameters: dict[str, Any] | None = None,
) -> ModelCard:
    card = ModelCard(
        name=name,
        task=task.name,
        algorithm=champion.candidate.algorithm,
        target=task.target,
        weight=task.weight,
        features=list(s.FEATURES),
        data_source=data_source,
        data_fingerprint=data_fingerprint,
        train_summary={"rows": len(train), "total_weight": round(float(train[task.weight].sum()), 2)},
        test_summary={"rows": len(test), "total_weight": round(float(test[task.weight].sum()), 2)},
        metrics=champion.test_metrics.to_dict(),
        candidates=[r.summary() for r in results],
        parameters=parameters or {},
    )
    save_model(champion.model, card, artifacts_dir)
    return card


def _bonus_malus_band(bonus_malus: pd.Series) -> pd.Series:
    return pd.cut(
        bonus_malus,
        bins=[0, 50, 75, 100, np.inf],
        labels=["50 (bonus max)", "51-75", "76-100", ">100 (malus)"],
    )


def train_pipeline(
    policies: pd.DataFrame,
    claims: pd.DataFrame,
    artifacts_dir: Path = config.ARTIFACTS_DIR,
    assumptions: PricingAssumptions = PricingAssumptions(),
    background_size: int = BACKGROUND_SIZE,
    explain_sample_size: int = GLOBAL_SAMPLE_SIZE,
) -> dict[str, Any]:
    artifacts_dir.mkdir(parents=True, exist_ok=True)
    data_fingerprint = fingerprint(policies, claims)
    source = (
        f"freMTPL2freq (OpenML {config.FREMTPL2_FREQ_OPENML_ID}) + "
        f"freMTPL2sev (OpenML {config.FREMTPL2_SEV_OPENML_ID}), sinistres indemnisés"
    )
    split = split_policies(policies)
    logger.info("Train portfolio: %s", portfolio_summary(split.train))
    logger.info("Test portfolio:  %s", portfolio_summary(split.test))

    # 1. Frequency ------------------------------------------------------------
    freq_results = benchmark_candidates(
        FREQUENCY_TASK, split.train, split.test, build_frequency_candidates()
    )
    freq_champion = select_champion(freq_results)
    freq_card = _register(
        FREQUENCY_TASK, freq_champion, freq_results, split.train, split.test,
        source, data_fingerprint, artifacts_dir, FREQUENCY_MODEL_NAME,
    )
    _write_json(
        artifacts_dir / "frequency_report.json",
        {"model_version": freq_card.version,
         **_model_report(FREQUENCY_TASK, freq_results, freq_champion, split.test)},
    )

    # 2. Severity -------------------------------------------------------------
    train_claims = claims[claims[s.POLICY_ID].isin(split.train[s.POLICY_ID])]
    cap = LargeClaimCap.fit(train_claims[s.CLAIM_AMOUNT])
    logger.info(
        "Large-claim cap: threshold=%.0f EUR, excess share=%.1f%%, loading=%.1f%%",
        cap.threshold, 100 * cap.excess_share, 100 * cap.loading,
    )
    sev_train = build_severity_table(split.train, claims, cap)
    sev_test = build_severity_table(split.test, claims, cap)
    sev_results = benchmark_candidates(
        SEVERITY_TASK, sev_train, sev_test, build_severity_candidates()
    )
    sev_champion = select_champion(sev_results)
    sev_card = _register(
        SEVERITY_TASK, sev_champion, sev_results, sev_train, sev_test,
        source, data_fingerprint, artifacts_dir, SEVERITY_MODEL_NAME,
        parameters={"large_claim_cap": cap.to_dict()},
    )
    _write_json(
        artifacts_dir / "severity_report.json",
        {"model_version": sev_card.version, "large_claim_cap": cap.to_dict(),
         **_model_report(SEVERITY_TASK, sev_results, sev_champion, sev_test)},
    )

    # 3. Pricing --------------------------------------------------------------
    engine = PricingEngine(freq_champion.model, sev_champion.model, cap.loading, assumptions)
    test = split.test
    quotes = engine.quote(test)
    loss_cost = test[s.CLAIM_AMOUNT] / test[s.EXPOSURE]
    pure_metrics = evaluate(loss_cost, quotes["pure_premium"], test[s.EXPOSURE], PURE_PREMIUM_POWER)
    logger.info(
        "[pricing] pure premium vs actual loss cost: D2=%.4f  Gini=%.4f  calibration=%.4f",
        pure_metrics.d2_explained, pure_metrics.gini, pure_metrics.calibration_ratio,
    )

    # Large claims are rare and volatile: a handful of them can move the
    # realised loss ratio by several points. Separating the capped part (what
    # the frequency and severity models predict) from the excess (what the
    # loading covers) shows where a gap between expected and actual comes from.
    test_claims = claims[claims[s.POLICY_ID].isin(test[s.POLICY_ID])]
    test_capped_total = float(test_claims[s.CLAIM_AMOUNT].clip(upper=cap.threshold).sum())
    capped_expected_total = float(
        (quotes["frequency"] * quotes["severity"] * test[s.EXPOSURE]).sum()
    )
    large_claims_diagnostic = {
        "threshold": cap.threshold,
        "train_excess_share": round(cap.excess_share, 6),
        "test_excess_share": round(1 - test_capped_total / float(test_claims[s.CLAIM_AMOUNT].sum()), 6),
        "capped_calibration_ratio": round(capped_expected_total / test_capped_total, 6),
        "largest_train_claims": train_claims[s.CLAIM_AMOUNT].nlargest(5).round(2).tolist(),
        "largest_test_claims": test_claims[s.CLAIM_AMOUNT].nlargest(5).round(2).tolist(),
    }
    logger.info(
        "[pricing] calibration excluding large claims=%.4f (excess share train=%.1f%%, test=%.1f%%)",
        large_claims_diagnostic["capped_calibration_ratio"],
        100 * large_claims_diagnostic["train_excess_share"],
        100 * large_claims_diagnostic["test_excess_share"],
    )

    risk_class = pd.qcut(quotes["pure_premium"].rank(method="first"), q=10, labels=False) + 1
    archetypes = pd.DataFrame.from_dict(ARCHETYPES, orient="index")
    archetype_quotes = engine.quote(archetypes)
    archetype_quotes.insert(0, "profile", archetype_quotes.index)

    pricing_config = {
        "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "frequency_model_version": freq_card.version,
        "severity_model_version": sev_card.version,
        "large_claim_cap": cap.to_dict(),
        "assumptions": assumptions.to_dict(),
        "currency": dict(config.DEFAULT_CURRENCY),
    }
    _write_json(artifacts_dir / PRICING_CONFIG_NAME, pricing_config)

    pricing_report = {
        **pricing_config,
        "pure_premium_metrics": pure_metrics.to_dict(),
        "large_claims_diagnostic": large_claims_diagnostic,
        "pure_premium_lorenz": {
            "oracle": sampled_lorenz(loss_cost, loss_cost, test[s.EXPOSURE]),
            "model": sampled_lorenz(loss_cost, quotes["pure_premium"], test[s.EXPOSURE]),
        },
        "margin": {
            "portfolio": _records(portfolio_margin(test, quotes)),
            "by_risk_class": _records(portfolio_margin(test, quotes, by=risk_class)),
            "by_area": _records(portfolio_margin(test, quotes, by=s.AREA)),
            "by_bonus_malus": _records(
                portfolio_margin(test, quotes, by=_bonus_malus_band(test[s.BONUS_MALUS]))
            ),
        },
        "example_quotes": _records(archetype_quotes.reset_index(drop=True)),
    }
    _write_json(artifacts_dir / "pricing_report.json", pricing_report)

    # 4. Explainability ---------------------------------------------------------
    background = sample_background(split.train, size=background_size)
    save_background(background, artifacts_dir)
    explainer = PremiumExplainer(engine, background)
    global_sample = sample_background(
        test, size=explain_sample_size, random_state=config.RANDOM_STATE + 1
    )
    explainability_report = build_explainability_report(explainer, global_sample, archetypes)
    _write_json(artifacts_dir / "explainability_report.json", explainability_report)
    top = explainability_report["global_importance"]["premium"][:3]
    logger.info(
        "[explainability] top premium drivers: %s",
        ", ".join(f"{row['feature']} ({100 * row['share']:.0f}%)" for row in top),
    )

    margin = pricing_report["margin"]["portfolio"][0]
    logger.info(
        "[pricing] test portfolio: premiums=%.0f  expected claims=%.0f  actual claims=%.0f  "
        "expected S/P=%.3f  actual S/P=%.3f",
        margin["premiums"], margin["expected_claims"], margin["actual_claims"],
        margin["expected_loss_ratio"], margin["actual_loss_ratio"],
    )
    return {
        "frequency_champion": freq_champion.candidate.name,
        "severity_champion": sev_champion.candidate.name,
        "large_claim_cap": cap.to_dict(),
        "pure_premium_metrics": pure_metrics.to_dict(),
        "large_claims_diagnostic": large_claims_diagnostic,
        "portfolio_margin": margin,
    }


def run(sample: int | None = None, artifacts_dir: Path = config.ARTIFACTS_DIR) -> dict[str, Any]:
    policies, claims = load_portfolio()
    if sample:
        policies = policies.sample(n=min(sample, len(policies)), random_state=config.RANDOM_STATE)
        claims = claims[claims[s.POLICY_ID].isin(policies[s.POLICY_ID])]
    return train_pipeline(policies, claims, artifacts_dir)


def main() -> None:
    parser = argparse.ArgumentParser(description="Train the actuarial pricing models.")
    parser.add_argument("--sample", type=int, default=None, help="Train on a random subsample.")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s | %(message)s")
    run(sample=args.sample)


if __name__ == "__main__":
    main()
