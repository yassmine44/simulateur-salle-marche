import { Component, inject } from '@angular/core';
import {
  ReactiveFormsModule,
  FormBuilder,
  Validators
} from '@angular/forms';

import {
  AuthService,
  ForgotPasswordRequest
} from '../../services/auth.service';

import { RecaptchaService } from '../../services/recaptcha.service';

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

  private readonly fb =
    inject(FormBuilder);

  private readonly authService =
    inject(AuthService);

  private readonly recaptchaService =
    inject(RecaptchaService);

  loading = false;

  successMessage = '';
  errorMessage = '';

  readonly form =
    this.fb.nonNullable.group({

      email: [
        '',
        [
          Validators.required,
          Validators.email,
          Validators.maxLength(150)
        ]
      ]

    });


  async onSubmit(): Promise<void> {

    if (
      this.form.invalid ||
      this.loading
    ) {

      this.form.markAllAsTouched();

      return;
    }

    this.successMessage = '';
    this.errorMessage = '';

    this.loading = true;

    try {

      /*
       * reCAPTCHA Enterprise invisible
       */
      const recaptchaToken =
        await this.recaptchaService.execute(
          'forgot_password'
        );

      const request:
        ForgotPasswordRequest = {

          email:
            this.form.controls.email.value,

          recaptchaToken
        };


      this.authService
        .forgotPassword(request)
        .subscribe({

          next: (response) => {

            this.loading = false;

            this.successMessage =
              response.message;

          },

          error: (error) => {

            this.loading = false;

            if (error.status === 400) {

              this.errorMessage =
                'Veuillez vérifier l’adresse e-mail saisie.';

              return;
            }

            if (error.status === 403) {

              this.errorMessage =
                'La vérification de sécurité a échoué. Veuillez réessayer.';

              return;
            }

            if (error.status === 503) {

              this.errorMessage =
                'Le service de vérification de sécurité est temporairement indisponible.';

              return;
            }

            this.errorMessage =
              'Une erreur est survenue. Veuillez réessayer.';

          }

        });

    } catch (error) {

      this.loading = false;

      this.errorMessage =
        'Impossible d’effectuer la vérification de sécurité. Veuillez réessayer.';

      console.error(
        'reCAPTCHA forgot-password error:',
        error
      );

    }

  }

}