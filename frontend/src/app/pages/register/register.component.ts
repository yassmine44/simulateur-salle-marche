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

import {
  AuthService,
  RegisterRequest
} from '../../services/auth.service';

import { RecaptchaService } from '../../services/recaptcha.service';


interface Country {
  code: string;
  name: string;
  dialCode: string;
  flag: string;
}


const passwordsMatchValidator: ValidatorFn = (
  control: AbstractControl
): ValidationErrors | null => {

  const password =
    control.get('password')?.value;

  const confirmPassword =
    control.get('confirmPassword')?.value;

  return password === confirmPassword
    ? null
    : {
        passwordsMismatch: true
      };
};


@Component({
  selector: 'app-register',
  standalone: true,
  imports: [
    ReactiveFormsModule
  ],
  templateUrl: './register.component.html',
  styleUrl: './register.component.scss'
})
export class RegisterComponent {

  private readonly fb =
    inject(FormBuilder);

  private readonly authService =
    inject(AuthService);

  private readonly recaptchaService =
    inject(RecaptchaService);


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


  registerForm =
    this.fb.nonNullable.group(
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
        validators:
          passwordsMatchValidator
      }

    );


  get selectedCountry(): Country {

    const countryCode =
      this.registerForm
        .controls
        .countryCode
        .value;


    return (
      this.countries.find(
        country =>
          country.code ===
          countryCode
      )
      ??
      this.countries[0]
    );
  }


  async onSubmit(): Promise<void> {

    if (this.isSubmitting) {
      return;
    }


    this.successMessage = '';
    this.errorMessage = '';


    if (
      this.registerForm.invalid
    ) {

      this.registerForm
        .markAllAsTouched();

      return;
    }


    this.isSubmitting = true;


    try {

      /*
       * =========================================
       * RECAPTCHA ENTERPRISE
       * =========================================
       */

      const recaptchaToken =
        await this.recaptchaService
          .execute(
            'register'
          );


      const {
        firstName,
        lastName,
        countryCode,
        phoneNumber,
        email,
        password
      } =
        this.registerForm
          .getRawValue();


      const request:
        RegisterRequest = {

          firstName,

          lastName,

          countryCode,

          phoneNumber,

          email,

          password,

          recaptchaToken

        };


      /*
       * =========================================
       * REGISTER
       * =========================================
       */

      this.authService
        .register(request)
        .subscribe({

          next: user => {

            this.isSubmitting =
              false;


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


error: (
  error: HttpErrorResponse
) => {

  this.isSubmitting =
    false;


  if (
    error.status === 409
  ) {

    this.errorMessage =
      'Cette adresse e-mail est déjà utilisée.';

    return;
  }


  if (
    error.status === 400
  ) {

    this.errorMessage =
      'Vérifiez les informations saisies, notamment le pays et le numéro de téléphone.';

    return;
  }


  if (
    error.status === 403
  ) {

    this.errorMessage =
      'La vérification de sécurité a échoué. Veuillez réessayer.';

    return;
  }


  if (
    error.status === 503
  ) {

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

      this.isSubmitting =
        false;


      this.errorMessage =
        'Impossible d’effectuer la vérification de sécurité. Veuillez réessayer.';


      console.error(
        'reCAPTCHA register error:',
        error
      );

    }

  }

}