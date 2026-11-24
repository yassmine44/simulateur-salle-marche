"""Column names and feature contract of the actuarial models.

Keeping these names in one place guarantees that training, inference, the
FastAPI layer and the Spring Boot DTOs all speak the same language.
"""

from __future__ import annotations

# Identifiers, targets and weights
POLICY_ID = "IDpol"
CLAIM_COUNT = "ClaimNb"  # number of paid claims (claims with a recorded amount)
EXPOSURE = "Exposure"  # years of coverage
FREQUENCY = "Frequency"  # ClaimNb / Exposure: annualised claim frequency
CLAIM_AMOUNT = "ClaimAmount"  # total incurred amount of the policy (uncapped)
CAPPED_AMOUNT = "ClaimAmountCapped"  # total amount after large-claim capping
SEVERITY = "Severity"  # CAPPED_AMOUNT / ClaimNb: average capped cost per claim

# Rating factors (features)
AREA = "Area"  # A (rural) .. F (urban centre)
VEHICLE_POWER = "VehPower"
VEHICLE_AGE = "VehAge"
DRIVER_AGE = "DrivAge"
BONUS_MALUS = "BonusMalus"  # 50 = best class, 100 = neutral, >100 = malus
VEHICLE_BRAND = "VehBrand"
VEHICLE_GAS = "VehGas"  # Diesel | Regular
DENSITY = "Density"  # inhabitants per km2 of the driver's city
REGION = "Region"

NUMERIC_FEATURES: tuple[str, ...] = (
    VEHICLE_POWER,
    VEHICLE_AGE,
    DRIVER_AGE,
    BONUS_MALUS,
    DENSITY,
)
CATEGORICAL_FEATURES: tuple[str, ...] = (
    AREA,
    VEHICLE_BRAND,
    VEHICLE_GAS,
    REGION,
)
FEATURES: tuple[str, ...] = NUMERIC_FEATURES + CATEGORICAL_FEATURES

FEATURE_LABELS: dict[str, str] = {
    AREA: "Zone géographique",
    VEHICLE_POWER: "Puissance du véhicule",
    VEHICLE_AGE: "Âge du véhicule",
    DRIVER_AGE: "Âge du conducteur",
    BONUS_MALUS: "Coefficient bonus-malus",
    VEHICLE_BRAND: "Marque du véhicule",
    VEHICLE_GAS: "Carburant",
    DENSITY: "Densité de population",
    REGION: "Région",
}

# Values accepted at inference time. Categories are those seen in training
# (an unseen category would silently fall back to a default effect). Numeric
# ranges are wider than the training caps: values above a cap are clipped
# exactly as during training (see data.clip_rating_factors).
CATEGORY_DOMAINS: dict[str, tuple[str, ...]] = {
    AREA: ("A", "B", "C", "D", "E", "F"),
    VEHICLE_BRAND: ("B1", "B2", "B3", "B4", "B5", "B6", "B10", "B11", "B12", "B13", "B14"),
    VEHICLE_GAS: ("Diesel", "Regular"),
    REGION: (
        "R11", "R21", "R22", "R23", "R24", "R25", "R26", "R31", "R41", "R42", "R43",
        "R52", "R53", "R54", "R72", "R73", "R74", "R82", "R83", "R91", "R93", "R94",
    ),
}
NUMERIC_DOMAINS: dict[str, tuple[int, int]] = {
    VEHICLE_POWER: (4, 15),
    VEHICLE_AGE: (0, 100),
    DRIVER_AGE: (18, 100),
    BONUS_MALUS: (50, 230),
    DENSITY: (1, 30_000),
}

REQUIRED_POLICY_COLUMNS: frozenset[str] = frozenset(
    {POLICY_ID, CLAIM_COUNT, EXPOSURE, *FEATURES}
)
REQUIRED_CLAIM_COLUMNS: frozenset[str] = frozenset({POLICY_ID, CLAIM_AMOUNT})
