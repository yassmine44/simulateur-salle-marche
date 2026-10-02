import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';

import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';

import { finalize } from 'rxjs';

import {
  UserProfileService,
  UpdateProfileRequest,
  ChangePasswordRequest,
  SetPasswordRequest
} from '../../services/user-profile.service';

import { AuthStateService } from '../../services/auth-state.service';


interface Country {
  code: string;
  name: string;
  dialCode: string;
}


@Component({
  selector: 'app-profile',
  standalone: true,

  imports: [
    CommonModule,
    ReactiveFormsModule
  ],

  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss'
})
export class ProfileComponent implements OnInit {

  private readonly fb =
    inject(FormBuilder);

  private readonly profileService =
    inject(UserProfileService);

  private readonly authState =
    inject(AuthStateService);


  /*
   * =========================
   * PROFILE STATE
   * =========================
   */

  loading = true;
  saving = false;

  successMessage = '';
  errorMessage = '';


  /*
   * =========================
   * PASSWORD STATE
   * =========================
   */

  changingPassword = false;

  /*
   * true :
   * utilisateur avec mot de passe local.
   *
   * false :
   * utilisateur Google-only.
   */
  hasLocalPassword = true;

  passwordSuccessMessage = '';
  passwordErrorMessage = '';


  /*
   * =========================
   * COUNTRIES
   * =========================
   */

  readonly countries: Country[] = [

    {
      code: 'TN',
      name: 'Tunisie',
      dialCode: '+216'
    },

    {
      code: 'FR',
      name: 'France',
      dialCode: '+33'
    },

    {
      code: 'US',
      name: 'États-Unis',
      dialCode: '+1'
    },

    {
      code: 'CA',
      name: 'Canada',
      dialCode: '+1'
    },

    {
      code: 'GB',
      name: 'Royaume-Uni',
      dialCode: '+44'
    },

    {
      code: 'DE',
      name: 'Allemagne',
      dialCode: '+49'
    },

    {
      code: 'IT',
      name: 'Italie',
      dialCode: '+39'
    },

    {
      code: 'ES',
      name: 'Espagne',
      dialCode: '+34'
    },

    {
      code: 'AE',
      name: 'Émirats arabes unis',
      dialCode: '+971'
    }

  ];


  /*
   * =========================
   * PROFILE FORM
   * =========================
   */

  readonly form =
    this.fb.nonNullable.group({

      firstName: [
        '',
        [
          Validators.required,
          Validators.maxLength(100)
        ]
      ],

      lastName: [
        '',
        [
          Validators.required,
          Validators.maxLength(100)
        ]
      ],

      email: [
        {
          value: '',
          disabled: true
        }
      ],

      role: [
        {
          value: '',
          disabled: true
        }
      ],

      countryCode: [
        'TN',
        Validators.required
      ],

      phoneNumber: [
        '',
        [
          Validators.required,
          Validators.maxLength(30)
        ]
      ]

    });


  /*
   * =========================
   * PASSWORD FORM
   * =========================
   */

  readonly passwordForm =
    this.fb.nonNullable.group({

      currentPassword: [
        '',
        [
          Validators.required
        ]
      ],

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


  /*
   * =========================
   * INITIALIZATION
   * =========================
   */

  ngOnInit(): void {

    this.loadProfile();

  }


  /*
   * =========================
   * LOAD PROFILE
   * =========================
   */

  loadProfile(): void {

    this.loading = true;

    this.successMessage = '';
    this.errorMessage = '';

    this.profileService
      .getProfile()
      .pipe(

        finalize(() => {

          this.loading = false;

        })

      )
      .subscribe({

        next: (profile) => {

          /*
           * Déterminer si le compte possède
           * déjà un mot de passe local.
           */
          this.hasLocalPassword =
            profile.hasLocalPassword;


          /*
           * Pour un compte Google-only,
           * currentPassword ne doit pas
           * être obligatoire.
           */
          const currentPasswordControl =
            this.passwordForm.controls.currentPassword;

          if (this.hasLocalPassword) {

            currentPasswordControl.setValidators([
              Validators.required
            ]);

          } else {

            currentPasswordControl.clearValidators();

            currentPasswordControl.setValue('');

          }

          currentPasswordControl
            .updateValueAndValidity();


          /*
           * Charger les informations
           * du profil.
           *
           * Google peut créer un compte
           * sans pays ni téléphone.
           */
          this.form.patchValue({

            firstName:
              profile.firstName,

            lastName:
              profile.lastName,

            email:
              profile.email,

            role:
              profile.role,

            countryCode:
              profile.countryCode ?? 'TN',

            phoneNumber:
              profile.phoneNumber ?? ''

          });

        },


        error: (error) => {

          if (error.status === 401) {

            this.errorMessage =
              'Votre session a expiré. Reconnectez-vous.';

          } else {

            this.errorMessage =
              'Impossible de charger votre profil.';

          }

        }

      });

  }


  /*
   * =========================
   * UPDATE PROFILE
   * =========================
   */

  onSubmit(): void {

    if (
      this.form.invalid ||
      this.saving
    ) {

      this.form.markAllAsTouched();

      return;

    }


    this.successMessage = '';
    this.errorMessage = '';


    const value =
      this.form.getRawValue();


    const request: UpdateProfileRequest = {

      firstName:
        value.firstName,

      lastName:
        value.lastName,

      countryCode:
        value.countryCode,

      phoneNumber:
        value.phoneNumber

    };


    this.saving = true;


    this.profileService
      .updateProfile(request)
      .pipe(

        finalize(() => {

          this.saving = false;

        })

      )
      .subscribe({

        next: (profile) => {

          this.successMessage =
            'Profil mis à jour avec succès.';


          /*
           * Mettre à jour le formulaire
           * avec les données normalisées
           * retournées par le backend.
           */
          this.form.patchValue({

            firstName:
              profile.firstName,

            lastName:
              profile.lastName,

            countryCode:
              profile.countryCode ?? 'TN',

            phoneNumber:
              profile.phoneNumber ?? ''

          });


          /*
           * Mettre également à jour
           * AuthState afin que la TopBar
           * affiche immédiatement les
           * nouvelles informations.
           */
          const currentUser =
            this.authState.currentUser;

          if (currentUser) {

            this.authState.setUser({

              ...currentUser,

              firstName:
                profile.firstName,

              lastName:
                profile.lastName,

              countryCode:
                profile.countryCode,

              phoneNumber:
                profile.phoneNumber

            });

          }

        },


        error: (error) => {

          if (error.status === 400) {

            this.errorMessage =
              'Vérifiez les informations saisies, notamment le pays et le numéro de téléphone.';

          } else if (error.status === 401) {

            this.errorMessage =
              'Votre session a expiré. Reconnectez-vous.';

          } else if (error.status === 403) {

            this.errorMessage =
              'La requête a été refusée par la protection de sécurité.';

          } else {

            this.errorMessage =
              'Une erreur est survenue lors de la mise à jour.';

          }

        }

      });

  }


  /*
   * =========================
   * PASSWORD
   * =========================
   */

  changePassword(): void {

    if (
      this.passwordForm.invalid ||
      this.changingPassword
    ) {

      this.passwordForm.markAllAsTouched();

      return;

    }


    this.passwordSuccessMessage = '';
    this.passwordErrorMessage = '';


    const value =
      this.passwordForm.getRawValue();


    /*
     * Vérifier que le nouveau mot
     * de passe correspond à sa
     * confirmation.
     */
    if (
      value.newPassword !==
      value.confirmPassword
    ) {

      this.passwordErrorMessage =
        'La confirmation du mot de passe ne correspond pas.';

      return;

    }


    this.changingPassword = true;


    /*
     * =====================================
     * CAS 1 :
     * COMPTE AVEC MOT DE PASSE LOCAL
     * =====================================
     */

    if (this.hasLocalPassword) {

      const request: ChangePasswordRequest = {

        currentPassword:
          value.currentPassword,

        newPassword:
          value.newPassword,

        confirmPassword:
          value.confirmPassword

      };


      this.profileService
        .changePassword(request)
        .pipe(

          finalize(() => {

            this.changingPassword = false;

          })

        )
        .subscribe({

          next: () => {

            this.passwordSuccessMessage =
              'Mot de passe modifié avec succès.';

            /*
             * Ne jamais garder les mots
             * de passe après l'opération.
             */
            this.passwordForm.reset();

          },


          error: (error) => {

            this.handlePasswordError(error);

          }

        });


      return;

    }


    /*
     * =====================================
     * CAS 2 :
     * COMPTE GOOGLE SANS MOT DE PASSE LOCAL
     * =====================================
     */

    const request: SetPasswordRequest = {

      newPassword:
        value.newPassword,

      confirmPassword:
        value.confirmPassword

    };


    this.profileService
      .setPassword(request)
      .pipe(

        finalize(() => {

          this.changingPassword = false;

        })

      )
      .subscribe({

        next: () => {

          this.passwordSuccessMessage =
            'Mot de passe défini avec succès. Vous pouvez désormais vous connecter avec Google ou avec votre e-mail et votre mot de passe.';


          /*
           * Le compte possède maintenant
           * un mot de passe local.
           */
          this.hasLocalPassword = true;


          /*
           * À partir de maintenant,
           * l'ancien mot de passe devient
           * obligatoire pour toute
           * modification future.
           */
          const currentPasswordControl =
            this.passwordForm
              .controls
              .currentPassword;

          currentPasswordControl
            .setValidators([
              Validators.required
            ]);

          currentPasswordControl
            .updateValueAndValidity();


          /*
           * Nettoyer le formulaire.
           */
          this.passwordForm.reset();


          /*
           * Synchroniser AuthState.
           */
          const currentUser =
            this.authState.currentUser;

          if (currentUser) {

            this.authState.setUser({

              ...currentUser,

              hasLocalPassword: true

            });

          }

        },


        error: (error) => {

          this.handlePasswordError(error);

        }

      });

  }


  /*
   * =========================
   * PASSWORD ERRORS
   * =========================
   */

  private handlePasswordError(
    error: any
  ): void {

    if (error.status === 400) {

      this.passwordErrorMessage =
        error.error?.message ??
        error.error?.detail ??
        'Vérifiez les informations du mot de passe.';

    } else if (error.status === 401) {

      this.passwordErrorMessage =
        'Votre session a expiré. Reconnectez-vous.';

    } else if (error.status === 403) {

      this.passwordErrorMessage =
        'La requête a été refusée par la protection de sécurité.';

    } else {

      this.passwordErrorMessage =
        'Impossible de mettre à jour le mot de passe.';

    }

  }

}