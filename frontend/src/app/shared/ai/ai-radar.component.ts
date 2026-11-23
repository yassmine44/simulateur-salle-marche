import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

export interface AiRadarMetrics {
  historicalPredictionSuccessRate: number | null;
  crashDetectionRating: number | null;
  rallyPredictionRating: number | null;
  explainer?: string;
  available?: boolean;
}

@Component({
  selector: 'app-ai-radar',
  standalone: true,
  imports: [CommonModule],
  template: `
    <section class="ai-radar" aria-label="ML Prediction Model metrics">
      <h3>ML Prediction Model</h3>
      <p class="row">
        <span>Historical Prediction Success Rate</span>
        <strong>{{ pct(metrics?.historicalPredictionSuccessRate) }}</strong>
      </p>
      <p class="row">
        <span>Crash Detection Rating</span>
        <strong>{{ pct(metrics?.crashDetectionRating) }}</strong>
      </p>
      <p class="row">
        <span>Rally Prediction Rating</span>
        <strong>{{ pct(metrics?.rallyPredictionRating) }}</strong>
      </p>
      <p class="footer">
        {{ metrics?.explainer || defaultExplainer }}
      </p>
    </section>
  `,
  styles: [`
    .ai-radar {
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 14px 16px;
      background: #fff;
    }
    h3 {
      margin: 0 0 10px;
      font-size: 1rem;
    }
    .row {
      display: flex;
      justify-content: space-between;
      gap: 12px;
      margin: 6px 0;
      color: #64748b;
      font-size: 0.92rem;
    }
    .row strong {
      color: #0369a1;
    }
    .footer {
      margin: 12px 0 0;
      color: #475569;
      font-size: 0.86rem;
      line-height: 1.45;
    }
  `]
})
export class AiRadarComponent {
  @Input() metrics: AiRadarMetrics | null = null;

  readonly defaultExplainer =
    'Hold-out metrics from the pre-trained Random Forest on 2018–2026 historical data. '
    + 'Start ml-service if scores show as unavailable.';

  pct(value: number | undefined | null): string {
    if (
      value === null
      || value === undefined
      || Number.isNaN(Number(value))
    ) {
      return '—';
    }
    return `${Math.round(Number(value) * 100)}%`;
  }
}
