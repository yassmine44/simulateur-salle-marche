import { FormControl, FormGroup, Validators } from '@angular/forms';

import { MetadataField, PolicyProfile, ProfileField } from '../actuarial.models';

export type ProfileForm = FormGroup<{ [K in ProfileField]: FormControl<PolicyProfile[K] | null> }>;

/** Regroupement métier des facteurs dans le formulaire. */
export const PROFILE_SECTIONS: ReadonlyArray<{ title: string; fields: ProfileField[] }> = [
  { title: 'Conducteur', fields: ['driverAge', 'bonusMalus'] },
  { title: 'Véhicule', fields: ['vehiclePower', 'vehicleAge', 'vehicleBrand', 'vehicleGas'] },
  { title: 'Localisation', fields: ['area', 'region', 'density'] }
];

/** Aides contextuelles affichées sous certains champs. */
export const FIELD_HINTS: Partial<Record<ProfileField, string>> = {
  bonusMalus: '50 = bonus maximal, 100 = neutre, au-delà = malus',
  density: 'Habitants par km² de la commune',
  area: 'A = rural … F = centre urbain dense'
};

/**
 * Construit le formulaire à partir des métadonnées du moteur : les bornes
 * et les valeurs acceptées ne sont jamais codées en dur côté interface.
 */
export function createProfileForm(fields: MetadataField[], initial: PolicyProfile): ProfileForm {
  const controls = {} as Record<ProfileField, FormControl<string | number | null>>;
  for (const field of fields) {
    const validators = [Validators.required];
    if (field.type === 'integer') {
      validators.push(Validators.min(field.min ?? 0), Validators.max(field.max ?? Number.MAX_SAFE_INTEGER));
      validators.push(Validators.pattern(/^-?\d+$/));
    }
    controls[field.field] = new FormControl<string | number | null>(initial[field.field], {
      validators
    });
  }
  return new FormGroup(controls) as unknown as ProfileForm;
}

/** Valeur du formulaire convertie dans le contrat de l'API (nombres entiers). */
export function profileValue(form: ProfileForm, fields: MetadataField[]): PolicyProfile {
  const raw = form.getRawValue() as Record<ProfileField, string | number | null>;
  const profile = {} as Record<ProfileField, string | number>;
  for (const field of fields) {
    const value = raw[field.field];
    profile[field.field] = field.type === 'integer' ? Number(value) : String(value);
  }
  return profile as unknown as PolicyProfile;
}

/** Message d'erreur d'un champ, ou null s'il est valide. */
export function fieldError(form: ProfileForm, field: MetadataField): string | null {
  const control = form.controls[field.field];
  if (!control || control.valid || !control.touched) {
    return null;
  }
  if (control.hasError('required')) {
    return 'Champ obligatoire.';
  }
  if (control.hasError('pattern')) {
    return 'Nombre entier attendu.';
  }
  if (control.hasError('min') || control.hasError('max')) {
    return `Entre ${field.min} et ${field.max}.`;
  }
  return 'Valeur invalide.';
}
