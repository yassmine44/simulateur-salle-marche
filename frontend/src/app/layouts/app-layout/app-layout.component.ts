
import { Component, inject, PLATFORM_ID } from '@angular/core';

import {
  AsyncPipe,
  DOCUMENT,
  isPlatformBrowser
} from '@angular/common';

import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from '@angular/router';

import { AuthService } from '../../services/auth.service';
import { AuthStateService } from '../../services/auth-state.service';

@Component({
  selector: 'app-app-layout',
  standalone: true,

  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    AsyncPipe
  ],

  templateUrl: './app-layout.component.html',
  styleUrl: './app-layout.component.scss'
})
export class AppLayoutComponent {

  private readonly authService = inject(AuthService);
  private readonly authState = inject(AuthStateService);
  private readonly router = inject(Router);

  private readonly document = inject(DOCUMENT);
  private readonly platformId = inject(PLATFORM_ID);

  readonly user$ = this.authState.currentUser$;

  // =========================
  // THEME
  // =========================

  isDark = false;

  constructor() {
    if (isPlatformBrowser(this.platformId)) {

      const savedTheme =
        localStorage.getItem('six7-theme');

      this.isDark = savedTheme === 'dark';

      this.applyTheme();
    }
  }

  toggleTheme(): void {
    this.isDark = !this.isDark;

    this.applyTheme();
  }

  private applyTheme(): void {
    if (!isPlatformBrowser(this.platformId)) {
      return;
    }

    const theme = this.isDark ? 'dark' : 'light';

    this.document.documentElement.setAttribute(
      'data-theme',
      theme
    );

    localStorage.setItem('six7-theme', theme);
  }

  // =========================
  // ADMIN MENU
  // =========================

  adminMenuOpen = false;

  toggleAdminMenu(): void {
    this.adminMenuOpen = !this.adminMenuOpen;
  }

  closeAdminMenu(): void {
    this.adminMenuOpen = false;
  }

  // =========================
  // LOGOUT
  // =========================

  logout(): void {

    this.authService.logout().subscribe({

      next: () => {
        this.authState.clearUser();
        this.router.navigate(['/login']);
      },

      error: () => {
        this.authState.clearUser();
        this.router.navigate(['/login']);
      }
    });
  }
}
