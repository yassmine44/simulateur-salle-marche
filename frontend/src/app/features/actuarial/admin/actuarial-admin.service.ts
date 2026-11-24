import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { AdminOverview, AdminQuote, PricingAssumptions } from '../actuarial.models';

/** Administration du module d'actuariat (Spring Boot, /api/admin/actuarial, rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class ActuarialAdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = 'http://localhost:8081/api/admin/actuarial';
  private readonly options = { withCredentials: true };

  overview(): Observable<AdminOverview> {
    return this.http.get<AdminOverview>(`${this.baseUrl}/overview`, this.options);
  }

  updateAssumptions(assumptions: PricingAssumptions): Observable<unknown> {
    const { expenseRatio, profitRatio, safetyLoading } = assumptions;
    return this.http.put(`${this.baseUrl}/assumptions`, { expenseRatio, profitRatio, safetyLoading }, this.options);
  }

  updateCurrency(currency: { code: string; label: string; rateFromModel: number; source: string | null }): Observable<unknown> {
    return this.http.put(`${this.baseUrl}/currency`, currency, this.options);
  }

  reload(): Observable<unknown> {
    return this.http.post(`${this.baseUrl}/reload`, {}, this.options);
  }

  quotes(limit = 50): Observable<AdminQuote[]> {
    const params = new HttpParams().set('limit', limit);
    return this.http.get<AdminQuote[]>(`${this.baseUrl}/quotes`, { ...this.options, params });
  }
}
