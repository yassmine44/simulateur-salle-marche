import { BankingModel } from './banking-stress.model';

export type BankOwnershipType = 'PUBLIC' | 'PRIVATE' | 'UNKNOWN';

export interface BankCatalogEntry {
  id: string;
  name: string;
  bankingModel: BankingModel | null;
  ownershipType: BankOwnershipType;
  logoUrl: string | null;
  sourceUrl: string;
  custom?: boolean;
}

const MINISTRY_BANK_LIST =
  'https://www.finances.gov.tn/fr/la-liste-des-banques';
const PUBLIC_ENTERPRISES =
  'https://www.finances.gov.tn/fr/entreprises-publiques-et-etablissements-publics-caractere-non-administratif';

export const TUNISIAN_BANK_CATALOG: BankCatalogEntry[] = [
  {
    id: 'biat',
    name: 'BIAT',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'PRIVATE',
    logoUrl: 'https://www.biat.com.tn/sites/default/files/faviconlogobiat.png',
    sourceUrl: 'https://www.biat.com.tn/'
  },
  {
    id: 'bna-bank',
    name: 'BNA Bank',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'PUBLIC',
    logoUrl: null,
    sourceUrl: PUBLIC_ENTERPRISES
  },
  {
    id: 'stb',
    name: 'STB',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'PUBLIC',
    logoUrl: null,
    sourceUrl: PUBLIC_ENTERPRISES
  },
  {
    id: 'amen-bank',
    name: 'Amen Bank',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'UNKNOWN',
    logoUrl: 'https://www.amenbank.com.tn/img/logo.jpg',
    sourceUrl: 'https://www.amenbank.com.tn/fr/'
  },
  {
    id: 'attijari-bank-tunisie',
    name: 'Attijari Bank Tunisie',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'UNKNOWN',
    logoUrl: 'https://www.attijaribank.com.tn/sites/default/files/Logo_attijari_0.png',
    sourceUrl: 'https://www.attijaribank.com.tn/fr'
  },
  {
    id: 'bh-bank',
    name: 'BH Bank',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'PUBLIC',
    logoUrl: null,
    sourceUrl: PUBLIC_ENTERPRISES
  },
  {
    id: 'banque-de-tunisie',
    name: 'Banque de Tunisie',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'UNKNOWN',
    logoUrl: 'https://www.bt.com.tn/Themes/BT/Content/logo_bt_xs.png',
    sourceUrl: 'https://www.bt.com.tn/'
  },
  {
    id: 'atb',
    name: 'ATB',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'UNKNOWN',
    logoUrl: null,
    sourceUrl: 'https://www.atb.com.tn/'
  },
  {
    id: 'uib',
    name: 'UIB',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'PRIVATE',
    logoUrl: null,
    sourceUrl: 'https://www.uib.com.tn/nous-connaitre/uib.html'
  },
  {
    id: 'ubci',
    name: 'UBCI',
    bankingModel: 'CONVENTIONAL',
    ownershipType: 'UNKNOWN',
    logoUrl: null,
    sourceUrl: 'https://www.ubcinet.net/'
  },
  {
    id: 'banque-zitouna',
    name: 'Banque Zitouna',
    bankingModel: 'NON_CONVENTIONAL',
    ownershipType: 'PRIVATE',
    logoUrl: null,
    sourceUrl: 'https://www.banquezitouna.com/fr/presentation-banque-zitouna/presentation-generale'
  },
  {
    id: 'al-baraka-bank-tunisia',
    name: 'Al Baraka Bank Tunisia',
    bankingModel: 'NON_CONVENTIONAL',
    ownershipType: 'PRIVATE',
    logoUrl: null,
    sourceUrl: 'https://www.albarakabank.com.tn/fr'
  },
  {
    id: 'wifak-international-bank',
    name: 'Wifak International Bank',
    bankingModel: 'NON_CONVENTIONAL',
    ownershipType: 'PRIVATE',
    logoUrl: null,
    sourceUrl: 'https://www.wib.tn/'
  },
  {
    id: 'custom',
    name: 'Autre banque / profil personnalisé',
    bankingModel: null,
    ownershipType: 'UNKNOWN',
    logoUrl: null,
    sourceUrl: MINISTRY_BANK_LIST,
    custom: true
  }
];
