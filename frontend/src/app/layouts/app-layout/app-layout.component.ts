import { Component, inject } from '@angular/core';
import {
  Router,
  RouterLink,
  RouterLinkActive,
  RouterOutlet
} from '@angular/router';
import { AsyncPipe } from '@angular/common';

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

  readonly user$ = this.authState.currentUser$;

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