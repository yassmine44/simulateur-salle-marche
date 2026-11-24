"""Actuarial pricing module (Module d'Actuariat, cahier des charges §3.7).

The module follows the classical frequency x severity decomposition used in
non-life insurance pricing:

    pure premium = E[claim count per year] x E[cost per claim]

Sub-modules
-----------
config          Paths, seeds and data-cleaning thresholds.
schema          Column names and the feature contract shared by every model.
data            Loading, cleaning and validation of the freMTPL2 portfolio.
preprocessing   Feature pipelines (GLM-friendly and tree-friendly).
metrics         Actuarial evaluation metrics (deviance, Gini, calibration).
frequency       Claim frequency models and claim probability inference.
registry        Versioned persistence of trained models and their model cards.
"""
