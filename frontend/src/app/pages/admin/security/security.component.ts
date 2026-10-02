import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  Component,
  inject,
  OnInit
} from '@angular/core';
import { FormsModule } from '@angular/forms';

import { finalize } from 'rxjs';

import {
  AdminAuditService,
  AuditEventType,
  AuditLogFilters,
  SecurityActivityPoint,
  SecurityAuditLog,
  SecurityAuditStats,
  SecurityEventDistribution,
  SecurityRiskLevel,
  SuspiciousIp
} from '../../../services/admin-audit.service';


@Component({
  selector: 'app-security',

  standalone: true,

  imports: [
    CommonModule,
    FormsModule
  ],

  templateUrl: './security.component.html',

  styleUrl: './security.component.scss'
})
export class SecurityComponent implements OnInit {

  // =========================================
  // SERVICES
  // =========================================

  private readonly auditService =
    inject(AdminAuditService);


  // =========================================
  // GLOBAL STATE
  // =========================================

  errorMessage = '';


  // =========================================
  // LOADING STATES
  // =========================================

  loadingLogs = false;

  loadingStats = false;

  loadingActivity = false;

  loadingDistribution = false;

  loadingSuspiciousIps = false;


  // =========================================
  // STATISTICS
  // =========================================

  stats: SecurityAuditStats = {

    totalEventsToday: 0,

    successfulLoginsToday: 0,

    failedLoginsToday: 0,

    rateLimitExceededToday: 0,

    recaptchaRejectedToday: 0,

    adminActionsToday: 0
  };


  // =========================================
  // SECURITY ACTIVITY
  // =========================================

  activity: SecurityActivityPoint[] = [];

  activityDays = 7;


  // =========================================
  // EVENT DISTRIBUTION
  // =========================================

  distribution:
    SecurityEventDistribution[] = [];


  // =========================================
  // SUSPICIOUS IPS
  // =========================================

  suspiciousIps: SuspiciousIp[] = [];

  suspiciousHours = 24;

  suspiciousThreshold = 5;


  // =========================================
  // AUDIT LOGS
  // =========================================

  logs: SecurityAuditLog[] = [];

  totalElements = 0;

  totalPages = 0;

  currentPage = 0;

  pageSize = 20;


  // =========================================
  // AUDIT FILTERS
  // =========================================

  search = '';

  selectedEventType:
    AuditEventType | '' = '';

  selectedSuccess:
    boolean | null = null;

  from = '';

  to = '';


  // =========================================
  // EVENT TYPES
  // =========================================

  readonly eventTypes:
    AuditEventType[] = [

      'LOGIN_SUCCESS',

      'LOGIN_FAILED',

      'GOOGLE_LOGIN_SUCCESS',

      'GOOGLE_LOGIN_FAILED',

      'LOGOUT',

      'REGISTER_SUCCESS',

      'REGISTER_FAILED',

      'PASSWORD_RESET_REQUEST',

      'PASSWORD_RESET_SUCCESS',

      'PASSWORD_CHANGED',

      'RECAPTCHA_REJECTED',

      'RATE_LIMIT_EXCEEDED',

      'USER_ENABLED',

      'USER_DISABLED',

      'ROLE_CHANGED',

      'PROFILE_UPDATED'
    ];


  // =========================================
  // INIT
  // =========================================

  ngOnInit(): void {

    this.loadAll();
  }


  // =========================================
  // LOAD ALL
  // =========================================

  loadAll(): void {

    this.errorMessage = '';

    this.loadStats();

    this.loadActivity();

    this.loadDistribution();

    this.loadSuspiciousIps();

    this.loadLogs();
  }


  // =========================================
  // STATISTICS
  // =========================================

  loadStats(): void {

    this.loadingStats = true;


    this.auditService
      .getStats()
      .pipe(

        finalize(() => {

          this.loadingStats = false;

        })

      )
      .subscribe({

        next: stats => {

          this.stats = stats;
        },


        error: error => {

          this.handleError(
            error
          );
        }

      });
  }


  // =========================================
  // SECURITY ACTIVITY
  // =========================================

  loadActivity(): void {

    this.loadingActivity = true;


    this.auditService
      .getActivity(
        this.activityDays
      )
      .pipe(

        finalize(() => {

          this.loadingActivity = false;

        })

      )
      .subscribe({

        next: activity => {

          this.activity = activity;
        },


        error: error => {

          this.activity = [];

          this.handleError(
            error
          );
        }

      });
  }


  changeActivityDays(
    days: number
  ): void {

    if (
      days === this.activityDays
    ) {

      return;
    }


    this.activityDays = days;


    /*
     * Les deux blocs utilisent
     * la même période.
     */

    this.loadActivity();

    this.loadDistribution();
  }


  get maxLoginActivity(): number {

    if (
      this.activity.length === 0
    ) {

      return 1;
    }


    const values =
      this.activity.flatMap(
        point => [

          point.successfulLogins,

          point.failedLogins
        ]
      );


    return Math.max(
      1,
      ...values
    );
  }


  barHeight(
    value: number
  ): number {

    if (
      value <= 0
    ) {

      return 0;
    }


    return Math.max(

      6,

      (
        value /
        this.maxLoginActivity
      ) * 100
    );
  }


  formatActivityDate(
    value: string
  ): string {

    /*
     * T00:00:00 évite qu'une date
     * ISO YYYY-MM-DD soit interprétée
     * en UTC et décalée localement.
     */

    const date =
      new Date(
        `${value}T00:00:00`
      );


    return new Intl.DateTimeFormat(
      'fr-FR',
      {
        weekday: 'short',
        day: '2-digit'
      }
    )
      .format(date);
  }


  // =========================================
  // EVENT DISTRIBUTION
  // =========================================

  loadDistribution(): void {

    this.loadingDistribution = true;


    this.auditService
      .getDistribution(
        this.activityDays
      )
      .pipe(

        finalize(() => {

          this.loadingDistribution = false;

        })

      )
      .subscribe({

        next: distribution => {

          this.distribution =
            distribution;
        },


        error: error => {

          this.distribution = [];

          this.handleError(
            error
          );
        }

      });
  }


  distributionClass(
    event: AuditEventType
  ): string {

    switch (event) {

      case 'LOGIN_SUCCESS':

      case 'GOOGLE_LOGIN_SUCCESS':

      case 'REGISTER_SUCCESS':

      case 'USER_ENABLED':

      case 'PASSWORD_RESET_SUCCESS':

        return 'distribution-success';


      case 'LOGIN_FAILED':

      case 'GOOGLE_LOGIN_FAILED':

      case 'REGISTER_FAILED':

      case 'RECAPTCHA_REJECTED':

      case 'RATE_LIMIT_EXCEEDED':

        return 'distribution-danger';


      case 'USER_DISABLED':

      case 'ROLE_CHANGED':

        return 'distribution-warning';


      default:

        return 'distribution-info';
    }
  }


  // =========================================
  // SUSPICIOUS IPS
  // =========================================

  loadSuspiciousIps(): void {

    this.loadingSuspiciousIps = true;


    this.auditService
      .getSuspiciousIps(

        this.suspiciousHours,

        this.suspiciousThreshold

      )
      .pipe(

        finalize(() => {

          this.loadingSuspiciousIps =
            false;

        })

      )
      .subscribe({

        next: ips => {

          this.suspiciousIps = ips;
        },


        error: error => {

          this.suspiciousIps = [];

          this.handleError(
            error
          );
        }

      });
  }


  riskLabel(
    level: SecurityRiskLevel
  ): string {

    switch (level) {

      case 'CRITICAL':

        return 'Critique';


      case 'HIGH':

        return 'Élevé';


      case 'MEDIUM':

        return 'Moyen';


      case 'LOW':

      default:

        return 'Faible';
    }
  }


  riskClass(
    level: SecurityRiskLevel
  ): string {

    return level
      .toLowerCase();
  }


  // =========================================
  // AUDIT LOGS
  // =========================================

  loadLogs(): void {

    this.loadingLogs = true;


    const filters:
      AuditLogFilters = {

        search:
          this.search,

        eventType:
          this.selectedEventType,

        success:
          this.selectedSuccess,

        from:
          this.from || undefined,

        to:
          this.to || undefined,

        page:
          this.currentPage,

        size:
          this.pageSize
      };


    this.auditService
      .getAuditLogs(
        filters
      )
      .pipe(

        finalize(() => {

          this.loadingLogs = false;

        })

      )
      .subscribe({

        next: page => {

          this.logs =
            page.content;

          this.totalElements =
            page.totalElements;

          this.totalPages =
            page.totalPages;

          this.currentPage =
            page.number;
        },


        error: error => {

          this.logs = [];

          this.totalElements = 0;

          this.totalPages = 0;

          this.handleError(
            error
          );
        }

      });
  }


  // =========================================
  // AUDIT FILTERS
  // =========================================

  applyFilters(): void {

    this.currentPage = 0;

    this.loadLogs();
  }


  resetFilters(): void {

    this.search = '';

    this.selectedEventType = '';

    this.selectedSuccess = null;

    this.from = '';

    this.to = '';

    this.currentPage = 0;


    this.loadLogs();
  }


  // =========================================
  // PAGINATION
  // =========================================

  previousPage(): void {

    if (
      this.currentPage <= 0
    ) {

      return;
    }


    this.currentPage--;

    this.loadLogs();
  }


  nextPage(): void {

    if (
      this.currentPage >=
      this.totalPages - 1
    ) {

      return;
    }


    this.currentPage++;

    this.loadLogs();
  }


  goToPage(
    page: number
  ): void {

    if (
      page < 0
      ||
      page >= this.totalPages
      ||
      page === this.currentPage
    ) {

      return;
    }


    this.currentPage = page;

    this.loadLogs();
  }


  changePageSize(): void {

    this.currentPage = 0;

    this.loadLogs();
  }


  get pageNumbers(): number[] {

    if (
      this.totalPages <= 0
    ) {

      return [];
    }


    const start =
      Math.max(
        0,
        this.currentPage - 2
      );


    const end =
      Math.min(
        this.totalPages - 1,
        start + 4
      );


    const realStart =
      Math.max(
        0,
        end - 4
      );


    return Array.from(
      {
        length:
          end - realStart + 1
      },

      (
        _,
        index
      ) =>
        realStart + index
    );
  }


  // =========================================
  // EVENT DISPLAY
  // =========================================

  eventLabel(
    event: AuditEventType
  ): string {

    const labels:
      Record<
        AuditEventType,
        string
      > = {

        LOGIN_SUCCESS:
          'Connexion réussie',

        LOGIN_FAILED:
          'Échec de connexion',

        GOOGLE_LOGIN_SUCCESS:
          'Connexion Google réussie',

        GOOGLE_LOGIN_FAILED:
          'Échec Google',

        LOGOUT:
          'Déconnexion',

        REGISTER_SUCCESS:
          'Inscription réussie',

        REGISTER_FAILED:
          'Échec inscription',

        PASSWORD_RESET_REQUEST:
          'Demande de réinitialisation',

        PASSWORD_RESET_SUCCESS:
          'Réinitialisation réussie',

        PASSWORD_CHANGED:
          'Mot de passe modifié',

        RECAPTCHA_REJECTED:
          'reCAPTCHA rejeté',

        RATE_LIMIT_EXCEEDED:
          'Rate limit dépassé',

        USER_ENABLED:
          'Utilisateur activé',

        USER_DISABLED:
          'Utilisateur désactivé',

        ROLE_CHANGED:
          'Rôle modifié',

        PROFILE_UPDATED:
          'Profil modifié'
      };


    return labels[event] ?? event;
  }


  eventClass(
    event: AuditEventType
  ): string {

    switch (event) {

      case 'LOGIN_FAILED':

      case 'GOOGLE_LOGIN_FAILED':

      case 'REGISTER_FAILED':

      case 'RECAPTCHA_REJECTED':

      case 'RATE_LIMIT_EXCEEDED':

        return 'event-danger';


      case 'USER_DISABLED':

      case 'ROLE_CHANGED':

        return 'event-warning';


      case 'LOGIN_SUCCESS':

      case 'GOOGLE_LOGIN_SUCCESS':

      case 'REGISTER_SUCCESS':

      case 'USER_ENABLED':

      case 'PASSWORD_RESET_SUCCESS':

        return 'event-success';


      default:

        return 'event-info';
    }
  }


  // =========================================
  // AUTHENTICATION METHOD
  // =========================================

  authMethod(
    event: AuditEventType
  ): 'Local' | 'Google' | null {

    switch (event) {

      case 'LOGIN_SUCCESS':

      case 'LOGIN_FAILED':

        return 'Local';


      case 'GOOGLE_LOGIN_SUCCESS':

      case 'GOOGLE_LOGIN_FAILED':

        return 'Google';


      default:

        return null;
    }
  }


  authMethodClass(
    event: AuditEventType
  ): string {

    const method =
      this.authMethod(event);


    if (
      method === 'Google'
    ) {

      return 'google';
    }


    if (
      method === 'Local'
    ) {

      return 'local';
    }


    return '';
  }


  // =========================================
  // ERROR HANDLING
  // =========================================

  private handleError(
    error: HttpErrorResponse
  ): void {

    if (
      error.status === 401
    ) {

      this.errorMessage =
        'Votre session a expiré. Reconnectez-vous.';

      return;
    }


    if (
      error.status === 403
    ) {

      this.errorMessage =
        'Accès réservé aux administrateurs.';

      return;
    }


    if (
      error.status === 429
    ) {

      this.errorMessage =
        'Trop de requêtes. Réessayez dans quelques instants.';

      return;
    }


    this.errorMessage =
      'Impossible de charger les données de sécurité.';
  }
  
}