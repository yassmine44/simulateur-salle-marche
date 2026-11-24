"""Static configuration of the actuarial module.

Every threshold below is a modelling decision. Each one is justified so that it
can be defended in front of a jury or reviewed by another actuary.
"""

from __future__ import annotations

from pathlib import Path

SERVICE_ROOT = Path(__file__).resolve().parent.parent

# OpenML caches the downloaded datasets here (git-ignored, ~25 MB).
DATA_HOME = SERVICE_ROOT / "data" / "openml"

# Trained models, model cards and evaluation reports.
ARTIFACTS_DIR = SERVICE_ROOT / "artifacts" / "actuarial"

RANDOM_STATE = 42
TEST_SIZE = 0.25

# --------------------------------------------------------------------------
# Data source: French Motor Third-Party Liability portfolio (freMTPL2),
# published with the R package CASdatasets and mirrored on OpenML.
# It is the reference dataset of the actuarial ML literature
# (Noll, Salzmann & Wuthrich, "Case Study: French Motor Third-Party
# Liability Claims", SSRN 3164764, 2018).
# --------------------------------------------------------------------------
FREMTPL2_FREQ_OPENML_ID = 41214  # 678 013 policies, one row per policy
FREMTPL2_SEV_OPENML_ID = 41215  # 26 639 claims, one row per claim

# --------------------------------------------------------------------------
# Cleaning thresholds (same choices as the reference case study).
# --------------------------------------------------------------------------
# A handful of policies report 5 to 16 claims, which are most likely data
# errors. They would dominate the Poisson likelihood, so they are capped.
CLAIM_COUNT_CAP = 4
# Exposure is expressed in years. A few contracts exceed one year, which is
# not possible for an annual policy record.
EXPOSURE_CAP = 1.0
# Sparse tails: very few observations above these values, so they are grouped
# with the last well-populated value to stabilise the GLM coefficients.
VEHICLE_AGE_CAP = 20
DRIVER_AGE_CAP = 90
BONUS_MALUS_CAP = 150

# --------------------------------------------------------------------------
# Large claims (écrêtement des sinistres graves).
# Claim amounts are extremely heavy-tailed: median 1 172 EUR, maximum
# 4.07 MEUR, and the top 1 % of claims carry ~31 % of the total cost. A few
# bodily-injury claims would otherwise drive the severity model. Standard
# practice: cap every claim at a high quantile, model the capped severity per
# risk profile, and spread the capped-off excess over the whole portfolio as a
# proportional large-claim loading (surcharge sinistres graves).
# --------------------------------------------------------------------------
LARGE_CLAIM_QUANTILE = 0.995

# --------------------------------------------------------------------------
# Commercial premium assumptions (hypothèses de tarification).
# Defaults are typical orders of magnitude for French motor insurance and are
# meant to be overridden by the platform administrator.
# --------------------------------------------------------------------------
EXPENSE_RATIO = 0.25  # management + acquisition costs, share of the commercial premium
PROFIT_RATIO = 0.05  # target profit, share of the commercial premium
SAFETY_LOADING = 0.03  # prudence margin on the pure premium (model uncertainty)
