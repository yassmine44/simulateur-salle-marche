import { PROFILE_PRESETS } from '../actuarial.models';
import { METADATA } from '../testing/actuarial-fixtures';
import { createProfileForm, fieldError, profileValue } from './profile-form';

describe('profile form', () => {
  const young = PROFILE_PRESETS[0].profile;

  it('is valid for a preset profile and converts values to the API contract', () => {
    const form = createProfileForm(METADATA.fields, young);
    form.controls.driverAge.setValue('35' as unknown as number);

    expect(form.valid).toBeTrue();
    const value = profileValue(form, METADATA.fields);
    expect(value.driverAge).toBe(35);
    expect(value.area).toBe('E');
  });

  it('enforces the bounds given by the engine metadata', () => {
    const form = createProfileForm(METADATA.fields, young);
    const driverAge = METADATA.fields.find(f => f.field === 'driverAge')!;

    form.controls.driverAge.setValue(16);
    form.controls.driverAge.markAsTouched();
    expect(form.valid).toBeFalse();
    expect(fieldError(form, driverAge)).toBe('Entre 18 et 100.');

    form.controls.driverAge.setValue(20.5);
    expect(fieldError(form, driverAge)).toBe('Nombre entier attendu.');

    form.controls.driverAge.setValue(null);
    expect(fieldError(form, driverAge)).toBe('Champ obligatoire.');
  });

  it('shows no error before the field is touched', () => {
    const form = createProfileForm(METADATA.fields, young);
    form.controls.bonusMalus.setValue(10);
    expect(fieldError(form, METADATA.fields.find(f => f.field === 'bonusMalus')!)).toBeNull();
  });
});
