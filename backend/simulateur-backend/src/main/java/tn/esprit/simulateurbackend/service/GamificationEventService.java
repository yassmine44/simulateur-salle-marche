package tn.esprit.simulateurbackend.service;

import org.springframework.stereotype.Service;

import tn.esprit.simulateurbackend.dto.HistoricalEvent;
import tn.esprit.simulateurbackend.entity.GamificationEvent;
import tn.esprit.simulateurbackend.entity.ImpactLevel;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/*
 * Calendrier économique historique figé pour les 3 défis.
 * Les dates sont mappées à la semaine active (fenêtre de 7 jours).
 */
@Service
public class GamificationEventService {

    private final Map<GamificationEvent, List<HistoricalEvent>>
            catalog = new EnumMap<>(GamificationEvent.class);


    public GamificationEventService() {

        catalog.put(
                GamificationEvent.CRISIS_2008,
                List.of(
                        event(
                                "2008-01-22",
                                "Fed Emergency Rate Cut",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2008-03-16",
                                "Bear Stearns Collapse",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2008-07-11",
                                "IndyMac Failure",
                                ImpactLevel.MEDIUM
                        ),
                        event(
                                "2008-09-07",
                                "GSE Conservatorship",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2008-09-15",
                                "Lehman Brothers Bankruptcy",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2008-09-16",
                                "AIG Bailout",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2008-10-03",
                                "TARP Passed",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2008-10-06",
                                "Worst Week — Dow falls 18%",
                                ImpactLevel.HIGH
                        )
                )
        );

        catalog.put(
                GamificationEvent.COVID_2020,
                List.of(
                        event(
                                "2020-02-19",
                                "Market Peak",
                                ImpactLevel.LOW
                        ),
                        event(
                                "2020-02-24",
                                "First Major Crack",
                                ImpactLevel.MEDIUM
                        ),
                        event(
                                "2020-03-03",
                                "Fed Emergency Cut",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2020-03-09",
                                "Black Monday I",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2020-03-11",
                                "Pandemic Declared",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2020-03-12",
                                "Black Thursday",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2020-03-15",
                                "Zero Interest Rates",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2020-03-16",
                                "Black Monday II",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2020-03-23",
                                "The Bottom & Unlimited QE",
                                ImpactLevel.HIGH
                        )
                )
        );

        catalog.put(
                GamificationEvent.UKRAINE_2022,
                List.of(
                        event(
                                "2022-01-26",
                                "Hawkish Pivot",
                                ImpactLevel.MEDIUM
                        ),
                        event(
                                "2022-02-24",
                                "Russia Invades Ukraine",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2022-03-08",
                                "Oil Shock",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2022-03-16",
                                "First Rate Hike",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2022-05-04",
                                "50 bps Hike",
                                ImpactLevel.MEDIUM
                        ),
                        event(
                                "2022-06-10",
                                "Hot CPI Print 8.6%",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2022-06-15",
                                "75 bps Hike",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2022-09-21",
                                "Third 75 bps Hike",
                                ImpactLevel.HIGH
                        ),
                        event(
                                "2022-10-13",
                                "The Turnaround",
                                ImpactLevel.HIGH
                        )
                )
        );
    }


    public List<HistoricalEvent> getEventsForWeek(
            GamificationEvent challenge,
            String weekStartDate,
            String nextWeekStartDate
    ) {

        LocalDate weekStart =
                LocalDate.parse(weekStartDate);

        LocalDate weekEndExclusive =
                nextWeekStartDate != null
                        && !nextWeekStartDate.isBlank()
                        ? LocalDate.parse(nextWeekStartDate)
                        : weekStart.plusDays(7);

        List<HistoricalEvent> matches =
                new ArrayList<>();

        for (HistoricalEvent item :
                catalog.getOrDefault(
                        challenge,
                        List.of()
                )) {

            LocalDate eventDate =
                    LocalDate.parse(item.date());

            if (
                    !eventDate.isBefore(weekStart)
                    &&
                    eventDate.isBefore(weekEndExclusive)
            ) {
                matches.add(item);
            }
        }

        return List.copyOf(matches);
    }


    private static HistoricalEvent event(
            String date,
            String description,
            ImpactLevel impact
    ) {

        return new HistoricalEvent(
                date,
                description,
                impact
        );
    }
}
