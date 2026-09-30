import { Component, inject } from '@angular/core';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators
} from '@angular/forms';
import { finalize } from 'rxjs';

import {
  AuthService,
  ForgotPasswordRequest
} from '../../services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [
    ReactiveFormsModule
  ],
  templateUrl: './forgot-password.component.html',
  styleUrl: './forgot-password.component.scss'
})
export class ForgotPasswordComponent {

  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  loading = false;

  successMessage = '';
  errorMessage = '';

  readonly form = this.fb.nonNullable.group({
    email: [
      '',
      [
        Validators.required,
        Validators.email,
        Validators.maxLength(150)
      ]
    ]
  });

  onSubmit(): void {

    if (
      this.form.invalid ||
      this.loading
    ) {
      this.form.markAllAsTouched();
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';

    const request: ForgotPasswordRequest = {
      email: this.form.controls.email.value
    };

    this.loading = true;

    this.authService
      .forgotPassword(request)
      .pipe(
        finalize(() => {
          this.loading = false;
        })
      )
      .subscribe({

        next: (response) => {
          this.successMessage =
            response.message;
        },

        error: () => {
          this.errorMessage =
            'Une erreur est survenue. Veuillez réessayer.';
        }

      });
  }
}