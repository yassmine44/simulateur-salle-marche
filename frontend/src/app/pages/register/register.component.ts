import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators
} from '@angular/forms';
import { finalize } from 'rxjs';

import {
  AuthService,
  RegisterRequest
} from '../../services/auth.service';

interface Country {
  code: string;
  name: string;
  dialCode: string;
  flag: string;
}

const passwordsMatchValidator: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {

  const password = control.get('password')?.value;
  const confirmPassword = control.get('confirmPassword')?.value;

  return password === confirmPassword
    ? null
    : { passwordsMismatch: true };
};

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {

  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);

  readonly countries: Country[] = [
    {
      code: 'TN',
      name: 'Tunisie',
      dialCode: '+216',
      flag: '🇹🇳'
    },
    {
      code: 'FR',
      name: 'France',
      dialCode: '+33',
      flag: '🇫🇷'
    },
    {
      code: 'US',
      name: 'États-Unis',
      dialCode: '+1',
      flag: '🇺🇸'
    },
    {
      code: 'CA',
      name: 'Canada',
      dialCode: '+1',
      flag: '🇨🇦'
    },
    {
      code: 'GB',
      name: 'Royaume-Uni',
      dialCode: '+44',
      flag: '🇬🇧'
    },
    {
      code: 'DE',
      name: 'Allemagne',
      dialCode: '+49',
      flag: '🇩🇪'
    },
    {
      code: 'IT',
      name: 'Italie',
      dialCode: '+39',
      flag: '🇮🇹'
    },
    {
      code: 'ES',
      name: 'Espagne',
      dialCode: '+34',
      flag: '🇪🇸'
    },
    {
      code: 'AE',
      name: 'Émirats arabes unis',
      dialCode: '+971',
      flag: '🇦🇪'
    }
  ];

  isSubmitting = false;

  successMessage = '';
  errorMessage = '';

  registerForm = this.fb.nonNullable.group(
    {
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

      countryCode: [
        'TN',
        [
          Validators.required
        ]
      ],

      phoneNumber: [
        '',
        [
          Validators.required,
          Validators.maxLength(30)
        ]
      ],

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
          Validators.required,
          Validators.minLength(8)
        ]
      ],

      confirmPassword: [
        '',
        [
          Validators.required
        ]
      ]
    },
    {
      validators: passwordsMatchValidator
    }
  );

  get selectedCountry(): Country {
    const countryCode =
      this.registerForm.controls.countryCode.value;

    return (
      this.countries.find(
        country => country.code === countryCode
      ) ?? this.countries[0]
    );
  }

  onSubmit(): void {

    if (this.isSubmitting) {
      return;
    }

    this.successMessage = '';
    this.errorMessage = '';

    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    const {
      firstName,
      lastName,
      countryCode,
      phoneNumber,
      email,
      password
    } = this.registerForm.getRawValue();

    const request: RegisterRequest = {
      firstName,
      lastName,
      countryCode,
      phoneNumber,
      email,
      password
    };

    this.isSubmitting = true;

    this.authService
      .register(request)
      .pipe(
        finalize(() => {
          this.isSubmitting = false;
        })
      )
      .subscribe({

        next: user => {

          this.successMessage =
            `Compte créé avec succès pour ${user.email}.`;

          this.registerForm.reset({
            firstName: '',
            lastName: '',
            countryCode: 'TN',
            phoneNumber: '',
            email: '',
            password: '',
            confirmPassword: ''
          });
        },

        error: (error: HttpErrorResponse) => {

          if (error.status === 409) {

            this.errorMessage =
              'Cette adresse e-mail est déjà utilisée.';

          } else if (error.status === 400) {

            this.errorMessage =
              'Vérifiez les informations saisies, notamment le pays et le numéro de téléphone.';

          } else {

            this.errorMessage =
              'Une erreur est survenue. Veuillez réessayer.';
          }
        }
      });
  }
}