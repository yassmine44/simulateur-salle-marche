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
| §3.6 Explainable AI : contributions, sens, explications locales/globales | `explainability` (valeurs de Shapley exactes) | ✅ |

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

## Explicabilité (XAI, §3.6)

### Méthode : valeurs de Shapley exactes, échelle logarithmique

La contribution φⱼ d'une variable à la prime d'un assuré est sa **valeur de
Shapley** : son apport marginal moyen sur toutes les coalitions des autres
variables, les variables absentes étant tirées d'un échantillon de référence
de 200 contrats (définition « interventionnelle », celle de `shap`).

* **Exacte, pas approximée** : avec 9 variables métier, les 2⁹ = 512
  coalitions sont énumérées. Fonctionne pour tout modèle (GLM, gradient
  boosting à splits catégoriels natifs — non pris en charge par
  `shap.TreeExplainer` —, modèle constant) et directement au niveau des
  variables métier, pas des colonnes encodées.
* **Échelle logarithmique** (lien des modèles) : les contributions sont
  additives en log et donc **multiplicatives sur la prime** :
  `prime = prime de base × Πⱼ exp(φⱼ)`. `exp(φⱼ)` est une **relativité
  tarifaire** (« le bonus-malus multiplie la prime par 2,18 »), le langage des
  actuaires et du régulateur. La prime pure étant λ·μ·(1+L), ses contributions
  sont la somme de celles des modèles de fréquence et de sévérité.
* **Propriétés vérifiées par les tests** : égalité avec la solution
  analytique d'un modèle log-additif (à 1e-10), efficience
  (`base + Σφ = log f(x)` exactement), variable inutilisée ⇒ φ = 0,
  interaction partagée entre les deux variables (symétrie).
* **Performance** : ~0,35 s par explication (une prédiction groupée de
  102 400 lignes) ; contributions nulles sans calcul pour un modèle constant
  (axiome « dummy »).

### Ce que le système permet de répondre (exigences §3.6.1)

| Question | Fonction |
|---|---|
| Quelles variables ont le plus contribué à la prime de cet assuré, et dans quel sens ? | `PremiumExplainer.explain` → relativité + `hausse` / `baisse` / `neutre` |
| Pourquoi l'assuré A paie-t-il plus que B ? | `PremiumExplainer.compare` (les écarts se multiplient exactement en ratio de primes) |
| Quelles variables pèsent le plus sur le portefeuille ? | `global_importance` (moyenne des \|φ\|) |
| Comment une variable fait-elle varier la prime ? | `dependence` (relativité moyenne par valeur) |

### Résultats (200 contrats du test)

| Rang | Variable | Part de l'importance |
|---|---|---|
| 1 | Coefficient bonus-malus | 39,3 % |
| 2 | Âge du conducteur | 13,4 % |
| 3 | Densité de population | 10,7 % |
| 4 | Âge du véhicule | 9,6 % |
| 5 | Carburant | 8,7 % |
| 6 | Région | 8,4 % |
| 7 | Marque du véhicule | 6,1 % |
| 8 | Puissance du véhicule | 3,1 % |
| 9 | Zone géographique | 0,7 % |

* La **zone** pèse peu car elle mesure presque la même chose que la densité,
  déjà utilisée par le modèle : le crédit va à la variable que le modèle exploite.
* L'**âge du conducteur** a un effet en U avec une bosse vers 50 ans
  (×1,45 à 20 ans, ×0,71 à 30 ans, ×1,20 à 50 ans), phénomène connu sur
  freMTPL2 (interprétation courante : conduite occasionnelle du véhicule
  parental par les enfants).
* Toute l'explication vient de la fréquence : la sévérité retenue est
  constante (voir plus haut), ses contributions sont nulles.

**Comparaison** — le jeune conducteur urbain en malus paie **4,98×** la prime
du conducteur expérimenté rural : bonus-malus ×2,76, âge ×1,52, densité ×1,31,
région ×1,20, marque ×0,75.

## Intégration (API)

```
Angular ──HTTPS, session + CSRF──► Spring Boot /api/actuarial/*  ──HTTP interne──► ml-service /api/actuarial/*
                                   authentification, validation,                     modèles, tarification,
                                   traçabilité (table actuarial_quotes)              explications XAI
```

### Spring Boot — API publique (utilisateur authentifié)

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/api/actuarial/metadata` | Définition du formulaire : libellés, bornes, valeurs acceptées |
| GET | `/api/actuarial/status` | Versions des modèles, hypothèses en vigueur |
| POST | `/api/actuarial/quotes` | Devis expliqué ; tracé au nom de l'utilisateur |
| GET | `/api/actuarial/quotes/me?limit=20` | Historique des devis de l'utilisateur (max 100) |
| POST | `/api/actuarial/comparisons` | Pourquoi A paie plus que B |
| GET | `/api/actuarial/reports/{frequency\|severity\|pricing\|explainability}` | Rapports d'évaluation |
| GET | `/api/actuarial/model-cards` | Fiches des modèles |

Exemple de requête :

```json
POST /api/actuarial/quotes
{
  "profile": {"driverAge": 20, "bonusMalus": 100, "vehicleAge": 2, "vehiclePower": 7,
              "density": 5000, "area": "E", "vehicleBrand": "B12",
              "vehicleGas": "Regular", "region": "R11"},
  "years": 1,
  "assumptions": {"expenseRatio": 0.25, "profitRatio": 0.05, "safetyLoading": 0.03},
  "explain": true,
  "top": 3
}
```

Réponse (extrait) : `purePremium` 600,23 · `commercialPremium` 883,19 ·
`premiumMargin` 282,96 · `claimProbability` 0,225 · `explanation.contributions` :
bonus-malus ×2,18 (hausse), âge ×1,81 (hausse), marque ×0,76 (baisse).

**Erreurs** (format `ProblemDetail`) : 400 champ invalide (message en
français par champ), 401 non authentifié, 404 rapport inconnu, 503 moteur
injoignable ou modèles non entraînés (avec la consigne), 502 réponse
inattendue du moteur.

**Traçabilité (§4)** : chaque devis est enregistré avec l'utilisateur, la
date, le profil, les résultats, les hypothèses et les versions des modèles.
Les modèles étant versionnés et déterministes, tout devis est reproductible.

**Configuration** (`application.properties`) :
`app.actuarial.ml-service.base-url` (variable `ACTUARIAL_ML_URL`, défaut
`http://localhost:5000`), délais de connexion 2 s et de réponse 10 s.

### ml-service — API interne

Mêmes cas d'usage sous `/api/actuarial/` (`status`, `metadata`, `quote`,
`compare`, `reports/{name}`, `model-cards`). Contrat JSON en camelCase,
indépendant des noms de colonnes d'entraînement. Validation stricte : une
catégorie jamais vue à l'entraînement est refusée (422) au lieu d'être
tarifée avec un effet par défaut ; les valeurs au-delà des plafonds
d'entraînement sont plafonnées comme à l'entraînement (pas d'écart
entraînement/production). Modèles chargés une fois, de façon thread-safe.
Latence mesurée d'un devis expliqué : ~0,8 s.

### Interface Angular (`frontend/src/app/features/actuarial/`)

Route `/app/actuarial`, lien « Actuariat » dans le menu, chargée à la demande.

| Onglet | Contenu |
|---|---|
| **Tarification** | Formulaire de profil (profils types, bornes issues de `/metadata`), hypothèses commerciales et horizon, prime commerciale, prime pure, marge, probabilité de sinistre, explication facteur par facteur avec phrase de lecture, historique des devis |
| **Comparaison** | Deux profils côte à côte ; décomposition du ratio des primes A/B |
| **Portefeuille et modèles** | Gini, calibration, S/P attendu/réalisé, surcharge sinistres graves ; comparaison des modèles ; courbe de Lorenz ; calibration par décile ; importance des variables ; effet d'une variable ; marges par segment |

Graphiques en SVG/CSS, sans dépendance : barres divergentes de relativités
(échelle logarithmique, ×2 et ×0,5 symétriques), barres d'importance, lignes
génériques (Lorenz, calibration, dépendance). Chacun a une info-bulle au
survol et au clavier, une vue tableau et des libellés lisibles par lecteur
d'écran. Couleurs validées (contraste ≥ 3:1, séparation daltonisme ΔE ≥ 24)
en thème clair et sombre. Les variables continues sont regroupées en déciles
dans les courbes de dépendance, pour que chaque point repose sur assez de
contrats.

Vérification visuelle effectuée dans Chrome (clair, sombre, mobile 390 px)
sur les vrais modèles ; elle a permis de corriger un rechargement de page à
la soumission et un débordement horizontal sur mobile, désormais couverts par
les tests et les styles.

### Tests de l'intégration

* Python : 27 tests d'API (`tests/actuarial/test_api.py`) — cas nominaux,
  9 cas de validation, plafonnement, 503 sans modèles, rechargement.
* Angular : 36 tests (`ng test --include='src/app/features/actuarial/**/*.spec.ts'`) —
  échelles des graphiques, formats, service HTTP et cache, formulaire,
  composants graphiques, page de tarification (soumission par le vrai bouton,
  conversion des hypothèses, erreurs).
* Java : 24 tests — client HTTP avec `MockRestServiceServer` (traduction de
  chaque erreur, champs transmis), service (traçabilité, historique borné),
  contrôleur (authentification sur chaque route), validation des DTO. Le
  JSON de test du client est la réponse réelle du ml-service.

## Utilisation

```powershell
cd ml-service
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt

python -m actuarial.train                  # pipeline complet (~1 min)
python -m actuarial.train --sample 100000  # exécution rapide
python -m pytest tests/actuarial           # 102 tests, données synthétiques
```

```python
from actuarial.pricing import PricingAssumptions, load_pricing_engine
from actuarial.explainability import load_premium_explainer

engine = load_pricing_engine()                       # hypothèses enregistrées
engine = load_pricing_engine(assumptions=PricingAssumptions(expense_ratio=0.20))
quotes = engine.quote(profiles_dataframe)            # une ligne par profil

explainer = load_premium_explainer()
explainer.explain(profiles_dataframe)[0].to_dict(top=5)
explainer.compare(profile_a, profile_b)
```

## Artefacts (`artifacts/actuarial/`)

| Fichier | Contenu |
|---|---|
| `frequency_model.joblib` / `.card.json` | Champion fréquence + fiche modèle |
| `severity_model.joblib` / `.card.json` | Champion sévérité + fiche (seuil d'écrêtement) |
| `pricing_config.json` | Versions des modèles, écrêtement, hypothèses commerciales |
| `frequency_report.json`, `severity_report.json` | Comparaison des candidats, courbes de Lorenz, calibration par décile |
| `pricing_report.json` | Qualité de la prime pure, diagnostic sinistres graves, marges par segment, exemples |
| `explainer_background.csv` | Échantillon de référence des explications (200 contrats) |
| `explainability_report.json` | Importance globale, dépendances, explications des profils types, comparaison |

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
├── explainability.py valeurs de Shapley exactes, explications locales/globales
├── registry.py       persistance versionnée + fiche modèle
├── train.py          pipeline d'entraînement de bout en bout
└── api.py            API FastAPI (contrat camelCase, validation, chargement thread-safe)
tests/actuarial/      102 tests sur portefeuille synthétique au processus connu
```

## Feuille de route

1. ~~Données + modèle de fréquence~~
2. ~~Modèle de sévérité, prime pure/commerciale, marge de prime~~
3. ~~Explicabilité SHAP (§3.6)~~
4. ~~API FastAPI + endpoints Spring Boot~~
5. ~~Écrans Angular~~
6. Provisionnement Chain-Ladder
7. Administration et intégration (stress test, formation)
