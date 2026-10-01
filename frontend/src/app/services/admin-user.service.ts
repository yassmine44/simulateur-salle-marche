import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export type UserRole = 'USER' | 'TRADER' | 'SALES' | 'SALES_TRADER' | 'RISK_MANAGER' | 'ADMIN';

export interface AdminUser {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  countryCode: string | null;
  phoneNumber: string | null;
  role: UserRole;
  enabled: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface AdminUserFilters {
  search?: string;
  role?: UserRole | '';
  enabled?: boolean | null;
  page?: number;
  size?: number;
}

export interface UpdateUserStatusRequest { enabled: boolean; }
export interface UpdateUserRoleRequest { role: UserRole; }

@Injectable({ providedIn: 'root' })
export class AdminUserService {
  private readonly apiUrl = 'http://localhost:8081/api/admin/users';

  constructor(private readonly http: HttpClient) {}

  getUsers(filters: AdminUserFilters = {}): Observable<PageResponse<AdminUser>> {
    let params = new HttpParams()
      .set('page', filters.page ?? 0)
      .set('size', filters.size ?? 10);
    const search = filters.search?.trim();
    if (search) {
      params = params.set('search', search);
    }
    if (filters.role) {
      params = params.set('role', filters.role);
    }
    if (filters.enabled !== undefined && filters.enabled !== null) {
      params = params.set('enabled', filters.enabled);
    }
    return this.http.get<PageResponse<AdminUser>>(this.apiUrl, {
      params,
      withCredentials: true
    });
  }

  getUserById(userId: number): Observable<AdminUser> {
    return this.http.get<AdminUser>(`${this.apiUrl}/${userId}`, { withCredentials: true });
  }

  updateStatus(userId: number, enabled: boolean): Observable<AdminUser> {
    const request: UpdateUserStatusRequest = { enabled };
    return this.http.patch<AdminUser>(`${this.apiUrl}/${userId}/status`, request, {
      withCredentials: true
    });
  }

  updateRole(userId: number, role: UserRole): Observable<AdminUser> {
    const request: UpdateUserRoleRequest = { role };
    return this.http.patch<AdminUser>(`${this.apiUrl}/${userId}/role`, request, {
      withCredentials: true
    });
  }
}
