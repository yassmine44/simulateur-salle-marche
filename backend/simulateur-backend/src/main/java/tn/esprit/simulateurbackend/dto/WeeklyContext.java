package tn.esprit.simulateurbackend.dto;

import java.util.List;

public record WeeklyContext(
        AiAnalysisReport aiAnalysis,
        List<HistoricalEvent> events
) {

    public static WeeklyContext empty() {

        return new WeeklyContext(
                AiAnalysisReport.learning("Neutre"),
                List.of()
        );
    }
}
