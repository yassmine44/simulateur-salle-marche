-- Executer dans la base utilisee par Spring Boot.
-- Elargit uniquement les types acceptes, sans supprimer l'historique.
BEGIN;
SET LOCAL lock_timeout = '5s';
ALTER TABLE public.scenario_executions
    DROP CONSTRAINT IF EXISTS scenario_executions_scenario_type_check;
ALTER TABLE public.scenario_executions
    ADD CONSTRAINT scenario_executions_scenario_type_check
    CHECK (scenario_type IN (
        'CRASH', 'LIQUIDITY_CRISIS', 'SELLING_PANIC', 'BULL_RALLY', 'FLASH_CRASH'
    ));
COMMIT;

SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'public.scenario_executions'::regclass
  AND conname = 'scenario_executions_scenario_type_check';