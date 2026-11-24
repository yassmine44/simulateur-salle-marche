package tn.esprit.simulateurbackend.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tn.esprit.simulateurbackend.entity.Course;
import tn.esprit.simulateurbackend.entity.CourseLevel;
import tn.esprit.simulateurbackend.entity.Lesson;
import tn.esprit.simulateurbackend.entity.QuizQuestion;
import tn.esprit.simulateurbackend.repository.CourseRepository;

/**
 * Ajoute le cours « Assurance et actuariat » au module Formation.
 *
 * <p>Idempotent : le cours n'est créé que s'il n'existe pas déjà (recherche
 * par titre), y compris sur une base existante.
 *
 * <p>Exécuté sur {@link ApplicationReadyEvent}, donc <em>après</em>
 * {@link FormationDataInitializer} : celui-ci ne crée le catalogue de base
 * que si la base ne contient aucun cours ; s'exécuter avant lui l'empêcherait
 * de le faire.
 */
@Component
@Profile("!banking-test")
public class ActuarialFormationInitializer {

    public static final String COURSE_TITLE = "Assurance et actuariat";

    private static final Logger log = LoggerFactory.getLogger(ActuarialFormationInitializer.class);

    private final CourseRepository courseRepository;

    public ActuarialFormationInitializer(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (courseRepository.existsByTitle(COURSE_TITLE)) {
            return;
        }
        courseRepository.save(actuarialCourse());
        log.info("Cours de formation « {} » ajouté.", COURSE_TITLE);
    }

    Course actuarialCourse() {
        Course course = new Course();
        course.setTitle(COURSE_TITLE);
        course.setDescription(
                "Comprendre comment un assureur fixe le prix d'un contrat, explique ce prix et "
                        + "provisionne les sinistres à venir, avec les modèles du simulateur.");
        course.setLevel(CourseLevel.INTERMEDIATE);
        course.setCategory("Actuariat");
        course.setDisplayOrder(5);
        course.setPublished(true);

        Lesson pricing = lesson(course, 1, 8, "Fréquence, coût et prime pure", """
                Un assureur ne connaît pas à l'avance les sinistres de ses clients : il raisonne en moyenne, sur un grand portefeuille.

                ## Deux questions séparées
                - La fréquence : combien de sinistres un profil aura-t-il en moyenne par an ? (ex. 0,07 sinistre par an)
                - Le coût moyen (sévérité) : combien coûte un sinistre quand il survient ? (ex. 1 700 €)

                ## La prime pure
                Prime pure = fréquence × coût moyen. C'est le coût attendu des sinistres : le « juste prix » actuariel, sans frais ni bénéfice.

                ## De la prime pure à la prime commerciale
                L'assuré paie une prime commerciale qui ajoute un chargement de sécurité, les frais de gestion et d'acquisition, et le bénéfice visé. La différence entre la prime commerciale et la prime pure est la marge de prime.

                ## Dans le simulateur
                Onglet Actuariat → Tarification : la fréquence est prédite par un modèle de gradient boosting de Poisson entraîné sur 678 013 contrats réels.
                """);
        question(pricing, "Comment calcule-t-on la prime pure ?",
                List.of("Frais + bénéfice", "Fréquence × coût moyen d'un sinistre",
                        "Prime commerciale − marge", "Coût du sinistre le plus grave"),
                1, "La prime pure est le coût attendu des sinistres : nombre attendu par an multiplié par le coût moyen.");
        question(pricing, "Pourquoi la prime commerciale est-elle supérieure à la prime pure ?",
                List.of("Pour couvrir les frais, le bénéfice et une marge de sécurité",
                        "Parce que les modèles se trompent toujours à la hausse",
                        "Parce que la loi l'impose au double", "Elle ne l'est pas"),
                0, "Prime commerciale = prime pure × (1 + sécurité) ÷ (1 − frais − bénéfice).");

        Lesson explain = lesson(course, 2, 7, "Expliquer une prime avec l'IA explicable", """
                Un tarif doit pouvoir être justifié devant l'assuré et le régulateur. Les valeurs de Shapley répondent à la question : quelle part de la prime vient de chaque caractéristique ?

                ## Des multiplicateurs
                Les modèles de tarification raisonnent en échelle logarithmique : chaque facteur multiplie la prime. Exemple : « le bonus-malus de 100 multiplie la prime par 2,18 ».

                ## Comparer deux assurés
                En faisant la différence des contributions de deux profils, on explique exactement le rapport de leurs primes : un jeune conducteur urbain en malus paie environ 5 fois la prime d'un conducteur expérimenté rural, dont ×2,8 à cause du bonus-malus.

                ## Une limite à connaître
                Une variable peu importante pour le modèle n'est pas forcément sans lien avec le risque : si deux variables mesurent la même chose (zone et densité de population), le modèle n'en utilise qu'une.
                """);
        question(explain, "Que signifie « le bonus-malus multiplie la prime par 2,18 » ?",
                List.of("La prime augmente de 2,18 €", "La prime est 2,18 fois plus élevée à cause de ce facteur",
                        "Le bonus-malus vaut 2,18", "La probabilité de sinistre est de 218 %"),
                1, "Les contributions s'expriment en multiplicateurs : ×2,18 signifie +118 % par rapport à la prime de base.");

        Lesson reserving = lesson(course, 3, 9, "Provisionner les sinistres : Chain-Ladder", """
                Un sinistre survenu cette année est souvent payé sur plusieurs années (expertises, soins, procès). L'assureur doit mettre de côté une provision pour ces paiements futurs.

                ## Le triangle de développement
                On range les paiements cumulés par année de survenance (lignes) et par année de développement (colonnes). Seule la partie haute du triangle est connue : le reste, c'est l'avenir.

                ## La méthode Chain-Ladder
                On calcule, colonne par colonne, de combien les paiements cumulés augmentent en moyenne d'une année à la suivante (facteurs de développement), puis on projette chaque année jusqu'à son coût final.

                ## Mesurer l'incertitude
                Le modèle de Mack donne une erreur standard et un intervalle de confiance. Sur des données réelles américaines (1988-1997), la réalité n'est tombée dans l'intervalle à 95 % que 80 % du temps : un modèle doit toujours être confronté à la réalité.
                """);
        question(reserving, "Que représente la provision d'une année de survenance ?",
                List.of("Les primes encaissées cette année", "Ce qui reste à payer sur les sinistres déjà survenus",
                        "Le bénéfice de l'assureur", "Le coût des sinistres de l'année suivante"),
                1, "Provision = coût final estimé − montant déjà payé.");
        question(reserving, "Sur quelle hypothèse repose Chain-Ladder ?",
                List.of("Les sinistres futurs sont nuls",
                        "Le rythme de paiement observé dans le passé se reproduit",
                        "Tous les sinistres sont payés la première année",
                        "Les primes augmentent chaque année"),
                1, "Si les paiements s'accélèrent ou ralentissent, l'estimation se trompe : c'est ce que montre le backtest.");
        return course;
    }

    private static Lesson lesson(Course course, int order, int minutes, String title, String content) {
        Lesson lesson = new Lesson();
        lesson.setTitle(title);
        lesson.setContent(content.strip());
        lesson.setDurationMinutes(minutes);
        lesson.setDisplayOrder(order);
        course.addLesson(lesson);
        return lesson;
    }

    private static void question(Lesson lesson, String text, List<String> options, int correct, String explanation) {
        QuizQuestion question = new QuizQuestion();
        question.setQuestion(text);
        question.getOptions().addAll(options);
        question.setCorrectIndex(correct);
        question.setExplanation(explanation);
        lesson.addQuestion(question);
    }
}
