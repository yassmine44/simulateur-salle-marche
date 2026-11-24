# Module d'Actuariat — ml-service

Implémentation du **§3.7 du cahier des charges** : modélisation de produits
d'assurance non-vie (tarification, survenance des sinistres, indemnisations,
marge de prime, provisionnement).

## Approche : fréquence × sévérité

```
Profil assuré + contrat
   ├─► Fréquence  λ(x)  = nombre de sinistres attendu par an      ← étape 1 (fait)
   ├─► Sévérité   μ(x)  = coût moyen d'un sinistre                ← étape 2
   │
   ├─► Prime pure        = λ(x) × μ(x)
   ├─► Prime commerciale = prime pure × (1 + chargements)
   ├─► Marge de prime    = primes encaissées − sinistres attendus
   └─► P(sinistre sur t) = 1 − exp(−λ(x)·t)
```

## Données

**freMTPL2** — portefeuille réel d'assurance responsabilité civile automobile
française (CASdatasets), jeu de référence de la littérature actuarielle
(Noll, Salzmann & Wüthrich, *Case Study: French MTPL Claims*, 2018).

| Table | OpenML | Lignes | Contenu |
|---|---|---|---|
| freMTPL2freq | 41214 | 678 013 contrats | profil, exposition, nombre de sinistres |
| freMTPL2sev | 41215 | 26 639 sinistres | montant de chaque sinistre |

Téléchargé automatiquement au premier entraînement dans `data/openml/` (ignoré par git).

**Nettoyage** (`data.py`, seuils justifiés dans `config.py`) : suppression
des guillemets ARFF, nombre de sinistres plafonné à 4, exposition à 1 an,
âge véhicule à 20, âge conducteur à 90, bonus-malus à 150. Validation stricte
du schéma (colonnes, nulls, unicité des contrats).

**Découpage** : 75 % entraînement / 25 % test, stratifié sur « au moins un
sinistre », graine fixe (reproductible).

## Étape 1 — Modèle de fréquence

Cible : `Frequency = ClaimNb / Exposure`, pondérée par `Exposure`,
perte de Poisson (équivalent au maximum de vraisemblance de
`N ~ Poisson(λ(x)·exposition)`).

### Candidats comparés

| Modèle | Rôle |
|---|---|
| `baseline` | Fréquence moyenne unique — tout modèle doit la battre |
| `poisson_glm` | GLM de Poisson, lien log — référence du marché, coefficients = relativités tarifaires |
| `poisson_gbm` | Gradient boosting à perte de Poisson — non-linéarités et interactions |

### Résultats (jeu de test, 169 504 contrats)

| Modèle | Déviance Poisson ↓ | D² expliqué ↑ | Gini ↑ | Calibration (prédit/observé) |
|---|---|---|---|---|
| baseline | 0.6245 | 0.000 | 0.00 | 0.999 |
| poisson_glm | 0.5927 | 0.051 | 0.283 | 1.002 |
| **poisson_gbm** (champion) | **0.5704** | **0.087** | **0.340** | 0.999 |

Lecture des métriques :

* **Déviance de Poisson / D²** — qualité d'ajustement. Un D² de quelques %
  est normal en assurance : la survenance d'un sinistre est majoritairement
  aléatoire, seule une partie est expliquée par le profil.
* **Gini** — capacité à classer les assurés du moins au plus risqué ; c'est
  ce qui rend un tarif compétitif.
* **Calibration** — le modèle prédit le bon nombre total de sinistres
  (solvabilité). Vérifiée aussi par décile de risque dans le rapport.

Écart train/test du champion : Gini 0.43 → 0.34 (léger sur-apprentissage
maîtrisé par early stopping ; amélioration possible par recherche
d'hyperparamètres en validation croisée). Le GLM ne présente aucun écart.

## Utilisation

```powershell
cd ml-service
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt

python -m actuarial.train_frequency            # entraînement complet (~30 s)
python -m actuarial.train_frequency --sample 100000
python -m pytest tests/actuarial               # 28 tests, données synthétiques
```

## Artefacts (`artifacts/actuarial/`)

| Fichier | Contenu |
|---|---|
| `frequency_model.joblib` | Pipeline champion (prétraitement + modèle) |
| `frequency_model.card.json` | Fiche modèle : version, empreinte des données, métriques, environnement |
| `frequency_report.json` | Comparaison des candidats, courbes de Lorenz, table de calibration (prêts pour le dashboard Angular) |

Le chargement (`registry.load_model`) refuse un modèle entraîné avec une
autre version de scikit-learn, pour éviter des prédictions silencieusement fausses.

## Architecture du code

```
actuarial/
├── config.py           seuils, chemins, graine
├── schema.py           contrat des colonnes (partagé avec l'API et Spring)
├── data.py             chargement, nettoyage, validation, découpage
├── preprocessing.py    encodage GLM (binning, one-hot, log) / arbres (ordinal)
├── metrics.py          déviance, D², Lorenz/Gini, calibration
├── frequency.py        candidats, benchmark, champion, P(sinistre)
├── registry.py         persistance versionnée + fiche modèle
└── train_frequency.py  CLI d'entraînement
tests/actuarial/        tests sur portefeuille synthétique au processus connu
```

## Feuille de route

1. ~~Données + modèle de fréquence~~
2. Modèle de sévérité (freMTPL2sev), prime pure/commerciale, marge de prime
3. Explicabilité SHAP (§3.6)
4. API FastAPI + endpoints Spring Boot
5. Écrans Angular
6. Provisionnement Chain-Ladder
7. Administration et intégration (stress test, formation)
