import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Subscription, finalize, switchMap } from 'rxjs';
import { AdminUser, AdminUserService, PageResponse, UserRole } from '../../services/admin-user.service';
import { AuthService } from '../../services/auth.service';
import { AuthStateService } from '../../services/auth-state.service';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-users.component.html',
  styleUrl: './admin-users.component.scss'
})
export class AdminUsersComponent implements OnInit, OnDestroy {
  private readonly api = inject(AdminUserService);
  private readonly auth = inject(AuthService);
  private readonly state = inject(AuthStateService);
  private readonly router = inject(Router);
  private listRequest?: Subscription;
  private detailRequest?: Subscription;
  private mutation?: Subscription;
  readonly roles: UserRole[] = ['USER', 'ADMIN'];
  search = '';
  role: UserRole | '' = '';
  enabled: boolean | null = null;
  size = 10;
  page = 0;
  result: PageResponse<AdminUser> | null = null;
  selected: AdminUser | null = null;
  loading = false;
  detailLoading = false;
  busy = false;
  error = '';
  notice = '';

  ngOnInit(): void { this.load(); }
  ngOnDestroy(): void {
    this.listRequest?.unsubscribe();
    this.detailRequest?.unsubscribe();
    this.mutation?.unsubscribe();
  }
  isSelf(user: AdminUser): boolean { return user.id === this.state.currentUser?.id; }
  phone(user: AdminUser): string {
    const number = user.phoneNumber?.trim();
    if (!number) return '—';
    return number.startsWith('+') || !user.countryCode ? number : `${user.countryCode} ${number}`;
  }
  applyFilters(): void { this.page = 0; this.load(); }
  resetFilters(): void {
    this.search = ''; this.role = ''; this.enabled = null; this.size = 10;
    this.applyFilters();
  }
  load(): void {
    this.listRequest?.unsubscribe();
    this.loading = true; this.error = ''; this.result = null;
    this.listRequest = this.api.getUsers({ search: this.search, role: this.role,
      enabled: this.enabled, page: this.page, size: this.size }).pipe(
      finalize(() => this.loading = false)
    ).subscribe({
      next: result => {
        if (result.totalPages > 0 && this.page >= result.totalPages) {
          this.page = result.totalPages - 1; this.load(); return;
        }
        this.page = result.number; this.result = result;
      },
      error: error => this.handleError(error)
    });
  }
  movePage(delta: number): void {
    if (this.loading || this.busy || !this.result) return;
    const next = this.page + delta;
    if (next < 0 || next >= this.result.totalPages) return;
    this.page = next; this.load();
  }
  details(user: AdminUser): void {
    this.detailRequest?.unsubscribe();
    this.selected = null; this.detailLoading = true; this.error = '';
    this.detailRequest = this.api.getUserById(user.id).pipe(
      finalize(() => this.detailLoading = false)
    ).subscribe({ next: detail => this.selected = detail, error: error => this.handleError(error) });
  }
  closeDetails(): void { this.detailRequest?.unsubscribe(); this.selected = null; }
  changeRole(user: AdminUser, value: string): void {
    if (this.isSelf(user) || this.busy || !this.roles.includes(value as UserRole) || value === user.role) return;
    if (!window.confirm(`Attribuer le rôle ${value} à ${user.firstName} ${user.lastName} ?`)) return;
    this.update(user, value as UserRole);
  }
  toggleStatus(user: AdminUser): void {
    if (this.isSelf(user) || this.busy) return;
    if (!window.confirm(`${user.enabled ? 'Désactiver' : 'Réactiver'} le compte de ${user.firstName} ${user.lastName} ?`)) return;
    this.update(user);
  }
  private update(user: AdminUser, role?: UserRole): void {
    this.busy = true; this.error = ''; this.notice = '';
    this.mutation = this.auth.csrf().pipe(
      switchMap(() => role ? this.api.updateRole(user.id, role) : this.api.updateStatus(user.id, !user.enabled)),
      finalize(() => this.busy = false)
    ).subscribe({
      next: updated => {
        if (this.selected?.id === updated.id) this.selected = updated;
        this.notice = 'Le compte a été mis à jour.'; this.load();
      },
      error: error => this.handleError(error)
    });
  }
  private handleError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      this.state.clearUser(); void this.router.navigate(['/login']); return;
    }
    this.error = error.status === 403 ? 'Vous ne disposez pas des droits nécessaires pour cette action.'
      : error.status === 0 ? 'Le serveur est injoignable. Réessayez dans un instant.'
      : error.status >= 500 ? 'Le serveur ne peut pas traiter la demande. Réessayez.'
      : error.error?.detail || 'La demande a échoué. Réessayez.';
  }
}


