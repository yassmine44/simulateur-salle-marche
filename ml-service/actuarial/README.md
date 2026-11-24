# Module d'Actuariat — ml-service

Implémentation du **§3.7 du cahier des charges** : modélisation de produits
d'assurance non-vie (tarification, survenance des sinistres, indemnisations,
marge de prime, provisionnement).

| Exigence du cahier des charges | Implémentation | État |
|---|---|---|
| Estimer les primes selon l'assuré et le risque | `pricing.PricingEngine` (prime pure et commerciale) | ✅ |
| Prédire la marge de prime (primes − sinistres attendus) | `pricing.portfolio_margin`, `premium_margin` par contrat | ✅ |
| Probabilité de survenance d'un sinistre | `frequency.claim_probability` | ✅ |
| Montant des indemnisations / provisionnement | `severity` (coût par sinistre) · Chain-Ladder | ✅ / étape 6 |

## Approche : fréquence × sévérité

```
Profil assuré + contrat
   ├─► Fréquence  λ(x)   sinistres indemnisés attendus par an   (GLM / GBM Poisson)
   ├─► Sévérité   μ(x)   coût moyen écrêté d'un sinistre        (GLM / GBM Gamma)
   ├─► Surcharge  L      sinistres graves mutualisés            (écrêtement au 99,5ᵉ centile)
   │
   ├─► Prime pure         P₀ = λ(x) · μ(x) · (1 + L)
   ├─► Prime commerciale  P  = P₀ · (1 + sécurité) / (1 − frais − bénéfice)
   ├─► Marge de prime     M  = P − P₀
   └─► P(sinistre sur t)     = 1 − exp(−λ(x)·t)
```

## Données

**freMTPL2** — portefeuille réel d'assurance responsabilité civile automobile
française (CASdatasets), jeu de référence de la littérature actuarielle
(Noll, Salzmann & Wüthrich, *Case Study: French MTPL Claims*, 2018).

| Table | OpenML | Lignes | Contenu |
|---|---|---|---|
| freMTPL2freq | 41214 | 678 013 contrats | profil, exposition, nombre de sinistres |
| freMTPL2sev | 41215 | 26 639 sinistres | montant de chaque sinistre |

Téléchargées automatiquement au premier entraînement dans `data/openml/` (ignoré par git).

### Traitements (`data.py`, seuils justifiés dans `config.py`)

* **Nettoyage** : guillemets ARFF supprimés ; plafonds : 4 sinistres, exposition
  1 an, âge véhicule 20, âge conducteur 90, bonus-malus 150. Validation stricte
  du schéma (colonnes, nulls, unicité des contrats, montants positifs).
* **Convention des sinistres indemnisés** : 9 116 contrats déclarent des
  sinistres sans montant (défaut connu de freMTPL2) et 195 sinistres ne
  correspondent à aucun contrat. Le nombre de sinistres est recalculé comme le
  nombre de sinistres *avec un montant* (36 056 déclarés → 26 405 indemnisés),
  sinon la prime pure serait surestimée d'environ un quart.
* **Écrêtement des sinistres graves** : montant médian 1 172 €, maximum
  4,07 M€. Chaque sinistre est écrêté au 99,5ᵉ centile des sinistres
  d'entraînement (34 793 €) ; l'excédent (28,4 % du coût) est mutualisé sur
  tout le portefeuille par une surcharge `L = excédent / coût écrêté = 39,6 %`.
* **Découpage** : 75 % / 25 % au niveau des contrats, stratifié sur « au moins
  un sinistre », graine fixe. Les sinistres, le seuil d'écrêtement et la table
  de sévérité sont dérivés de ce découpage : aucune fuite vers le test.

## Résultats (jeu de test : 169 504 contrats, 6 625 sinistres)

Métriques : déviance de la loi du modèle et D² (part de déviance expliquée)
pour l'ajustement ; **Gini** (courbe de Lorenz) pour la capacité à classer les
risques ; **calibration** (total prédit / total observé) pour la solvabilité.

### Fréquence (Poisson, pondération par l'exposition)

| Modèle | Déviance ↓ | D² ↑ | Gini ↑ | Calibration |
|---|---|---|---|---|
| baseline (fréquence moyenne) | 0.4804 | 0.000 | 0.00 | 0.993 |
| poisson_glm | 0.4601 | 0.042 | 0.278 | 0.992 |
| **poisson_gbm** (champion) | **0.4512** | **0.061** | **0.320** | 0.992 |

### Sévérité (Gamma, pondération par le nombre de sinistres)

| Modèle | Déviance ↓ | D² ↑ | Gini ↑ | Calibration |
|---|---|---|---|---|
| **baseline** (coût moyen, champion) | **1.1544** | −0.000 | −0.02 | 0.976 |
| gamma_glm (α = 0.1, choisi par validation croisée 5 plis) | 1.1554 | −0.001 | 0.02 | 0.971 |
| gamma_gbm | 1.1702 | −0.014 | 0.02 | 0.949 |

**Interprétation** : avec ces variables, le coût d'un sinistre n'est pas
prédictible (D² en validation croisée : 0,4 % ± 0,5 %). Le profil de
l'assuré explique *si* un sinistre survient, pas *combien* il coûte (qui
dépend des circonstances : matériel vs corporel). C'est un résultat connu sur
freMTPL2. La sélection automatique retient donc le coût moyen, ce qui est la
décision statistiquement correcte : un modèle plus complexe n'ajouterait
que du bruit. La différenciation tarifaire provient de la fréquence.

### Prime pure vs sinistralité réelle du test

| Indicateur | Valeur |
|---|---|
| Gini de la prime pure | **0.335** |
| Calibration hors sinistres graves | **0.968** |
| Calibration totale | 1.160 |
| S/P attendu / réalisé | 0.680 / 0.586 |

L'écart de calibration totale vient uniquement des sinistres graves : les
4 plus gros sinistres (4,07 M€, 1,40 M€, 1,30 M€, 0,77 M€) sont tous dans
l'entraînement. L'excédent pèse 28,4 % du coût en entraînement contre 14,2 %
en test. Hors excédent, le modèle est calibré à 3 % près. La surcharge est
donc prudente sur ce test ; c'est le rôle d'une surcharge de sinistres graves,
estimée sur peu d'événements extrêmes (amélioration possible : théorie des
valeurs extrêmes, loi de Pareto généralisée sur l'excédent).

### Exemples de tarifs (hypothèses : frais 25 %, bénéfice 5 %, sécurité 3 %)

| Profil | λ / an | P(sinistre 1 an) | Prime pure | Prime commerciale | Marge |
|---|---|---|---|---|---|
| Jeune conducteur urbain, malus | 0.255 | 22.5 % | 600 € | 883 € | 283 € |
| Conducteur expérimenté rural, bonus max | 0.051 | 5.0 % | 121 € | 177 € | 57 € |
| Conducteur senior, ville moyenne | 0.058 | 5.6 % | 135 € | 199 € | 64 € |

Le rapport `pricing_report.json` détaille aussi primes, sinistres attendus,
sinistres réels, marges et S/P par classe de risque, par zone et par tranche
de bonus-malus.

## Utilisation

```powershell
cd ml-service
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt

python -m actuarial.train                  # pipeline complet (~1 min)
python -m actuarial.train --sample 100000  # exécution rapide
python -m pytest tests/actuarial           # 60 tests, données synthétiques
```

```python
from actuarial.pricing import PricingAssumptions, load_pricing_engine

engine = load_pricing_engine()                       # hypothèses enregistrées
engine = load_pricing_engine(assumptions=PricingAssumptions(expense_ratio=0.20))
quotes = engine.quote(profiles_dataframe)            # une ligne par profil
```

## Artefacts (`artifacts/actuarial/`)

| Fichier | Contenu |
|---|---|
| `frequency_model.joblib` / `.card.json` | Champion fréquence + fiche modèle |
| `severity_model.joblib` / `.card.json` | Champion sévérité + fiche (seuil d'écrêtement) |
| `pricing_config.json` | Versions des modèles, écrêtement, hypothèses commerciales |
| `frequency_report.json`, `severity_report.json` | Comparaison des candidats, courbes de Lorenz, calibration par décile |
| `pricing_report.json` | Qualité de la prime pure, diagnostic sinistres graves, marges par segment, exemples |

Chaque fiche modèle contient la version, l'empreinte des données
d'entraînement, les métriques et l'environnement. Le chargement refuse un
modèle entraîné avec une autre version de scikit-learn.

## Architecture du code

```
actuarial/
├── config.py         seuils, chemins, graine, hypothèses commerciales
├── schema.py         contrat des colonnes (partagé avec l'API et Spring)
├── data.py           chargement, nettoyage, sinistres indemnisés, écrêtement, découpage
├── preprocessing.py  encodage GLM (binning, one-hot, log) / arbres (ordinal)
├── metrics.py        déviance Tweedie, D², Lorenz/Gini, calibration
├── modeling.py       banc d'essai générique : tâche, candidats, champion
├── frequency.py      candidats Poisson, P(sinistre)
├── severity.py       candidats Gamma
├── pricing.py        prime pure/commerciale, marge, rechargement du moteur
├── registry.py       persistance versionnée + fiche modèle
└── train.py          pipeline d'entraînement de bout en bout
tests/actuarial/      60 tests sur portefeuille synthétique au processus connu
```

## Feuille de route

1. ~~Données + modèle de fréquence~~
2. ~~Modèle de sévérité, prime pure/commerciale, marge de prime~~
3. Explicabilité SHAP (§3.6)
4. API FastAPI + endpoints Spring Boot
5. Écrans Angular
6. Provisionnement Chain-Ladder
7. Administration et intégration (stress test, formation)
