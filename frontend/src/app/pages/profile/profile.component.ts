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
  UpdateProfileRequest
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

  private readonly fb = inject(FormBuilder);
  private readonly profileService = inject(UserProfileService);
  private readonly authState = inject(AuthStateService);

  loading = true;
  saving = false;

  successMessage = '';
  errorMessage = '';

  readonly countries: Country[] = [
    { code: 'TN', name: 'Tunisie', dialCode: '+216' },
    { code: 'FR', name: 'France', dialCode: '+33' },
    { code: 'US', name: 'États-Unis', dialCode: '+1' },
    { code: 'CA', name: 'Canada', dialCode: '+1' },
    { code: 'GB', name: 'Royaume-Uni', dialCode: '+44' },
    { code: 'DE', name: 'Allemagne', dialCode: '+49' },
    { code: 'IT', name: 'Italie', dialCode: '+39' },
    { code: 'ES', name: 'Espagne', dialCode: '+34' },
    { code: 'AE', name: 'Émirats arabes unis', dialCode: '+971' }
  ];

  readonly form = this.fb.nonNullable.group({
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
      { value: '', disabled: true }
    ],

    role: [
      { value: '', disabled: true }
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

  ngOnInit(): void {
    this.loadProfile();
  }

  loadProfile(): void {

    this.loading = true;
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

          this.form.patchValue({
            firstName: profile.firstName,
            lastName: profile.lastName,
            email: profile.email,
            role: profile.role,
            countryCode: profile.countryCode,
            phoneNumber: profile.phoneNumber
          });
        },

        error: () => {
          this.errorMessage =
            'Impossible de charger votre profil.';
        }

      });
  }

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
      firstName: value.firstName,
      lastName: value.lastName,
      countryCode: value.countryCode,
      phoneNumber: value.phoneNumber
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

          this.form.patchValue({
            firstName: profile.firstName,
            lastName: profile.lastName,
            countryCode: profile.countryCode,
            phoneNumber: profile.phoneNumber
          });

          const currentUser =
            this.authState.currentUser;

          if (currentUser) {
            this.authState.setUser({
              ...currentUser,
              firstName: profile.firstName,
              lastName: profile.lastName,
              countryCode: profile.countryCode,
              phoneNumber: profile.phoneNumber
            });
          }
        },

        error: (error) => {

          if (error.status === 400) {
            this.errorMessage =
              'Vérifiez les informations saisies, notamment le pays et le numéro de téléphone.';
          } else if (error.status === 403) {
            this.errorMessage =
              'La session de sécurité est invalide. Reconnectez-vous.';
          } else {
            this.errorMessage =
              'Une erreur est survenue lors de la mise à jour.';
          }
        }

      });
  }
}