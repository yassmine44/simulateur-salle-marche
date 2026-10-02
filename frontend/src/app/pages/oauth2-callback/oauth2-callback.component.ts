import { Component, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { switchMap, map, catchError, of } from 'rxjs';

import { AuthService } from '../../services/auth.service';
import { AuthStateService } from '../../services/auth-state.service';

@Component({
  selector: 'app-oauth2-callback',
  standalone: true,
  imports: [],
  templateUrl: './oauth2-callback.component.html',
  styleUrl: './oauth2-callback.component.scss'
})
export class Oauth2CallbackComponent implements OnInit {

  private readonly authService = inject(AuthService);
  private readonly authState = inject(AuthStateService);
  private readonly router = inject(Router);

  ngOnInit(): void {

    this.authService.me()
      .pipe(

        switchMap(user => {

          this.authState.setUser(user);

          return this.authService.csrf()
            .pipe(
              map(() => user)
            );
        }),

        catchError(() => {

          this.router.navigate(
            ['/login'],
            {
              queryParams: {
                oauthError: 'google'
              }
            }
          );

          return of(null);
        })

      )
      .subscribe(user => {

        if (!user) {
          return;
        }

        this.router.navigate([
          '/app/dashboard'
        ]);
      });
  }
}