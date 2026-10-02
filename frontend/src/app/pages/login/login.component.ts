import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AuthStateService } from '../../services/auth-state.service';
import {
  AuthService,
  LoginRequest
} from '../../services/auth.service';

import { RecaptchaService } from '../../services/recaptcha.service';


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

  private readonly authState =
    inject(AuthStateService);

  private readonly fb =
    inject(FormBuilder);

  private readonly authService =
    inject(AuthService);

  private readonly router =
    inject(Router);

  private readonly recaptchaService =
    inject(RecaptchaService);


  isSubmitting = false;
  errorMessage = '';


  loginForm =
    this.fb.nonNullable.group({

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


  async onSubmit(): Promise<void> {

    if (this.isSubmitting) {
      return;
    }

    this.errorMessage = '';


    if (this.loginForm.invalid) {

      this.loginForm.markAllAsTouched();

      return;
    }


    this.isSubmitting = true;


    try {

      /*
       * =========================================
       * RECAPTCHA ENTERPRISE
       * =========================================
       *
       * Invisible :
       * aucune checkbox "I'm not a robot".
       */
      const recaptchaToken =
        await this.recaptchaService.execute(
          'login'
        );


      const {
        email,
        password
      } = this.loginForm.getRawValue();


      const request: LoginRequest = {

        email,

        password,

        recaptchaToken
      };


      /*
       * =========================================
       * LOGIN
       * =========================================
       */
      this.authService
        .login(request)
        .subscribe({

          next: (user) => {

            /*
             * La session backend vient
             * d'être créée.
             *
             * On récupère ensuite le token
             * CSRF de la nouvelle session.
             */
            this.authService
              .csrf()
              .subscribe({

                next: () => {

                  this.authState
                    .setUser(user);

                  this.isSubmitting =
                    false;


                  this.router.navigate([
                    '/app/dashboard'
                  ]);

                },


                error: () => {

                  this.isSubmitting =
                    false;

                  this.errorMessage =
                    'Impossible d’initialiser la session sécurisée.';

                }

              });

          },


          error: (
            error: HttpErrorResponse
          ) => {

            this.isSubmitting =
              false;


            /*
             * Mauvais e-mail /
             * mot de passe
             */
            if (error.status === 401) {

              this.errorMessage =
                'Adresse e-mail ou mot de passe incorrect.';

              return;
            }


            /*
             * Validation DTO
             */
            if (error.status === 400) {

              this.errorMessage =
                'Veuillez vérifier les informations saisies.';

              return;
            }


            /*
             * reCAPTCHA rejeté
             */
            if (error.status === 403) {

              this.errorMessage =
                'La vérification de sécurité a échoué. Veuillez réessayer.';

              return;
            }


            /*
             * Google reCAPTCHA indisponible
             */
            if (error.status === 503) {

              this.errorMessage =
                'Le service de vérification de sécurité est temporairement indisponible.';

              return;
            }
            

  if (
    error.status === 429
  ) {

    this.errorMessage =
      'Trop de tentatives de création de compte. Veuillez patienter avant de réessayer.';

    return;
  }


            this.errorMessage =
              'Une erreur est survenue. Veuillez réessayer.';

          }

        });


    } catch (error) {

      /*
       * Échec avant même l'appel backend :
       * script Google non chargé,
       * navigateur bloquant reCAPTCHA, etc.
       */
      this.isSubmitting =
        false;

      this.errorMessage =
        'Impossible d’effectuer la vérification de sécurité. Veuillez réessayer.';

      console.error(
        'reCAPTCHA error:',
        error
      );

    }

  }


  /*
   * =========================================
   * GOOGLE OAUTH
   * =========================================
   */

  loginWithGoogle(): void {

    window.location.href =
      'http://localhost:8081/oauth2/authorization/google';

  }

}