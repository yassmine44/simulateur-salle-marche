import { Component, computed, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';

import { MetadataField, PROFILE_PRESETS, ProfilePreset } from '../actuarial.models';
import { FIELD_HINTS, fieldError, PROFILE_SECTIONS, ProfileForm } from './profile-form';

/**
 * Formulaire de profil d'assuré, construit à partir des métadonnées du moteur.
 * Détection de changements par défaut (pas OnPush) : l'état « touché » des
 * champs peut être modifié par le parent à la soumission.
 */
@Component({
  selector: 'app-profile-form',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './profile-form.component.html',
  styleUrl: './profile-form.component.scss'
})
export class ProfileFormComponent {
  readonly form = input.required<ProfileForm>();
  readonly fields = input.required<MetadataField[]>();
  /** Préfixe des identifiants, pour afficher deux formulaires sur une page. */
  readonly idPrefix = input('profile');

  readonly presets = PROFILE_PRESETS;
  readonly hints = FIELD_HINTS;

  readonly sections = computed(() => {
    const byName = new Map(this.fields().map(f => [f.field, f]));
    return PROFILE_SECTIONS.map(section => ({
      title: section.title,
      fields: section.fields.map(name => byName.get(name)).filter((f): f is MetadataField => !!f)
    }));
  });

  applyPreset(preset: ProfilePreset): void {
    this.form().setValue(preset.profile);
    this.form().markAsPristine();
  }

  error(field: MetadataField): string | null {
    return fieldError(this.form(), field);
  }
}
