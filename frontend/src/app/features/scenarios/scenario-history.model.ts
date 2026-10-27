export type ScenarioExecutionStatus =
  | 'RUNNING'
  | 'STOPPED'
  | 'EXPIRED'
  | 'INTERRUPTED';

export interface ScenarioHistoryItem {
  id: number;
  type: string;
  symbol: string;
  intensity: number;
  status: ScenarioExecutionStatus;
  startedAt: string;
  scheduledEndsAt: string;
  actualEndedAt: string | null;
}

export interface ScenarioHistoryResponse {
  content: ScenarioHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ScenarioReportResponse {
  id: number;
  type: string;
  symbol: string;
  intensity: number;
  status: ScenarioExecutionStatus;
  startedAt: string;
  scheduledEndsAt: string;
  actualEndedAt: string | null;
  plannedDurationSeconds: number;
  actualDurationSeconds: number | null;
  completionPercent: number | null;
}
