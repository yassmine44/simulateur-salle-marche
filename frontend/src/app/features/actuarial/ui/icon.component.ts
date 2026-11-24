import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/** Icônes du module (tracés SVG 24×24, trait de 1,8 px, sans dépendance externe). */
const ICONS = {
  shield: ['M12 3l7 3v6c0 4.5-3 7.5-7 9-4-1.5-7-4.5-7-9V6l7-3z', 'M9 12l2 2 4-4'],
  receipt: ['M6 3h12v18l-3-2-3 2-3-2-3 2V3z', 'M9 8h6', 'M9 12h6', 'M9 16h3'],
  scale: ['M12 4v16', 'M7 20h10', 'M5 8h14', 'M5 8l-3 6h6l-3-6', 'M19 8l-3 6h6l-3-6'],
  staircase: ['M4 20V5h4v4h4v4h4v4h4v3H4z'],
  chart: ['M4 19h16', 'M7 16v-5', 'M12 16V7', 'M17 16v-8'],
  user: ['M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z', 'M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6'],
  car: ['M5 16l1.5-5h11L19 16', 'M4 16h16v3H4z', 'M7.5 19v1.5', 'M16.5 19v1.5'],
  pin: ['M12 21s-7-6-7-11a7 7 0 0 1 14 0c0 5-7 11-7 11z', 'M12 12.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z'],
  sparkles: ['M12 3l1.8 4.7 4.7 1.8-4.7 1.8L12 16l-1.8-4.7-4.7-1.8 4.7-1.8L12 3z', 'M19 15l.8 2 2 .8-2 .8-.8 2-.8-2-2-.8 2-.8z'],
  history: ['M3 12a9 9 0 1 0 3-6.7', 'M3 4v4h4', 'M12 8v4l3 2'],
  wallet: ['M3 7h15a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H3z', 'M3 7l12-4v4', 'M16 13.5h.01'],
  gauge: ['M4 17a8 8 0 1 1 16 0', 'M12 17l4-5'],
  bolt: ['M13 2L4 14h7l-1 8 9-12h-7l1-8z'],
  sliders: ['M4 6h9', 'M17 6h3', 'M4 12h3', 'M11 12h9', 'M4 18h11', 'M19 18h1',
    'M13 6a2 2 0 1 0 4 0 2 2 0 0 0-4 0', 'M7 12a2 2 0 1 0 4 0 2 2 0 0 0-4 0', 'M15 18a2 2 0 1 0 4 0 2 2 0 0 0-4 0'],
  database: ['M12 8c4.4 0 8-1.3 8-3s-3.6-3-8-3-8 1.3-8 3 3.6 3 8 3z', 'M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5', 'M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3'],
  info: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 11v5', 'M12 8h.01']
} as const;

export type IconName = keyof typeof ICONS;

@Component({
  selector: 'app-icon',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
         stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false">
      @for (d of paths(); track $index) { <path [attr.d]="d" /> }
    </svg>
  `,
  styles: `
    :host { display: inline-flex; width: 1em; height: 1em; }
    svg { width: 100%; height: 100%; }
  `
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly paths = computed(() => ICONS[this.name()]);
}
