import { Component, OnInit, inject } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import {
  ActivatedRoute,
  Router,
  RouterLink
} from '@angular/router';
import { finalize } from 'rxjs';

import {
  AuthService,
  ResetPasswordRequest
} from '../../services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink
  ],
  templateUrl: './reset-password.component.html',
  styleUrl: './reset-password.component.scss'
})
export class ResetPasswordComponent implements OnInit {

  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  token = '';

  loading = false;
  tokenMissing = false;

  successMessage = '';
  errorMessage = '';

  readonly form = this.fb.nonNullable.group({

    newPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.maxLength(72)
      ]
    ],

    confirmPassword: [
      '',
      [
        Validators.required,
        Validators.minLength(8),
        Validators.maxLength(72)
      ]
    ]

  });

  ngOnInit(): void {

    this.token =
      this.route.snapshot.queryParamMap
        .get('token') ?? '';

    if (!this.token) {
      this.tokenMissing = true;
      this.errorMessage =
        'Le lien de réinitialisation est invalide.';
    }
  }

  onSubmit(): void {

    if (
      this.form.invalid ||
      this.loading ||
      this.tokenMissing
    ) {
      this.form.markAllAsTouched();
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';

    const value =
      this.form.getRawValue();

    if (
      value.newPassword !==
      value.confirmPassword
    ) {
      this.errorMessage =
        'La confirmation du mot de passe ne correspond pas.';
      return;
    }

    const request: ResetPasswordRequest = {
      token: this.token,
      newPassword: value.newPassword,
      confirmPassword: value.confirmPassword
    };

    this.loading = true;

    this.authService
      .resetPassword(request)
      .pipe(
        finalize(() => {
          this.loading = false;
        })
      )
      .subscribe({

        next: (response) => {

          this.successMessage =
            response.message;

          this.form.reset();

          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 2000);
        },

        error: (error) => {

          if (error.status === 400) {

            this.errorMessage =
              error.error?.message ??
              error.error?.detail ??
              'Le lien de réinitialisation est invalide ou a expiré.';

          } else {

            this.errorMessage =
              'Une erreur est survenue. Veuillez réessayer.';
          }
        }

      });
  }
}