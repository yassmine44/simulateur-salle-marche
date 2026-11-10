import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, inject, isDevMode, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  BankCatalogEntry,
  BankOwnershipType,
  TUNISIAN_BANK_CATALOG
} from './bank-catalog';
import { BankingStressService } from './banking-stress.service';
import {
  createDemoComparisonRequest,
  createDemoResponse
} from './banking-stress.demo';
import {
  BankingModel,
  BankingStressScenarioType,
  BankingStressTestRequest,
  BankingStressTestResponse,
  BalanceSheetSide,
  BankInput,
  CreditExposure,
  ExposureTerms,
  FinancialContractType,
  MarketExposure,
  RateExposure,
  ReturnBasis,
  ScenarioInput,
  validateLossShares
} from './banking-stress.model';
import {
  FINANCIAL_REFERENCE_2025,
  PublishedBankFinancials
} from './financial-reference-2025';

type ExposureKind = 'credit' | 'rate' | 'market';

interface BankDraft {
  catalogBankId: string;
  bankId: string;
  bankName: string;
  model: BankingModel | null;
  ownershipType: BankOwnershipType;
  capitalProxy: number | null;
  riskWeightedAssets: number | null;
  creditExposures: CreditExposureDraft[];
  rateExposures: RateExposureDraft[];
  marketExposures: MarketExposureDraft[];
}

interface ExposureTermsDraft {
  contractType: FinancialContractType;
  returnBasis: ReturnBasis;
  bankLossShare: string | null;
  investorLossShare: string | null;
}

interface CreditExposureDraft {
  exposureId: string;
  terms: ExposureTermsDraft;
  exposureAtDefault: number | null;
  probabilityOfDefault: number | null;
  lossGivenDefault: number | null;
}

interface RateExposureDraft {
  exposureId: string;
  terms: ExposureTermsDraft;
  side: BalanceSheetSide;
  presentValue: number | null;
  modifiedDurationYears: number | null;
  convexityYearsSquared: number | null;
}

interface MarketExposureDraft {
  exposureId: string;
  terms: ExposureTermsDraft;
  marketValue: number | null;
}

interface ScenarioDraft {
  scenarioId: string;
  type: BankingStressScenarioType;
  rateShock: number | null;
  recessionProbabilityMultiplier: number | null;
  recessionLossGivenDefaultAddOn: number | null;
  defaultProbabilityAddOn: number | null;
  equityReturnShock: number | null;
}

@Component({
  selector: 'app-banking-stress',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './banking-stress.component.html',
  styleUrl: './banking-stress.component.scss'
})
export class BankingStressComponent {
  private readonly bankingStressService = inject(BankingStressService);
  @ViewChild('bankSearchInput') private bankSearchInput?: ElementRef<HTMLInputElement>;
  private nextBankNumber = 1;
  private nextScenarioNumber = 1;
  private nextExposureNumber = 1;

  readonly models: { value: BankingModel; label: string }[] = [
    { value: 'CONVENTIONAL', label: 'Conventionnel' },
    { value: 'NON_CONVENTIONAL', label: 'Non conventionnel' }
  ];

  readonly scenarioTypes: { value: BankingStressScenarioType; label: string }[] = [
    { value: 'RATE_SHOCK', label: 'Choc de taux' },
    { value: 'RECESSION', label: 'Récession' },
    { value: 'CREDIT_DEFAULT', label: 'Défaut de crédit' },
    { value: 'EQUITY_SHOCK', label: 'Choc actions' },
    { value: 'COMBINED', label: 'Scénario combiné' }
  ];

  readonly contracts: { value: FinancialContractType; label: string }[] = [
    { value: 'LOAN', label: 'Prêt' },
    { value: 'DEPOSIT', label: 'Dépôt' },
    { value: 'BOND', label: 'Obligation' },
    { value: 'SUKUK', label: 'Sukuk' },
    { value: 'MURABAHA', label: 'Mourabaha' },
    { value: 'IJARA', label: 'Ijara' },
    { value: 'MUSHARAKA', label: 'Moucharaka' },
    { value: 'MUDARABA', label: 'Moudaraba' },
    { value: 'EQUITY', label: 'Participation' },
    { value: 'OTHER', label: 'Autre' }
  ];

  readonly returnBases: { value: ReturnBasis; label: string }[] = [
    { value: 'FIXED_RATE', label: 'Taux fixe' },
    { value: 'BENCHMARK_LINKED', label: 'Indexée à un benchmark' },
    { value: 'PROFIT_SHARING', label: 'Partage des profits' },
    { value: 'VARIABLE', label: 'Variable' },
    { value: 'NOT_APPLICABLE', label: 'Sans objet' }
  ];

  readonly sides: { value: BalanceSheetSide; label: string }[] = [
    { value: 'ASSET', label: 'Actif' },
    { value: 'LIABILITY', label: 'Passif' }
  ];

  readonly bankCatalog = TUNISIAN_BANK_CATALOG;
  readonly demoAvailable = isDevMode();
  readonly publishedFinancialMetrics: {
    label: string;
    key: keyof PublishedBankFinancials;
  }[] = [
    { label: 'Total des actifs', key: 'totalAssets' },
    { label: 'Capitaux propres comptables', key: 'totalEquity' },
    { label: 'Crédits / financements nets', key: 'customerReceivablesNet' },
    { label: 'Crédits bruts', key: 'customerReceivablesGross' },
    { label: 'Dépôts clientèle', key: 'customerDeposits' },
    { label: 'Portefeuille d’investissement', key: 'investmentPortfolio' },
    { label: 'Résultat net', key: 'netProfit' },
    { label: 'Provisions crédit publiées', key: 'customerCreditProvisions' },
    { label: 'Provisions additionnelles', key: 'additionalProvisions' },
    { label: 'Provisions collectives', key: 'collectiveProvisions' },
    { label: 'Intérêts / revenus réservés', key: 'reservedInterestOrIncome' },
    { label: 'Autres ajustements crédit', key: 'otherCreditAdjustments' },
    { label: 'Déduction des intérêts payés d’avance', key: 'prepaidInterestDeduction' }
  ];
  readonly currencies = [
    { code: 'TND', name: 'Dinar tunisien', symbol: 'DT', flag: '🇹🇳' },
    { code: 'EUR', name: 'Euro', symbol: '€', flag: '🇪🇺' },
    { code: 'USD', name: 'Dollar américain', symbol: '$', flag: '🇺🇸' }
  ] as const;

  currency = 'TND';
  creditHorizonDays: number | null = 365;
  banks: BankDraft[] = [];
  scenarios: ScenarioDraft[] = [];
  bankSearch = '';
  bankPickerOpen = false;
  activeBankOptionIndex = 0;
  private readonly failedLogoIds = new Set<string>();
  loading = false;
  errorMessage = '';
  response: BankingStressTestResponse | null = null;
  isDemoResult = false;

  addBank(): void {
    this.bankSearch = '';
    this.activeBankOptionIndex = 0;
    this.bankPickerOpen = true;
    setTimeout(() => this.bankSearchInput?.nativeElement.focus());
  }

  get availableBanks(): BankCatalogEntry[] {
    const query = this.bankSearch.trim().toLocaleLowerCase('fr');
    return this.bankCatalog.filter(bank => {
      const alreadySelected = !bank.custom
        && this.banks.some(selected => selected.catalogBankId === bank.id);
      return !alreadySelected && bank.name.toLocaleLowerCase('fr').includes(query);
    });
  }

  get selectedCurrency(): typeof this.currencies[number] {
    return this.currencies.find(currency => currency.code === this.currency)
      ?? this.currencies[0];
  }

  selectBank(bank: BankCatalogEntry): void {
    if (!bank.custom && this.banks.some(selected => selected.catalogBankId === bank.id)) {
      return;
    }

    const sequence = this.nextBankNumber++;
    const isCustom = bank.custom === true;
    const name = isCustom ? '' : bank.name;

    this.banks.push({
      catalogBankId: bank.id,
      bankId: isCustom ? `custom-bank-${sequence}` : bank.id,
      bankName: name,
      model: bank.bankingModel,
      ownershipType: bank.ownershipType,
      capitalProxy: null,
      riskWeightedAssets: null,
      creditExposures: [],
      rateExposures: [],
      marketExposures: []
    });
    this.bankPickerOpen = false;
    this.clearResponse();
  }

  searchBanks(query: string): void {
    this.bankSearch = query;
    this.activeBankOptionIndex = 0;
  }

  catalogEntry(id: string): BankCatalogEntry | undefined {
    return this.bankCatalog.find(bank => bank.id === id);
  }

  bankLogo(bank: BankCatalogEntry): string | null {
    return this.failedLogoIds.has(bank.id) ? null : bank.logoUrl;
  }

  bankLogoForId(bankId: string): string | null {
    const bank = this.catalogEntry(bankId);
    return bank ? this.bankLogo(bank) : null;
  }

  markLogoUnavailable(bankId: string): void {
    this.failedLogoIds.add(bankId);
  }

  closeBankPicker(): void {
    this.bankPickerOpen = false;
  }

  onBankPickerKeydown(event: KeyboardEvent): void {
    const options = this.availableBanks;
    if (event.key === 'ArrowDown' && options.length > 0) {
      event.preventDefault();
      this.activeBankOptionIndex = (this.activeBankOptionIndex + 1) % options.length;
    } else if (event.key === 'ArrowUp' && options.length > 0) {
      event.preventDefault();
      this.activeBankOptionIndex =
        (this.activeBankOptionIndex - 1 + options.length) % options.length;
    } else if (event.key === 'Enter' && this.bankPickerOpen && options.length > 0) {
      event.preventDefault();
      const selected = options[this.activeBankOptionIndex];
      if (selected) {
        this.selectBank(selected);
      }
    } else if (event.key === 'Escape') {
      this.closeBankPicker();
    }
  }

  initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).slice(0, 2)
      .map(word => word.charAt(0).toLocaleUpperCase('fr')).join('');
  }

  bankTypeLabel(model: BankingModel | null): string {
    if (model === 'CONVENTIONAL') {
      return 'Conventionnel';
    }
    if (model === 'NON_CONVENTIONAL') {
      return 'Non conventionnel';
    }
    return 'Modèle à préciser';
  }

  publishedFinancialData(bankId: string): PublishedBankFinancials | undefined {
    return FINANCIAL_REFERENCE_2025[bankId];
  }

  publishedMetricValue(
    financials: PublishedBankFinancials,
    key: keyof PublishedBankFinancials
  ): number | null {
    const value = financials[key];
    return typeof value === 'number' ? value : null;
  }

  ownershipTypeLabel(type: BankOwnershipType): string {
    switch (type) {
      case 'PUBLIC':
        return 'Publique';
      case 'PRIVATE':
        return 'Privée';
      default:
        return 'Non vérifiée';
    }
  }

  currencyLabel(code: string): string {
    const currency = this.currencies.find(option => option.code === code);
    return currency ? `${currency.code} (${currency.symbol})` : code;
  }

  removeBank(index: number): void {
    this.banks.splice(index, 1);
    this.clearResponse();
  }

  addExposure(bank: BankDraft, kind: ExposureKind): void {
    const id = `${kind}-${this.nextExposureNumber++}`;
    if (kind === 'credit') {
      bank.creditExposures.push({
        exposureId: id,
        terms: this.createTerms(),
        exposureAtDefault: null,
        probabilityOfDefault: null,
        lossGivenDefault: null
      });
    } else if (kind === 'rate') {
      bank.rateExposures.push({
        exposureId: id,
        terms: this.createTerms(),
        side: 'ASSET',
        presentValue: null,
        modifiedDurationYears: null,
        convexityYearsSquared: null
      });
    } else {
      bank.marketExposures.push({
        exposureId: id,
        terms: this.createTerms(),
        marketValue: null
      });
    }
    this.clearResponse();
  }

  removeExposure(bank: BankDraft, kind: ExposureKind, index: number): void {
    if (kind === 'credit') {
      bank.creditExposures.splice(index, 1);
    } else if (kind === 'rate') {
      bank.rateExposures.splice(index, 1);
    } else {
      bank.marketExposures.splice(index, 1);
    }
    this.clearResponse();
  }

  addScenario(): void {
    this.scenarios.push({
      scenarioId: `scenario-${this.nextScenarioNumber++}`,
      type: 'COMBINED',
      rateShock: null,
      recessionProbabilityMultiplier: null,
      recessionLossGivenDefaultAddOn: null,
      defaultProbabilityAddOn: null,
      equityReturnShock: null
    });
    this.clearResponse();
  }

  removeScenario(index: number): void {
    this.scenarios.splice(index, 1);
    this.clearResponse();
  }

  onScenarioTypeChange(scenario: ScenarioDraft): void {
    scenario.rateShock = null;
    scenario.recessionProbabilityMultiplier = null;
    scenario.recessionLossGivenDefaultAddOn = null;
    scenario.defaultProbabilityAddOn = null;
    scenario.equityReturnShock = null;
    this.clearResponse();
  }

  onModelChange(): void {
    this.clearResponse();
  }

  onOwnershipChange(): void {
    this.clearResponse();
  }

  onFormChange(): void {
    this.clearResponse();
  }

  loadDemoComparison(): void {
    if (!this.demoAvailable || this.loading || this.banks.length > 0
        || this.scenarios.length > 0) {
      return;
    }

    const request = createDemoComparisonRequest();
    this.currency = request.currency;
    this.creditHorizonDays = request.creditHorizonDays;
    this.banks = request.banks.map(bank => ({
      catalogBankId: bank.bankId,
      bankId: bank.bankId,
      bankName: bank.bankName,
      model: bank.model,
      ownershipType: this.catalogEntry(bank.bankId)?.ownershipType ?? 'UNKNOWN',
      capitalProxy: bank.capitalProxy,
      riskWeightedAssets: bank.riskWeightedAssets,
      creditExposures: bank.creditExposures.map(exposure => ({
        ...exposure,
        terms: {
          ...exposure.terms,
          bankLossShare: String(exposure.terms.bankLossShare),
          investorLossShare: String(exposure.terms.investorLossShare)
        }
      })),
      rateExposures: bank.rateExposures.map(exposure => ({
        ...exposure,
        terms: {
          ...exposure.terms,
          bankLossShare: String(exposure.terms.bankLossShare),
          investorLossShare: String(exposure.terms.investorLossShare)
        }
      })),
      marketExposures: bank.marketExposures.map(exposure => ({
        ...exposure,
        terms: {
          ...exposure.terms,
          bankLossShare: String(exposure.terms.bankLossShare),
          investorLossShare: String(exposure.terms.investorLossShare)
        }
      }))
    }));
    this.scenarios = request.scenarios.map(scenario => ({ ...scenario }));
    this.nextBankNumber = this.banks.length + 1;
    this.nextScenarioNumber = this.scenarios.length + 1;
    this.nextExposureNumber = 7;
    this.bankPickerOpen = false;
    this.clearResponse();
  }

  runDemo(): void {
    if (!this.demoAvailable || this.loading) {
      return;
    }

    this.errorMessage = '';
    this.response = null;
    this.isDemoResult = false;
    try {
      this.response = createDemoResponse(this.buildRequest());
      this.isDemoResult = true;
    } catch (error: unknown) {
      this.errorMessage = error instanceof Error
        ? error.message
        : 'Vérifiez les paramètres de la démonstration.';
    }
  }

  submit(): void {
    this.errorMessage = '';
    this.response = null;
    this.isDemoResult = false;

    try {
      const request = this.buildRequest();
      this.loading = true;
      this.bankingStressService.analyze(request).subscribe({
        next: response => {
          this.response = response;
          this.loading = false;
        },
        error: (error: HttpErrorResponse) => {
          this.errorMessage = this.describeHttpError(error);
          this.loading = false;
        }
      });
    } catch (error: unknown) {
      this.errorMessage = error instanceof Error
        ? error.message
        : 'Vérifiez les paramètres de la simulation.';
    }
  }

  get resultCount(): number {
    return this.response?.results.length ?? 0;
  }

  scenarioLabel(type: BankingStressScenarioType): string {
    return this.scenarioTypes.find(item => item.value === type)?.label ?? type;
  }

  private buildRequest(): BankingStressTestRequest {
    const currency = this.currency.trim().toUpperCase();
    if (!/^[A-Z]{3}$/.test(currency)) {
      throw new Error('Saisissez un code devise de trois lettres, par exemple EUR.');
    }
    if (!this.creditHorizonDays || !Number.isInteger(this.creditHorizonDays)
        || this.creditHorizonDays < 1 || this.creditHorizonDays > 3650) {
      throw new Error('L’horizon PD doit être compris entre 1 et 3650 jours.');
    }
    if (this.banks.length === 0 || this.scenarios.length === 0) {
      throw new Error('Ajoutez au moins une banque et un scénario.');
    }
    if (this.banks.length > 100 || this.scenarios.length > 100
        || this.banks.length * this.scenarios.length > 1000) {
      throw new Error('La requête dépasse le maximum de banques, scénarios ou résultats.');
    }

    let evaluations = 0;
    const banks = this.banks.map(bank => {
      if (!bank.bankName.trim() || bank.bankName.length > 120) {
        throw new Error('Chaque banque doit avoir un nom descriptif de 1 à 120 caractères.');
      }
      if (bank.bankId.length > 80) {
        throw new Error('L’identifiant technique d’une banque dépasse 80 caractères.');
      }
      const exposureCount = this.exposureCount(bank);
      if ([bank.creditExposures.length, bank.rateExposures.length,
        bank.marketExposures.length].some(count => count > 500)) {
        throw new Error('Chaque catégorie est limitée à 500 expositions par banque.');
      }
      evaluations += exposureCount * this.scenarios.length;
      if (evaluations > 1_000_000) {
        throw new Error('La requête dépasse le maximum d’évaluations autorisé.');
      }
      return this.buildBank(bank);
    });

    const scenarioIds = new Set<string>();
    const scenarios = this.scenarios.map(scenario => {
      if (scenarioIds.has(scenario.scenarioId)) {
        throw new Error('Les identifiants de scénarios doivent être uniques.');
      }
      scenarioIds.add(scenario.scenarioId);
      return this.buildScenario(scenario);
    });

    const bankIds = new Set(banks.map(bank => bank.bankId));
    if (bankIds.size !== banks.length) {
      throw new Error('Les identifiants de banques doivent être uniques.');
    }

    return {
      currency,
      creditHorizonDays: this.creditHorizonDays,
      banks,
      scenarios
    };
  }

  private buildBank(bank: BankDraft): BankInput {
    if (!bank.model) {
      throw new Error(`Sélectionnez ou confirmez le modèle bancaire de ${bank.bankName}.`);
    }
    const model = bank.model;
    return {
      bankId: bank.bankId,
      bankName: bank.bankName.trim(),
      model,
      capitalProxy: this.requiredNumber(bank.capitalProxy, 'Capital proxy', 0, 999_999_999_999_999),
      riskWeightedAssets: this.requiredNumber(
        bank.riskWeightedAssets, 'RWA proxy', 0, 999_999_999_999_999
      ),
      creditExposures: bank.creditExposures.map(exposure => ({
        exposureId: exposure.exposureId,
        terms: this.buildTerms(exposure.terms, exposure.exposureId, model),
        exposureAtDefault: this.requiredNumber(
          exposure.exposureAtDefault, 'EAD', 0, 999_999_999_999_999
        ),
        probabilityOfDefault: this.requiredNumber(
          exposure.probabilityOfDefault, 'PD', 0, 1
        ),
        lossGivenDefault: this.requiredNumber(exposure.lossGivenDefault, 'LGD', 0, 1)
      })),
      rateExposures: bank.rateExposures.map(exposure => ({
        exposureId: exposure.exposureId,
        terms: this.buildTerms(exposure.terms, exposure.exposureId, model),
        side: exposure.side,
        presentValue: this.requiredNumber(
          exposure.presentValue, 'Valeur actuelle', 0, 999_999_999_999_999
        ),
        modifiedDurationYears: this.requiredNumber(
          exposure.modifiedDurationYears, 'Duration (années)', 0, 99_999_999
        ),
        convexityYearsSquared: this.requiredNumber(
          exposure.convexityYearsSquared, 'Convexité (années²)', 0, 99_999_999
        )
      })),
      marketExposures: bank.marketExposures.map(exposure => ({
        exposureId: exposure.exposureId,
        terms: this.buildTerms(exposure.terms, exposure.exposureId, model),
        marketValue: this.requiredNumber(
          exposure.marketValue, 'Valeur de marché', 0, 999_999_999_999_999
        )
      }))
    };
  }

  private buildTerms(
    terms: ExposureTermsDraft,
    exposureId: string,
    model: BankingModel
  ): ExposureTerms {
    const { bankLossShare, investorLossShare } = validateLossShares(
      terms.bankLossShare,
      terms.investorLossShare,
      exposureId
    );
    if (model === 'CONVENTIONAL'
        && ['SUKUK', 'MURABAHA', 'IJARA', 'MUSHARAKA', 'MUDARABA']
          .includes(terms.contractType)) {
      throw new Error(
        `Le contrat ${terms.contractType} n’est pas cohérent avec le modèle conventionnel.`
      );
    }

    const compatibleBases: Record<FinancialContractType, ReturnBasis[]> = {
      LOAN: ['FIXED_RATE', 'BENCHMARK_LINKED', 'VARIABLE'],
      DEPOSIT: ['FIXED_RATE', 'BENCHMARK_LINKED', 'VARIABLE'],
      BOND: ['FIXED_RATE', 'BENCHMARK_LINKED', 'VARIABLE'],
      SUKUK: ['BENCHMARK_LINKED', 'PROFIT_SHARING', 'VARIABLE'],
      MURABAHA: ['FIXED_RATE'],
      IJARA: ['FIXED_RATE', 'BENCHMARK_LINKED', 'VARIABLE'],
      MUSHARAKA: ['PROFIT_SHARING'],
      MUDARABA: ['PROFIT_SHARING'],
      EQUITY: ['PROFIT_SHARING', 'VARIABLE', 'NOT_APPLICABLE'],
      OTHER: this.returnBases.map(basis => basis.value)
    };
    if (!compatibleBases[terms.contractType].includes(terms.returnBasis)) {
      throw new Error(
        `La base de rendement n’est pas compatible avec le contrat ${terms.contractType}.`
      );
    }
    return {
      contractType: terms.contractType,
      returnBasis: terms.returnBasis,
      bankLossShare,
      investorLossShare
    };
  }

  private buildScenario(scenario: ScenarioDraft): ScenarioInput {
    const optional = (value: number | null, label: string, min: number, max: number) =>
      value === null ? null : this.requiredNumber(value, label, min, max);
    const result: ScenarioInput = {
      ...scenario,
      rateShock: optional(scenario.rateShock, 'Choc de taux', -1, 1),
      recessionProbabilityMultiplier: optional(
        scenario.recessionProbabilityMultiplier, 'Multiplicateur PD', 1, 100
      ),
      recessionLossGivenDefaultAddOn: optional(
        scenario.recessionLossGivenDefaultAddOn, 'Supplément LGD récession', 0, 1
      ),
      defaultProbabilityAddOn: optional(
        scenario.defaultProbabilityAddOn, 'Supplément PD défaut', 0, 1
      ),
      equityReturnShock: optional(scenario.equityReturnShock, 'Choc actions', -1, 1)
    };

    const values = [
      result.rateShock,
      result.recessionProbabilityMultiplier,
      result.recessionLossGivenDefaultAddOn,
      result.defaultProbabilityAddOn,
      result.equityReturnShock
    ];
    if (scenario.type === 'COMBINED') {
      if (values.every(value => value === null)) {
        throw new Error(`Ajoutez au moins un choc au scénario ${scenario.scenarioId}.`);
      }
      if ((result.recessionProbabilityMultiplier === null)
          !== (result.recessionLossGivenDefaultAddOn === null)) {
        throw new Error('Les deux paramètres de récession doivent être renseignés ensemble.');
      }
    } else {
      const expectedField: Record<Exclude<BankingStressScenarioType, 'COMBINED'>, number> = {
        RATE_SHOCK: 0,
        RECESSION: 1,
        CREDIT_DEFAULT: 3,
        EQUITY_SHOCK: 4
      };
      const activeIndex = expectedField[scenario.type];
      if (values[activeIndex] === null) {
        throw new Error(`Renseignez le choc requis pour ${this.scenarioLabel(scenario.type)}.`);
      }
      if (scenario.type === 'RECESSION'
          && result.recessionLossGivenDefaultAddOn === null) {
        throw new Error('Renseignez le supplément LGD du scénario de récession.');
      }
      if (values.some((value, index) => value !== null && index !== activeIndex
          && !(scenario.type === 'RECESSION' && index === 2))) {
        throw new Error('Un scénario simple ne peut contenir que ses paramètres dédiés.');
      }
    }
    return result;
  }

  private requiredNumber(
    value: number | null,
    label: string,
    min: number,
    max: number
  ): number {
    if (value === null || !Number.isFinite(value) || value < min || value > max) {
      throw new Error(`${label} doit être compris entre ${min} et ${max}.`);
    }
    if (Number(value.toFixed(8)) !== value) {
      throw new Error(`${label} ne peut pas comporter plus de 8 décimales.`);
    }
    return value;
  }

  private createTerms(): ExposureTermsDraft {
    return {
      contractType: 'LOAN',
      returnBasis: 'FIXED_RATE',
      bankLossShare: null,
      investorLossShare: null
    };
  }

  private exposureCount(bank: BankDraft): number {
    return bank.creditExposures.length
      + bank.rateExposures.length
      + bank.marketExposures.length;
  }

  private clearResponse(): void {
    this.response = null;
    this.errorMessage = '';
    this.isDemoResult = false;
  }

  private describeHttpError(error: HttpErrorResponse): string {
    if (error.status === 0) {
      return 'Le service bancaire est injoignable. Vérifiez que le backend est disponible.';
    }
    const body: unknown = error.error;
    if (typeof body === 'object' && body !== null && 'message' in body
        && typeof body.message === 'string') {
      return body.message;
    }
    return `La simulation a échoué (HTTP ${error.status}). Vérifiez les données saisies.`;
  }
}
