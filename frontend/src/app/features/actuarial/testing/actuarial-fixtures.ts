import { ActuarialMetadata, QuoteResponse } from '../actuarial.models';

/** Métadonnées réelles renvoyées par le moteur (GET /api/actuarial/metadata). */
export const METADATA: ActuarialMetadata = {
  fields: [
    { field: 'area', feature: 'Area', label: 'Zone géographique', type: 'category', values: ['A', 'B', 'C', 'D', 'E', 'F'] },
    { field: 'vehiclePower', feature: 'VehPower', label: 'Puissance du véhicule', type: 'integer', min: 4, max: 15 },
    { field: 'vehicleAge', feature: 'VehAge', label: 'Âge du véhicule', type: 'integer', min: 0, max: 100 },
    { field: 'driverAge', feature: 'DrivAge', label: 'Âge du conducteur', type: 'integer', min: 18, max: 100 },
    { field: 'bonusMalus', feature: 'BonusMalus', label: 'Coefficient bonus-malus', type: 'integer', min: 50, max: 230 },
    {
      field: 'vehicleBrand', feature: 'VehBrand', label: 'Marque du véhicule', type: 'category',
      values: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6', 'B10', 'B11', 'B12', 'B13', 'B14']
    },
    { field: 'vehicleGas', feature: 'VehGas', label: 'Carburant', type: 'category', values: ['Diesel', 'Regular'] },
    { field: 'density', feature: 'Density', label: 'Densité de population', type: 'integer', min: 1, max: 30000 },
    { field: 'region', feature: 'Region', label: 'Région', type: 'category', values: ['R11', 'R24', 'R82'] }
  ],
  defaultAssumptions: { expenseRatio: 0.25, profitRatio: 0.05, safetyLoading: 0.03 }
};

/** Devis réel du ml-service pour le jeune conducteur urbain en malus. */
export const QUOTE: QuoteResponse = {
  years: 1,
  frequency: 0.255324,
  claimProbability: 0.225334,
  severity: 1683.48,
  largeClaimLoading: 0.396423,
  purePremium: 600.23,
  commercialPremium: 883.19,
  premiumMargin: 282.96,
  expectedLossRatio: 0.679612,
  assumptions: { expenseRatio: 0.25, profitRatio: 0.05, safetyLoading: 0.03, commercialFactor: 1.471429 },
  modelVersions: { frequency: '20261124162401', severity: '20261124162402' },
  explanation: {
    basePremium: 135.0837,
    purePremium: 600.2258,
    contributions: [
      {
        feature: 'BonusMalus', field: 'bonusMalus', label: 'Coefficient bonus-malus', value: 100,
        phi: 0.778484, relativity: 2.178169, frequencyPhi: 0.778484, severityPhi: 0, direction: 'hausse'
      },
      {
        feature: 'DrivAge', field: 'driverAge', label: 'Âge du conducteur', value: 20,
        phi: 0.5914, relativity: 1.806516, frequencyPhi: 0.5914, severityPhi: 0, direction: 'hausse'
      },
      {
        feature: 'VehBrand', field: 'vehicleBrand', label: 'Marque du véhicule', value: 'B12',
        phi: -0.276941, relativity: 0.758099, frequencyPhi: -0.276941, severityPhi: 0, direction: 'baisse'
      }
    ]
  }
};
