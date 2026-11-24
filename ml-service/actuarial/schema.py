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

REQUIRED_POLICY_COLUMNS: frozenset[str] = frozenset(
    {POLICY_ID, CLAIM_COUNT, EXPOSURE, *FEATURES}
)
REQUIRED_CLAIM_COLUMNS: frozenset[str] = frozenset({POLICY_ID, CLAIM_AMOUNT})
