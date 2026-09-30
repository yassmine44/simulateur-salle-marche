import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { AuthStateService } from '../../services/auth-state.service';
import {
  AuthService,
  LoginRequest
} from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {

  private readonly authState = inject(AuthStateService);
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  isSubmitting = false;
  errorMessage = '';

  loginForm = this.fb.nonNullable.group({
    email: [
      '',
      [
        Validators.required,
        Validators.email
      ]
    ],

    password: [
      '',
      [
        Validators.required
      ]
    ]
  });

  onSubmit(): void {

    if (this.isSubmitting) {
      return;
    }

    this.errorMessage = '';

    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    const {
      email,
      password
    } = this.loginForm.getRawValue();

    const request: LoginRequest = {
      email,
      password
    };

    this.isSubmitting = true;

    this.authService.login(request)
      .pipe(
        finalize(() => {
          this.isSubmitting = false;
        })
      )
      .subscribe({

        next: (user) => {

          this.authState.setUser(user);

          console.log(
            'Utilisateur connecté :',
            user.email
          );

          this.router.navigate(['/app/dashboard']);
        },

        error: (error: HttpErrorResponse) => {

          if (error.status === 401) {

            this.errorMessage =
              'Adresse e-mail ou mot de passe incorrect.';

          } else if (error.status === 400) {

            this.errorMessage =
              'Veuillez vérifier les informations saisies.';

          } else {

            this.errorMessage =
              'Une erreur est survenue. Veuillez réessayer.';
          }
        }
      });
  }
}