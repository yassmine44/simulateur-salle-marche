# Références financières bancaires 2025

Ce registre documente les sources de la table `FINANCIAL_REFERENCE_2025` utilisée par
le référentiel informatif Angular. Les trois banques détaillées ci-dessous ont fait
l'objet d'une comparaison ciblée des cinq indicateurs demandés; les autres valeurs
du référentiel ne sont pas certifiées par cette vérification. Les chiffres publiés
restent distincts des hypothèses manuelles utilisées par le moteur de simulation.

## Contrôle des quatre sources ciblées

| Banque | Exercice / situation | Unité affichée | Document et lien de source | Pages déclarées dans le référentiel | Statut |
|---|---|---|---|---|---|
| BH Bank | 2025 / 31-12-2025 | KDT | [États financiers 2025, PDF CMF](https://www.cmf.tn/sites/default/files/pdfs/emetteurs/informations/rapports-societes/bh_bank_efd311225.pdf) | Bilan p. 2; résultat p. 4 | Les cinq indicateurs demandés concordent. États présentés pour BH Bank sans colonnes consolidées; la couverture ne dit pas explicitement « individuels ». |
| Banque de Tunisie | 2025 / 31-12-2025 | KDT | [États financiers individuels, PDF CMF](https://www.cmf.tn/sites/default/files/pdfs/emetteurs/informations/rapports-societes/bt_efd311225.pdf) | Bilan p. 2; résultat p. 4 | Les cinq indicateurs demandés concordent. La page 1 qualifie explicitement les états d'individuels. |
| UBCI | 2025 / 31-12-2025 | KDT | [États financiers 2025, PDF CMF](https://www.cmf.tn/sites/default/files/pdfs/emetteurs/informations/rapports-societes/ubci_efd311225.pdf) | Bilan p. 2; créances nettes, note 5.3 p. 17; résultat p. 4 | Les cinq indicateurs demandés concordent. États de l'UBCI sans colonnes consolidées; la couverture ne dit pas explicitement « individuels ». |
| Wifak International Bank | 2025 / 31-12-2025 selon la copie | KDT indiqué dans le référentiel | [Copie PDF hébergée par Mubasher, identifiée comme document BVMT](https://static.mubasher.info/File.Mix_Announcement_File/A2CA40C9-CD73-4D74-AD04-2B0543A66835.pdf) | La copie indique bilan p. 1 / note 1.3 et résultat p. 3; références à confirmer sur une source primaire | Source primaire émetteur/CMF/Bourse non confirmée. Le passage TND → KDT n'est pas vérifié contre des montants primaires; conserver les chiffres en attente de preuve. |

## Vérification page par page — cinq indicateurs

Les trois PDF CMF ont été téléchargés dans un répertoire temporaire, lus localement
et non ajoutés au dépôt. Les empreintes SHA-256 identifient les copies vérifiées.
Les libellés de source sont « mille(s) de dinars » ou « K.TND », équivalents à KDT;
aucune conversion numérique n'était nécessaire. Les numéros ci-dessous sont les
pages physiques du PDF.

| Banque | Indicateur | Valeur codée (KDT) | Valeur PDF | Unité source → Angular | Page PDF | Statut |
|---|---|---:|---:|---|---:|---|
| BH Bank | Total actifs | 15 244 878 | 15 244 878 | mille dinars → KDT | 2 | Confirmé |
| BH Bank | Capitaux propres | 1 373 273 | 1 373 273 | mille dinars → KDT | 2 | Confirmé |
| BH Bank | Créances clientèle nettes (AC3) | 10 317 861 | 10 317 861 | mille dinars → KDT | 2 | Confirmé |
| BH Bank | Dépôts clientèle | 10 028 454 | 10 028 454 | mille dinars → KDT | 2 | Confirmé |
| BH Bank | Résultat net de l'exercice | 39 769 | 39 769 | mille dinars → KDT | 4 | Confirmé |
| Banque de Tunisie | Total actifs | 8 884 449 | 8 884 449 | K.TND → KDT | 2 | Confirmé |
| Banque de Tunisie | Capitaux propres | 1 507 191 | 1 507 191 | K.TND → KDT | 2 | Confirmé |
| Banque de Tunisie | Créances clientèle nettes (AC3) | 6 585 368 | 6 585 368 | K.TND → KDT | 2 | Confirmé |
| Banque de Tunisie | Dépôts clientèle | 6 957 654 | 6 957 654 | K.TND → KDT | 2 | Confirmé |
| Banque de Tunisie | Résultat net de la période | 190 265 | 190 265 | K.TND → KDT | 4 | Confirmé |
| UBCI | Total actifs | 5 644 261 | 5 644 261 | milliers de dinars → KDT | 2 | Confirmé |
| UBCI | Capitaux propres | 589 280 | 589 280 | milliers de dinars → KDT | 2 | Confirmé |
| UBCI | Créances clientèle nettes (AC3) | 3 514 826 | 3 514 826 | milliers de dinars → KDT | 2; détail note 5.3 p. 17 | Confirmé |
| UBCI | Dépôts clientèle | 3 910 652 | 3 910 652 | milliers de dinars → KDT | 2 | Confirmé |
| UBCI | Résultat net de la période | 65 851 | 65 851 | milliers de dinars → KDT | 4 | Confirmé |

### Empreintes et portée des états

| Banque | SHA-256 du PDF CMF téléchargé | Périmètre établi par le document |
|---|---|---|
| BH Bank | `A9FADA0F992F0D84F87A0FC8CBBB4888BEF1690D0EEE881CB046847E68C0827D` | Page 1 annonce les états de BH Bank; bilan p. 2 au nom de la banque sans colonnes consolidées. La couverture ne précise pas explicitement « individuels ». |
| Banque de Tunisie | `0FCC0CE0D80CED13CC57F59F58E9D3B83AD6FD526E43454467C2876B1305EE42` | Page 1 indique explicitement les états financiers individuels; bilan p. 2 et résultat p. 4. |
| UBCI | `418C45035BBA9A7A64C36C5B31608D0F65590CA1B58290EC681D775451B28728` | Page 1 annonce les états de l'UBCI sans employer « individuels »; bilan p. 2 au nom de l'UBCI sans colonnes consolidées. |

Pour l'UBCI, la note 5.3 p. 17 indique le brut de 3 762 467 KDT, moins 18 190
KDT d'agios réservés et 229 451 KDT de provisions, soit le net de 3 514 826 KDT.
Cela confirme que `customerReceivablesNet` correspond au net, et non au brut.
Les pages vérifiées concernent les états de la banque; aucun chiffre consolidé n'a
été utilisé.

## Règles d'interprétation et travaux restants

- L'unité `KDT` signifie milliers de dinars tunisiens. Pour un montant source en TND,
  la conversion attendue est `KDT = TND / 1 000`; la règle arithmétique seule ne certifie
  ni le montant source ni les valeurs importées.
- Les cinq indicateurs des trois banques ci-dessus ont été contrôlés directement sur
  les pages PDF indiquées; les autres champs financiers de ces banques restent hors
  de cette vérification ciblée.
- Pour Wifak, ne pas qualifier le PDF Mubasher de publication primaire, ni les valeurs
  ou la conversion de vérifiées. Trouver un lien officiel émetteur, CMF ou Bourse avant
  d'approuver la ligne.
- Les champs `null` restent « non disponibles » et ne signifient pas zéro. Les états
  individuels ne doivent pas être remplacés par des états consolidés.
- Aucun PDF tiers n'est copié ou incorporé par ce registre; seuls les liens et
  références de pages sont consignés.

## Statut de certification

**15/15 indicateurs** demandés pour BH Bank, Banque de Tunisie et UBCI concordent avec
les PDF primaires. Les autres indicateurs de ces banques et le reste du référentiel
n'ont pas été vérifiés dans cette tâche.
