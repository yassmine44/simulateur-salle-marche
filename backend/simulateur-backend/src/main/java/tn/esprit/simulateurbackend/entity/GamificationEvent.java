package tn.esprit.simulateurbackend.entity;

import java.util.Locale;

/*
 * Défis historiques isolés du marché live.
 * Chaque événement pointe vers un CSV OHLCV hebdomadaire.
 */
public enum GamificationEvent {

    CRISIS_2008(
            "2008",
            "sp500_weekly_2008_2009.csv",
            "2008 Subprime Mortgage Crisis"
    ),

    COVID_2020(
            "2020",
            "sp500_weekly_2020_covid.csv",
            "2020 COVID-19 Flash Crash"
    ),

    UKRAINE_2022(
            "2022",
            "sp500_weekly_2022.csv",
            "2022 Russia-Ukraine Shock"
    );


    private final String pathKey;

    private final String csvFileName;

    private final String displayName;


    GamificationEvent(
            String pathKey,
            String csvFileName,
            String displayName
    ) {
        this.pathKey = pathKey;
        this.csvFileName = csvFileName;
        this.displayName = displayName;
    }


    public String getPathKey() {
        return pathKey;
    }


    public String getCsvFileName() {
        return csvFileName;
    }


    public String getDisplayName() {
        return displayName;
    }


    public static GamificationEvent fromPath(
            String raw
    ) {

        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(
                    "Événement de gamification obligatoire."
            );
        }

        String key =
                raw.trim().toLowerCase(Locale.ROOT);

        return switch (key) {

            case "2008",
                 "crisis-2008",
                 "crisis_2008",
                 "subprime" ->
                    CRISIS_2008;

            case "2020",
                 "covid-2020",
                 "covid_2020",
                 "covid" ->
                    COVID_2020;

            case "2022",
                 "ukraine-2022",
                 "ukraine_2022",
                 "ukraine" ->
                    UKRAINE_2022;

            default ->
                    throw new IllegalArgumentException(
                            "Événement inconnu : " + raw
                    );
        };
    }
}
