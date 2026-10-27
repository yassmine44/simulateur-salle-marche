import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Subscription, finalize } from 'rxjs';
import {
  ScenarioExecutionStatus,
  ScenarioHistoryItem,
  ScenarioHistoryResponse,
  ScenarioReportResponse
} from './scenario-history.model';
import { ScenarioHistoryService } from './scenario-history.service';

@Component({
  selector: 'app-scenario-history',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './scenario-history.component.html',
  styleUrl: './scenario-history.component.scss'
})
export class ScenarioHistoryComponent implements OnInit, OnDestroy {
  private readonly api = inject(ScenarioHistoryService);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;

  readonly pageSize = 10;
  page = 0;
  history: ScenarioHistoryResponse | null = null;
  selected: ScenarioHistoryItem | null = null;
  report: ScenarioReportResponse | null = null;

  loading = false;
  reportLoading = false;
  listError = '';
  reportError = '';

  ngOnInit(): void {
    this.loadPage(0);
  }

  ngOnDestroy(): void {
    this.listRequest?.unsubscribe();
    this.detailRequest?.unsubscribe();
  }

  loadPage(page: number): void {
    if (page < 0) return;
    if (this.history && page >= this.history.totalPages && page !== 0) return;

    this.listRequest?.unsubscribe();
    this.detailRequest?.unsubscribe();
    this.selected = null;
    this.report = null;
    this.reportError = '';
    this.listError = '';
    this.loading = true;

    this.listRequest = this.api.getHistory(page, this.pageSize)
      .pipe(finalize(() => { this.loading = false; }))
      .subscribe({
        next: response => {
          this.history = response;
          this.page = response.page;
        },
        error: error => {
          this.history = null;
          this.listError = this.describeError('Chargement de l’historique', error);
        }
      });
  }

  selectExecution(item: ScenarioHistoryItem): void {
    this.detailRequest?.unsubscribe();
    this.selected = item;
    this.report = null;
    this.reportError = '';
    this.reportLoading = true;

    this.detailRequest = this.api.getReport(item.id)
      .pipe(finalize(() => { this.reportLoading = false; }))
      .subscribe({
        next: report => { this.report = report; },
        error: error => {
          this.reportError = this.describeError('Chargement du bilan', error);
        }
      });
  }

  get hasPrevious(): boolean {
    return !this.loading && this.page > 0;
  }

  get hasNext(): boolean {
    return !this.loading && !!this.history && this.page + 1 < this.history.totalPages;
  }

  statusLabel(status: ScenarioExecutionStatus): string {
    switch (status) {
      case 'RUNNING': return 'En cours';
      case 'STOPPED': return 'Arrêté';
      case 'EXPIRED': return 'Terminé';
      case 'INTERRUPTED': return 'Interrompu';
    }
  }

  trackById(_index: number, item: ScenarioHistoryItem): number {
    return item.id;
  }

  private describeError(action: string, error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401 || error.status === 403) {
        return `${action} : accès refusé. Vérifiez votre session et vos droits.`;
      }
      if (error.status === 404) {
        return `${action} : ressource introuvable (404).`;
      }
      if (error.status === 0) {
        return `${action} : backend injoignable.`;
      }
      return `${action} : erreur HTTP ${error.status}.`;
    }
    return `${action} : erreur inconnue.`;
  }
}
