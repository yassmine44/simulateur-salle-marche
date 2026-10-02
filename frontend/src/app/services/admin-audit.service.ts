import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';

import { Observable } from 'rxjs';

export type LoginMethod =
  | 'LOCAL'
  | 'GOOGLE';

export type SecurityRiskLevel =
  | 'LOW'
  | 'MEDIUM'
  | 'HIGH'
  | 'CRITICAL';


export interface SuspiciousIp {

  ipAddress: string;

  failedAttempts: number;

  distinctEmails: number;

  firstAttempt: string;

  lastAttempt: string;

  riskLevel: SecurityRiskLevel;
}

export interface SuspiciousIp {
  ipAddress: string;
  failedAttempts: number;
  distinctEmails: number;
  firstAttempt: string;
  lastAttempt: string;
  riskLevel: SecurityRiskLevel;
}
export interface LoginAudit {

  id: number;

  userId: number | null;

  email: string | null;

  method: LoginMethod;

  ipAddress: string | null;

  userAgent: string | null;

  success: boolean;

  details: string | null;

  createdAt: string;
}


export interface LoginAuditPage {

  content: LoginAudit[];

  totalElements: number;

  totalPages: number;

  size: number;

  number: number;

  first: boolean;

  last: boolean;

  empty: boolean;
}


export interface LoginAuditFilters {

  search?: string;

  ipAddress?: string;

  method?: LoginMethod | '';

  success?: boolean | null;

  from?: string;

  to?: string;

  page?: number;

  size?: number;
}
export type AuditEventType =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILED'
  | 'LOGOUT'
  | 'REGISTER_SUCCESS'
  | 'REGISTER_FAILED'
  | 'GOOGLE_LOGIN_SUCCESS'
  | 'GOOGLE_LOGIN_FAILED'
  | 'PASSWORD_RESET_REQUEST'
  | 'PASSWORD_RESET_SUCCESS'
  | 'PASSWORD_CHANGED'
  | 'RECAPTCHA_REJECTED'
  | 'RATE_LIMIT_EXCEEDED'
  | 'USER_ENABLED'
  | 'USER_DISABLED'
  | 'ROLE_CHANGED'
  | 'PROFILE_UPDATED';


export interface SecurityAuditLog {

  id: number;

  eventType: AuditEventType;

  userId: number | null;

  email: string | null;

  actorEmail: string | null;

  ipAddress: string | null;

  userAgent: string | null;

  endpoint: string | null;

  success: boolean;

  details: string | null;

  createdAt: string;
}
export interface SecurityEventDistribution {

  eventType: AuditEventType;

  count: number;

  percentage: number;
}


export interface SecurityAuditStats {

  totalEventsToday: number;

  successfulLoginsToday: number;

  failedLoginsToday: number;

  rateLimitExceededToday: number;

  recaptchaRejectedToday: number;

  adminActionsToday: number;
}
export interface SecurityActivityPoint {

  date: string;

  totalEvents: number;

  successfulLogins: number;

  failedLogins: number;

  rateLimitExceeded: number;

  recaptchaRejected: number;

  adminActions: number;
}


export interface AuditLogPage {

  content: SecurityAuditLog[];

  totalElements: number;

  totalPages: number;

  size: number;

  number: number;

  first: boolean;

  last: boolean;

  empty: boolean;
}


export interface AuditLogFilters {

  search?: string;

  eventType?: AuditEventType | '';

  success?: boolean | null;

  from?: string;

  to?: string;

  page?: number;

  size?: number;
}


@Injectable({
  providedIn: 'root'
})
export class AdminAuditService {

  private readonly http =
    inject(HttpClient);


  private readonly baseUrl =
    'http://localhost:8081/api/admin/audit-logs';


  /*
   * =========================================
   * AUDIT LOGS
   * =========================================
   */

  getAuditLogs(
    filters: AuditLogFilters = {}
  ): Observable<AuditLogPage> {

    let params =
      new HttpParams();


    /*
     * SEARCH
     */
    if (
      filters.search &&
      filters.search.trim()
    ) {

      params =
        params.set(
          'search',
          filters.search.trim()
        );
    }


    /*
     * EVENT TYPE
     */
    if (
      filters.eventType
    ) {

      params =
        params.set(
          'eventType',
          filters.eventType
        );
    }


    /*
     * SUCCESS / FAILURE
     */
    if (
      filters.success !== undefined &&
      filters.success !== null
    ) {

      params =
        params.set(
          'success',
          filters.success.toString()
        );
    }


    /*
     * FROM
     */
    if (
      filters.from
    ) {

      params =
        params.set(
          'from',
          filters.from
        );
    }


    /*
     * TO
     */
    if (
      filters.to
    ) {

      params =
        params.set(
          'to',
          filters.to
        );
    }


    /*
     * PAGE
     */
    params =
      params.set(
        'page',
        (
          filters.page ?? 0
        ).toString()
      );


    /*
     * PAGE SIZE
     */
    params =
      params.set(
        'size',
        (
          filters.size ?? 20
        ).toString()
      );


    return this.http.get<AuditLogPage>(
      this.baseUrl,
      {
        params,
        withCredentials: true
      }
    );
  }


  /*
   * =========================================
   * SECURITY CENTER STATS
   * =========================================
   */

  getStats():
    Observable<SecurityAuditStats> {

    return this.http
      .get<SecurityAuditStats>(
        `${this.baseUrl}/stats`,
        {
          withCredentials: true
        }
      );
  }
  getActivity(
  days: number = 7
): Observable<SecurityActivityPoint[]> {

  const safeDays =
    Math.min(
      Math.max(days, 1),
      30
    );


  const params =
    new HttpParams()
      .set(
        'days',
        safeDays.toString()
      );


  return this.http
    .get<SecurityActivityPoint[]>(
      `${this.baseUrl}/activity`,
      {
        params,
        withCredentials: true
      }
    );
}
getDistribution(
  days: number = 7
): Observable<SecurityEventDistribution[]> {

  const safeDays =
    Math.min(
      Math.max(days, 1),
      30
    );


  const params =
    new HttpParams()
      .set(
        'days',
        safeDays.toString()
      );


  return this.http
    .get<SecurityEventDistribution[]>(
      `${this.baseUrl}/distribution`,
      {
        params,
        withCredentials: true
      }
    );
}
getLoginHistory(
  filters: LoginAuditFilters = {}
): Observable<LoginAuditPage> {

  let params =
    new HttpParams();


  if (
    filters.search &&
    filters.search.trim()
  ) {

    params =
      params.set(
        'search',
        filters.search.trim()
      );
  }


  if (
    filters.ipAddress &&
    filters.ipAddress.trim()
  ) {

    params =
      params.set(
        'ipAddress',
        filters.ipAddress.trim()
      );
  }


  if (
    filters.method
  ) {

    params =
      params.set(
        'method',
        filters.method
      );
  }


  if (
    filters.success !== undefined &&
    filters.success !== null
  ) {

    params =
      params.set(
        'success',
        filters.success.toString()
      );
  }


  if (
    filters.from
  ) {

    params =
      params.set(
        'from',
        filters.from
      );
  }


  if (
    filters.to
  ) {

    params =
      params.set(
        'to',
        filters.to
      );
  }


  params =
    params
      .set(
        'page',
        (
          filters.page ?? 0
        ).toString()
      )
      .set(
        'size',
        (
          filters.size ?? 10
        ).toString()
      );


  return this.http
    .get<LoginAuditPage>(
      `${this.baseUrl}/logins`,
      {
        params,
        withCredentials: true
      }
    );
}
getSuspiciousIps(
  hours: number = 24,
  threshold: number = 5
): Observable<SuspiciousIp[]> {

  const params =
    new HttpParams()
      .set(
        'hours',
        hours.toString()
      )
      .set(
        'threshold',
        threshold.toString()
      );


  return this.http.get<SuspiciousIp[]>(
    `${this.baseUrl}/suspicious-ips`,
    {
      params,
      withCredentials: true
    }
  );
}

}