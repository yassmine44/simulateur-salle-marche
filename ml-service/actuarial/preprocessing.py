"""Feature pipelines.

Two encodings are provided because the two model families need different
inputs:

* GLM: a log-linear model can only learn monotone effects of a raw numeric
  variable. Driver age and vehicle age have U-shaped effects, so they are
  binned and one-hot encoded; density is log-transformed (its effect is
  roughly linear on the log scale).
* Gradient boosting: trees learn non-linearities themselves, so raw numeric
  values are kept and categories are ordinally encoded and declared as native
  categorical features.
"""

from __future__ import annotations

import numpy as np
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import (
    FunctionTransformer,
    KBinsDiscretizer,
    OneHotEncoder,
    OrdinalEncoder,
    StandardScaler,
)

from actuarial import config
from actuarial import schema as s

GLM_BINNED_FEATURES = (s.VEHICLE_AGE, s.DRIVER_AGE)
GLM_ONE_HOT_FEATURES = (s.VEHICLE_POWER, *s.CATEGORICAL_FEATURES)


def build_glm_preprocessor(n_bins: int = 10) -> ColumnTransformer:
    log_density = make_pipeline(
        FunctionTransformer(np.log, feature_names_out="one-to-one"),
        StandardScaler(),
    )
    return ColumnTransformer(
        transformers=[
            (
                "binned",
                KBinsDiscretizer(
                    n_bins=n_bins,
                    encode="onehot",
                    strategy="quantile",
                    subsample=200_000,
                    random_state=config.RANDOM_STATE,
                ),
                list(GLM_BINNED_FEATURES),
            ),
            (
                "onehot",
                OneHotEncoder(handle_unknown="ignore", min_frequency=100),
                list(GLM_ONE_HOT_FEATURES),
            ),
            ("bonus_malus", StandardScaler(), [s.BONUS_MALUS]),
            ("log_density", log_density, [s.DENSITY]),
        ],
        remainder="drop",
        sparse_threshold=1.0,
    )


def build_tree_preprocessor() -> ColumnTransformer:
    """Categorical columns come first so the model can locate them by index."""
    return ColumnTransformer(
        transformers=[
            (
                "categorical",
                OrdinalEncoder(
                    handle_unknown="use_encoded_value",
                    unknown_value=np.nan,
                    encoded_missing_value=np.nan,
                ),
                list(s.CATEGORICAL_FEATURES),
            ),
            ("numeric", "passthrough", list(s.NUMERIC_FEATURES)),
        ],
        remainder="drop",
        verbose_feature_names_out=False,
    )


def tree_categorical_mask() -> list[bool]:
    """Boolean mask matching the column order of `build_tree_preprocessor`."""
    return [True] * len(s.CATEGORICAL_FEATURES) + [False] * len(s.NUMERIC_FEATURES)
