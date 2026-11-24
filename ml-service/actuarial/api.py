"""HTTP API of the actuarial module (consumed by the Spring Boot backend).

Endpoints (prefix /api/actuarial):

    GET  /status        models loaded, versions, pricing assumptions
    GET  /metadata      accepted values and labels of every rating factor
    POST /quote         annual tariff of a profile, optionally explained
    POST /compare       why profile A pays more or less than profile B
    GET  /reports/{n}   evaluation reports (frequency, severity, pricing, explainability)
    GET  /model-cards   lineage and metrics of the registered models

Design notes
------------
* The JSON contract is camelCase and independent of the training column
  names; the mapping lives in `PolicyProfile.to_row` only.
* Inputs are validated against the domains seen in training
  (`schema.CATEGORY_DOMAINS`, `schema.NUMERIC_DOMAINS`): an unknown category
  is rejected with 422 instead of being silently priced with a default effect.
* Models are loaded lazily once, behind a lock, and can be reloaded after a
  retraining. Missing artifacts yield 503 with an actionable message.
* The service is internal: it is only called by Spring Boot, which handles
  authentication, authorisation and traceability.
"""

from __future__ import annotations

import json
import os
import threading
from dataclasses import replace
from pathlib import Path
from typing import Any, Literal

import pandas as pd
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from actuarial import config
from actuarial import schema as s
from actuarial.explainability import PremiumExplainer, load_premium_explainer
from actuarial.pricing import (
    FREQUENCY_MODEL_NAME,
    PRICING_CONFIG_NAME,
    SEVERITY_MODEL_NAME,
    PricingAssumptions,
    PricingEngine,
    load_pricing_engine,
)
from actuarial.registry import model_paths
from actuarial.reserving import Triangle, TriangleError, chain_ladder

ReportName = Literal["frequency", "severity", "pricing", "explainability", "reserving"]

MAX_TRIANGLE_SIZE = 30

_FIELD_TO_COLUMN = {
    "area": s.AREA,
    "vehiclePower": s.VEHICLE_POWER,
    "vehicleAge": s.VEHICLE_AGE,
    "driverAge": s.DRIVER_AGE,
    "bonusMalus": s.BONUS_MALUS,
    "vehicleBrand": s.VEHICLE_BRAND,
    "vehicleGas": s.VEHICLE_GAS,
    "density": s.DENSITY,
    "region": s.REGION,
}
_COLUMN_TO_FIELD = {column: field for field, column in _FIELD_TO_COLUMN.items()}


# ----------------------------------------------------------------------------
# Request / response contract
# ----------------------------------------------------------------------------
def _range(column: str) -> dict[str, int]:
    low, high = s.NUMERIC_DOMAINS[column]
    return {"ge": low, "le": high}


class PolicyProfile(BaseModel):
    """Rating factors of one policyholder."""

    model_config = ConfigDict(extra="forbid")

    driverAge: int = Field(**_range(s.DRIVER_AGE), description="Âge du conducteur (ans)")
    bonusMalus: int = Field(**_range(s.BONUS_MALUS), description="50 = bonus max, 100 = neutre")
    vehicleAge: int = Field(**_range(s.VEHICLE_AGE), description="Âge du véhicule (ans)")
    vehiclePower: int = Field(**_range(s.VEHICLE_POWER), description="Puissance fiscale")
    density: int = Field(**_range(s.DENSITY), description="Habitants par km² de la commune")
    area: str
    vehicleBrand: str
    vehicleGas: str
    region: str

    @field_validator("area", "vehicleBrand", "vehicleGas", "region")
    @classmethod
    def _known_category(cls, value: str, info) -> str:
        allowed = s.CATEGORY_DOMAINS[_FIELD_TO_COLUMN[info.field_name]]
        if value not in allowed:
            raise ValueError(f"valeur inconnue '{value}', valeurs acceptées : {', '.join(allowed)}")
        return value

    def to_row(self) -> dict[str, Any]:
        return {column: getattr(self, field) for field, column in _FIELD_TO_COLUMN.items()}


class Assumptions(BaseModel):
    model_config = ConfigDict(extra="forbid")

    expenseRatio: float = Field(ge=0, lt=1, description="Frais, part de la prime commerciale")
    profitRatio: float = Field(ge=0, lt=1, description="Bénéfice, part de la prime commerciale")
    safetyLoading: float = Field(ge=0, le=1, description="Chargement de sécurité sur la prime pure")

    @model_validator(mode="after")
    def _ratios_below_one(self) -> "Assumptions":
        if self.expenseRatio + self.profitRatio >= 1:
            raise ValueError("expenseRatio + profitRatio doit être strictement inférieur à 1")
        return self

    def to_domain(self) -> PricingAssumptions:
        return PricingAssumptions(
            expense_ratio=self.expenseRatio,
            profit_ratio=self.profitRatio,
            safety_loading=self.safetyLoading,
        )


class AssumptionsView(Assumptions):
    commercialFactor: float


class QuoteRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    profile: PolicyProfile
    years: float = Field(1.0, gt=0, le=10, description="Horizon de la probabilité de sinistre")
    assumptions: Assumptions | None = None
    explain: bool = True
    top: int | None = Field(None, ge=1, le=len(s.FEATURES), description="Nombre de facteurs expliqués")


class Contribution(BaseModel):
    feature: str
    field: str
    label: str
    value: int | float | str
    phi: float
    relativity: float
    frequencyPhi: float
    severityPhi: float
    direction: Literal["hausse", "baisse", "neutre"]


class Explanation(BaseModel):
    basePremium: float
    purePremium: float
    contributions: list[Contribution]


class ModelVersions(BaseModel):
    frequency: str
    severity: str


class QuoteResponse(BaseModel):
    years: float
    frequency: float = Field(description="Sinistres indemnisés attendus par an")
    claimProbability: float = Field(description="P(au moins un sinistre sur `years` ans)")
    severity: float = Field(description="Coût moyen écrêté d'un sinistre")
    largeClaimLoading: float
    purePremium: float = Field(description="Prime pure annuelle")
    commercialPremium: float = Field(description="Prime commerciale annuelle")
    premiumMargin: float
    expectedLossRatio: float
    assumptions: AssumptionsView
    modelVersions: ModelVersions
    explanation: Explanation | None = None


class CompareRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    profileA: PolicyProfile
    profileB: PolicyProfile


class ComparisonContribution(BaseModel):
    feature: str
    field: str
    label: str
    valueA: int | float | str
    valueB: int | float | str
    phiDifference: float
    relativity: float


class ComparisonResponse(BaseModel):
    premiumA: float
    premiumB: float
    premiumRatio: float
    contributions: list[ComparisonContribution]


class ChainLadderRequest(BaseModel):
    """Cumulative run-off triangle supplied by the user.

    Row i holds the cumulative paid amounts of origin i; it may stop at the
    diagonal (n - i values) or be padded with nulls up to n columns.
    """

    model_config = ConfigDict(extra="forbid")

    origins: list[int] | None = Field(None, description="Années d'origine (défaut : 1..n)")
    triangle: list[list[float | None]] = Field(min_length=3, max_length=MAX_TRIANGLE_SIZE)
    confidenceLevel: float = Field(0.95, gt=0.5, lt=1)

    @model_validator(mode="after")
    def _consistent_origins(self) -> "ChainLadderRequest":
        if self.origins is not None and len(self.origins) != len(self.triangle):
            raise ValueError("origins doit contenir une année par ligne du triangle")
        return self

    def to_triangle(self) -> Triangle:
        n = len(self.triangle)
        values = []
        for i, row in enumerate(self.triangle):
            if len(row) > n:
                raise TriangleError(f"Ligne {i + 1} : {len(row)} valeurs pour un triangle de taille {n}.")
            values.append([float("nan") if v is None else float(v) for v in row] + [float("nan")] * (n - len(row)))
        origins = tuple(self.origins) if self.origins else tuple(range(1, n + 1))
        return Triangle(origins, values)


# ----------------------------------------------------------------------------
# Service
# ----------------------------------------------------------------------------
class ModelsNotAvailable(RuntimeError):
    pass


class ActuarialService:
    """Owns the loaded models; thread-safe lazy loading and reload."""

    def __init__(self, directory: Path) -> None:
        self.directory = directory
        self._lock = threading.Lock()
        self._engine: PricingEngine | None = None
        self._explainer: PremiumExplainer | None = None
        self._config: dict[str, Any] | None = None

    # -- loading -------------------------------------------------------------
    def _ensure_loaded(self) -> None:
        if self._engine is not None:
            return
        with self._lock:
            if self._engine is not None:
                return
            try:
                engine = load_pricing_engine(self.directory)
                explainer = load_premium_explainer(self.directory, engine)
                stored = json.loads((self.directory / PRICING_CONFIG_NAME).read_text(encoding="utf-8"))
            except FileNotFoundError as error:
                raise ModelsNotAvailable(str(error)) from error
            self._engine, self._explainer, self._config = engine, explainer, stored

    def reload(self) -> None:
        with self._lock:
            self._engine = self._explainer = self._config = None
        self._ensure_loaded()

    @property
    def engine(self) -> PricingEngine:
        self._ensure_loaded()
        return self._engine  # type: ignore[return-value]

    @property
    def explainer(self) -> PremiumExplainer:
        self._ensure_loaded()
        return self._explainer  # type: ignore[return-value]

    @property
    def versions(self) -> ModelVersions:
        self._ensure_loaded()
        return ModelVersions(
            frequency=self._config["frequency_model_version"],  # type: ignore[index]
            severity=self._config["severity_model_version"],  # type: ignore[index]
        )

    # -- use cases -------------------------------------------------------------
    def status(self) -> dict[str, Any]:
        engine = self.engine
        return {
            "status": "ok",
            "modelVersions": self.versions.model_dump(),
            "largeClaimLoading": engine.large_claim_loading,
            "assumptions": _assumptions_view(engine.assumptions).model_dump(),
        }

    def quote(self, request: QuoteRequest) -> QuoteResponse:
        engine = self.engine
        if request.assumptions is not None:
            engine = replace(engine, assumptions=request.assumptions.to_domain())
        profile = pd.DataFrame([request.profile.to_row()])
        quote = engine.quote(profile, years=request.years).iloc[0]

        explanation = None
        if request.explain:
            explained = self.explainer.explain(profile)[0].to_dict(top=request.top)
            explanation = Explanation(
                basePremium=explained["base_premium"],
                purePremium=explained["pure_premium"],
                contributions=[
                    Contribution(
                        feature=row["feature"],
                        field=_COLUMN_TO_FIELD[row["feature"]],
                        label=row["label"],
                        value=row["value"],
                        phi=row["phi"],
                        relativity=row["relativity"],
                        frequencyPhi=row["frequency_phi"],
                        severityPhi=row["severity_phi"],
                        direction=row["direction"],
                    )
                    for row in explained["contributions"]
                ],
            )

        return QuoteResponse(
            years=request.years,
            frequency=round(float(quote["frequency"]), 6),
            claimProbability=round(float(quote["claim_probability"]), 6),
            severity=round(float(quote["severity"]), 2),
            largeClaimLoading=round(engine.large_claim_loading, 6),
            purePremium=round(float(quote["pure_premium"]), 2),
            commercialPremium=round(float(quote["commercial_premium"]), 2),
            premiumMargin=round(float(quote["premium_margin"]), 2),
            expectedLossRatio=round(float(quote["expected_loss_ratio"]), 6),
            assumptions=_assumptions_view(engine.assumptions),
            modelVersions=self.versions,
            explanation=explanation,
        )

    def compare(self, request: CompareRequest) -> ComparisonResponse:
        result = self.explainer.compare(
            pd.DataFrame([request.profileA.to_row()]),
            pd.DataFrame([request.profileB.to_row()]),
        )
        return ComparisonResponse(
            premiumA=round(result["premium_a"], 2),
            premiumB=round(result["premium_b"], 2),
            premiumRatio=result["premium_ratio"],
            contributions=[
                ComparisonContribution(
                    feature=row["feature"],
                    field=_COLUMN_TO_FIELD[row["feature"]],
                    label=row["label"],
                    valueA=row["value_a"],
                    valueB=row["value_b"],
                    phiDifference=row["phi_difference"],
                    relativity=row["relativity"],
                )
                for row in result["contributions"]
            ],
        )

    def report(self, name: ReportName) -> dict[str, Any]:
        path = self.directory / f"{name}_report.json"
        if not path.exists():
            command = "actuarial.reserving_report" if name == "reserving" else "actuarial.train"
            raise ModelsNotAvailable(f"Rapport '{name}' introuvable. Lancez python -m {command}.")
        return json.loads(path.read_text(encoding="utf-8"))

    def model_cards(self) -> dict[str, Any]:
        cards = {}
        for name in (FREQUENCY_MODEL_NAME, SEVERITY_MODEL_NAME):
            _, card_path = model_paths(name, self.directory)
            if not card_path.exists():
                raise ModelsNotAvailable(
                    f"Fiche du modèle '{name}' introuvable. Lancez python -m actuarial.train."
                )
            cards[name] = json.loads(card_path.read_text(encoding="utf-8"))
        return cards


def _assumptions_view(assumptions: PricingAssumptions) -> AssumptionsView:
    return AssumptionsView(
        expenseRatio=assumptions.expense_ratio,
        profitRatio=assumptions.profit_ratio,
        safetyLoading=assumptions.safety_loading,
        commercialFactor=round(assumptions.commercial_factor, 6),
    )


def metadata() -> dict[str, Any]:
    """Form definition for the frontend: labels, ranges and allowed values."""
    fields = []
    for field, column in _FIELD_TO_COLUMN.items():
        entry: dict[str, Any] = {"field": field, "feature": column, "label": s.FEATURE_LABELS[column]}
        if column in s.CATEGORY_DOMAINS:
            entry.update(type="category", values=list(s.CATEGORY_DOMAINS[column]))
        else:
            low, high = s.NUMERIC_DOMAINS[column]
            entry.update(type="integer", min=low, max=high)
        fields.append(entry)
    return {
        "fields": fields,
        "defaultAssumptions": {
            "expenseRatio": config.EXPENSE_RATIO,
            "profitRatio": config.PROFIT_RATIO,
            "safetyLoading": config.SAFETY_LOADING,
        },
    }


# ----------------------------------------------------------------------------
# Router
# ----------------------------------------------------------------------------
_service: ActuarialService | None = None
_service_lock = threading.Lock()


def get_service() -> ActuarialService:
    global _service
    if _service is None:
        with _service_lock:
            if _service is None:
                directory = Path(os.environ.get("ACTUARIAL_ARTIFACTS_DIR", config.ARTIFACTS_DIR))
                _service = ActuarialService(directory)
    return _service


router = APIRouter(prefix="/api/actuarial", tags=["actuariat"])


def _unavailable(error: ModelsNotAvailable) -> HTTPException:
    return HTTPException(status_code=503, detail=str(error))


@router.get("/status")
def status(service: ActuarialService = Depends(get_service)) -> dict[str, Any]:
    try:
        return service.status()
    except ModelsNotAvailable as error:
        raise _unavailable(error) from error


@router.get("/metadata")
def get_metadata() -> dict[str, Any]:
    return metadata()


@router.post("/quote", response_model=QuoteResponse)
def quote(request: QuoteRequest, service: ActuarialService = Depends(get_service)) -> QuoteResponse:
    try:
        return service.quote(request)
    except ModelsNotAvailable as error:
        raise _unavailable(error) from error


@router.post("/compare", response_model=ComparisonResponse)
def compare(
    request: CompareRequest, service: ActuarialService = Depends(get_service)
) -> ComparisonResponse:
    try:
        return service.compare(request)
    except ModelsNotAvailable as error:
        raise _unavailable(error) from error


@router.get("/reports/{name}")
def report(name: ReportName, service: ActuarialService = Depends(get_service)) -> dict[str, Any]:
    try:
        return service.report(name)
    except ModelsNotAvailable as error:
        raise _unavailable(error) from error


@router.post("/reserving/chain-ladder")
def reserving_chain_ladder(request: ChainLadderRequest) -> dict[str, Any]:
    """Chain-Ladder reserves and Mack standard errors of a user triangle.

    Pure computation: no model to load, available even before training.
    """
    try:
        return chain_ladder(request.to_triangle()).to_dict(request.confidenceLevel)
    except TriangleError as error:
        raise HTTPException(status_code=422, detail=str(error)) from error


@router.get("/model-cards")
def model_cards(service: ActuarialService = Depends(get_service)) -> dict[str, Any]:
    try:
        return service.model_cards()
    except ModelsNotAvailable as error:
        raise _unavailable(error) from error
