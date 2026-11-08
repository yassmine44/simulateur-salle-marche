package tn.esprit.simulateurbackend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.simulateurbackend.entity.Course;
import tn.esprit.simulateurbackend.entity.CourseLevel;
import tn.esprit.simulateurbackend.entity.Lesson;
import tn.esprit.simulateurbackend.entity.QuizQuestion;
import tn.esprit.simulateurbackend.repository.CourseRepository;

@Component
public class FormationDataInitializer
        implements CommandLineRunner {

    private final CourseRepository courseRepository;


    public FormationDataInitializer(
            CourseRepository courseRepository
    ) {
        this.courseRepository = courseRepository;
    }


    @Override
    @Transactional
    public void run(String... args) {

        if (courseRepository.count() > 0) {
            return;
        }

        courseRepository.save(basicsCourse());
        courseRepository.save(riskCourse());
        courseRepository.save(technicalCourse());
        courseRepository.save(crisesCourse());
    }


    // =========================================================
    // 1. LES BASES DU TRADING
    // =========================================================

    private Course basicsCourse() {

        Course course = course(
                "Les bases du trading",
                "Comprendre comment fonctionne un marché, lire un carnet "
                        + "d'ordres et passer ses premiers ordres en confiance.",
                CourseLevel.BEGINNER,
                "Fondamentaux",
                1
        );

        Lesson l1 = lesson(course, 1, 6,
                "Qu'est-ce qu'un marché financier ?",
                """
                Un marché financier est un lieu où des acheteurs et des vendeurs échangent des actifs : actions, cryptomonnaies, devises, matières premières… Le prix d'un actif n'est pas décidé par une autorité : il naît de la rencontre entre l'offre et la demande.

                ## Les principaux actifs
                - Action : une part du capital d'une entreprise (Apple, Microsoft…).
                - Cryptomonnaie : un actif numérique échangé en continu (Bitcoin, Ethereum…).
                - Forex : l'échange d'une devise contre une autre (EUR/USD…).

                ## Pourquoi les prix bougent
                Si davantage d'acheteurs que de vendeurs sont prêts à payer un prix donné, le prix monte. Dans le cas inverse, il baisse. Les nouvelles économiques, les résultats d'entreprise ou les émotions des investisseurs modifient en permanence ce rapport de forces.

                ## Trader n'est pas parier
                Un trader prépare un scénario, définit à l'avance ce qu'il est prêt à perdre et agit selon des règles. Sur cette plateforme, vous vous entraînez avec un capital virtuel : l'objectif est d'apprendre à décider, pas de deviner.
                """);
        question(l1,
                "Qu'est-ce qui fait varier le prix d'un actif ?",
                new String[]{
                        "Une décision quotidienne de la bourse",
                        "Le rapport entre l'offre et la demande",
                        "Le nombre de traders connectés",
                        "L'heure de la journée uniquement"
                },
                1,
                "Le prix résulte de la confrontation entre acheteurs et vendeurs. Plus la demande dépasse l'offre, plus le prix monte.");
        question(l1,
                "Que représente une action ?",
                new String[]{
                        "Un prêt accordé à une entreprise",
                        "Une devise étrangère",
                        "Une part du capital d'une entreprise",
                        "Un contrat d'assurance"
                },
                2,
                "Acheter une action, c'est devenir propriétaire d'une petite fraction de l'entreprise.");

        Lesson l2 = lesson(course, 2, 8,
                "Le carnet d'ordres : bid, ask et spread",
                """
                Le carnet d'ordres liste, pour un actif, tous les ordres en attente : d'un côté les acheteurs, de l'autre les vendeurs. C'est le cœur d'un marché.

                ## Bid et Ask
                - Bid : le meilleur prix auquel quelqu'un est prêt à acheter.
                - Ask : le meilleur prix auquel quelqu'un est prêt à vendre.

                Le Bid est toujours inférieur au Ask. Si vous voulez acheter immédiatement, vous payez le Ask. Si vous voulez vendre immédiatement, vous recevez le Bid.

                ## Le spread
                Le spread est l'écart entre le Ask et le Bid. Exemple : Bid 99,95 et Ask 100,05 donnent un spread de 0,10. C'est un coût implicite : acheter puis revendre aussitôt fait perdre le spread.

                ## La profondeur
                La profondeur indique les quantités disponibles à chaque niveau de prix. Un carnet profond absorbe de gros ordres sans bouger le prix. Un carnet fin provoque du slippage : votre ordre consomme plusieurs niveaux et s'exécute à un prix moyen moins favorable.
                """);
        question(l2,
                "À quel prix achetez-vous immédiatement ?",
                new String[]{
                        "Au Bid",
                        "Au Ask",
                        "Au milieu du Bid et du Ask",
                        "Au dernier prix de la veille"
                },
                1,
                "Un achat immédiat consomme les vendeurs : il s'exécute donc au meilleur prix de vente, le Ask.");
        question(l2,
                "Avec un Bid à 99,95 et un Ask à 100,05, quel est le spread ?",
                new String[]{"0,05", "0,10", "100,00", "0,20"},
                1,
                "Spread = Ask - Bid = 100,05 - 99,95 = 0,10.");
        question(l2,
                "Qu'est-ce que le slippage ?",
                new String[]{
                        "Une commission fixe du courtier",
                        "L'écart entre le prix attendu et le prix réellement obtenu",
                        "Un type d'ordre conditionnel",
                        "La fermeture du marché"
                },
                1,
                "Quand un ordre traverse plusieurs niveaux du carnet, son prix moyen d'exécution s'éloigne du prix affiché.");

        Lesson l3 = lesson(course, 3, 8,
                "Les types d'ordres",
                """
                Un ordre est une instruction que vous donnez au marché. Le type d'ordre détermine comment et à quel prix elle sera exécutée.

                ## Ordre au marché
                Exécuté immédiatement au meilleur prix disponible. Vous êtes sûr d'être exécuté, mais pas du prix exact.

                ## Ordre à cours limité
                Vous fixez le prix maximum à l'achat ou minimum à la vente. L'ordre attend dans le carnet : vous maîtrisez le prix, mais pas l'exécution, qui peut ne jamais avoir lieu.

                ## Ordres de protection
                - Stop-loss : vend automatiquement si le cours tombe à un niveau fixé, pour limiter une perte.
                - Take-profit : vend automatiquement si le cours atteint un objectif, pour sécuriser un gain.
                - OCO (One-Cancels-the-Other) : associe un take-profit et un stop-loss. Dès que l'un s'exécute, l'autre est annulé.

                ## Durée de validité
                Un ordre peut rester actif jusqu'à annulation (GTC), seulement pour la journée (DAY), être exécuté immédiatement pour ce qui peut l'être (IOC) ou entièrement ou pas du tout (FOK).
                """);
        question(l3,
                "Quel ordre garantit l'exécution immédiate mais pas le prix exact ?",
                new String[]{
                        "L'ordre à cours limité",
                        "L'ordre au marché",
                        "Le take-profit",
                        "L'ordre FOK à prix fixe"
                },
                1,
                "L'ordre au marché prend le meilleur prix disponible tout de suite, quel qu'il soit.");
        question(l3,
                "Que fait un ordre OCO ?",
                new String[]{
                        "Il double la quantité de l'ordre",
                        "Il annule l'ordre au bout d'une heure",
                        "Il lie un take-profit et un stop-loss : l'exécution de l'un annule l'autre",
                        "Il exécute deux achats successifs"
                },
                2,
                "OCO signifie One-Cancels-the-Other : dès qu'un des deux ordres s'exécute, l'autre disparaît.");

        return course;
    }


    // =========================================================
    // 2. GÉRER SON RISQUE
    // =========================================================

    private Course riskCourse() {

        Course course = course(
                "Gérer son risque",
                "La compétence qui distingue les traders qui durent des autres : "
                        + "limiter les pertes, dimensionner ses positions, diversifier.",
                CourseLevel.INTERMEDIATE,
                "Risque",
                2
        );

        Lesson l1 = lesson(course, 1, 6,
                "Pourquoi le risque passe avant le gain",
                """
                Les débutants se demandent combien ils peuvent gagner. Les professionnels se demandent d'abord combien ils peuvent perdre.

                ## L'asymétrie des pertes
                Une perte demande un gain plus grand pour être compensée :
                - Perte de 10 % : il faut gagner 11,1 % pour revenir à l'équilibre.
                - Perte de 20 % : il faut gagner 25 %.
                - Perte de 50 % : il faut gagner 100 %.

                Plus la perte est profonde, plus le chemin du retour est long. Éviter les grosses pertes est donc plus important que chercher les gros gains.

                ## Le capital est votre outil de travail
                Un trader qui perd tout son capital ne peut plus trader. La première règle est de rester en jeu : survivre assez longtemps pour que votre méthode produise des résultats.
                """);
        question(l1,
                "Après une perte de 50 %, quel gain faut-il pour revenir au capital initial ?",
                new String[]{"50 %", "75 %", "100 %", "150 %"},
                2,
                "Avec 100 réduit à 50, il faut doubler (+100 %) pour revenir à 100.");
        question(l1,
                "Quelle est la première priorité d'un trader professionnel ?",
                new String[]{
                        "Maximiser chaque gain",
                        "Préserver son capital",
                        "Trader le plus souvent possible",
                        "Suivre les rumeurs du marché"
                },
                1,
                "Sans capital, on ne peut plus trader : protéger le capital vient avant tout.");

        Lesson l2 = lesson(course, 2, 8,
                "Stop-loss, take-profit et ratio risque/rendement",
                """
                Avant d'entrer en position, définissez trois niveaux : votre prix d'entrée, votre stop-loss (où vous admettez que vous aviez tort) et votre take-profit (votre objectif).

                ## Le ratio risque/rendement
                C'est le gain visé divisé par la perte acceptée. Exemple : entrée à 100, stop-loss à 95 (risque 5), take-profit à 110 (gain 10). Le ratio est de 2 pour 1.

                ## Pourquoi c'est puissant
                Avec un ratio de 2 pour 1, vous pouvez vous tromper plus d'une fois sur deux et rester gagnant. Sur 10 trades avec 4 gagnants à +10 et 6 perdants à -5 : 40 - 30 = +10 au total.

                ## Bonnes pratiques
                - Fixez le stop-loss avant d'entrer, jamais après.
                - Ne le déplacez pas pour « laisser une chance au prix ».
                - Utilisez un ordre OCO pour poser stop-loss et take-profit en une seule fois.
                """);
        question(l2,
                "Entrée à 100, stop-loss à 95, take-profit à 110 : quel est le ratio risque/rendement ?",
                new String[]{"1 pour 1", "2 pour 1", "3 pour 1", "0,5 pour 1"},
                1,
                "Gain visé : 10. Perte acceptée : 5. 10 / 5 = 2, soit 2 pour 1.");
        question(l2,
                "Quand faut-il fixer son stop-loss ?",
                new String[]{
                        "Après être entré, quand le prix baisse",
                        "Avant d'entrer en position",
                        "Seulement si le trade est perdant",
                        "Jamais, il faut attendre le rebond"
                },
                1,
                "Fixé à l'avance, le stop-loss évite de décider sous le coup de l'émotion.");

        Lesson l3 = lesson(course, 3, 9,
                "Dimensionner sa position et diversifier",
                """
                Le dimensionnement répond à la question : combien d'unités acheter ?

                ## La règle du pourcentage fixe
                Beaucoup de traders risquent 1 à 2 % de leur capital par trade. Avec 100 000 $ et un risque de 1 %, la perte maximale acceptée est de 1 000 $.

                ## La formule
                Quantité = montant risqué / (prix d'entrée - prix du stop-loss)

                Exemple : entrée à 100 $, stop-loss à 95 $. Le risque par unité est de 5 $. Quantité = 1 000 / 5 = 200 unités, soit une position de 20 000 $.

                ## Diversifier
                Mettre tout son capital sur un seul actif expose à un seul événement. Répartir sur des actifs peu corrélés (actions, crypto, devises) réduit l'impact d'un mauvais trade.

                Attention : en cas de crise, beaucoup d'actifs chutent ensemble. La diversification réduit le risque, elle ne le supprime pas.
                """);
        question(l3,
                "Capital 50 000 $, risque 2 % par trade, entrée à 100 $, stop-loss à 90 $. Quelle quantité acheter ?",
                new String[]{"50 unités", "100 unités", "500 unités", "1 000 unités"},
                1,
                "Risque : 2 % de 50 000 = 1 000 $. Risque par unité : 10 $. 1 000 / 10 = 100 unités.");
        question(l3,
                "Que permet la diversification ?",
                new String[]{
                        "Supprimer totalement le risque",
                        "Garantir des gains",
                        "Réduire l'impact d'un mauvais trade ou d'un seul actif",
                        "Éviter d'utiliser un stop-loss"
                },
                2,
                "Elle limite la dépendance à un seul actif, mais ne protège pas d'une chute généralisée du marché.");

        return course;
    }


    // =========================================================
    // 3. ANALYSE TECHNIQUE
    // =========================================================

    private Course technicalCourse() {

        Course course = course(
                "Analyse technique",
                "Lire un graphique en chandeliers, repérer les tendances, "
                        + "les supports et résistances, et utiliser les moyennes mobiles.",
                CourseLevel.INTERMEDIATE,
                "Analyse",
                3
        );

        Lesson l1 = lesson(course, 1, 7,
                "Lire un graphique en chandeliers",
                """
                Un chandelier résume l'activité d'une période (une minute, un jour, une semaine) en quatre prix : ouverture, plus haut, plus bas et clôture, d'où le format OHLC (Open, High, Low, Close).

                ## Anatomie d'un chandelier
                - Le corps relie l'ouverture à la clôture.
                - Les mèches (ou ombres) montent jusqu'au plus haut et descendent jusqu'au plus bas.
                - Corps vert : la clôture est au-dessus de l'ouverture (hausse).
                - Corps rouge : la clôture est sous l'ouverture (baisse).

                ## Ce que racontent les formes
                - Un long corps montre une conviction forte des acheteurs ou des vendeurs.
                - Une longue mèche basse indique que les vendeurs ont poussé le prix vers le bas, mais que les acheteurs l'ont repoussé.
                - Un chandelier au corps minuscule (doji) traduit l'indécision.

                Un chandelier isolé ne suffit jamais : lisez-le dans son contexte, avec la tendance et les niveaux clés.
                """);
        question(l1,
                "Que signifie OHLC ?",
                new String[]{
                        "Offer, High, Low, Cost",
                        "Open, High, Low, Close",
                        "Order, Hold, Limit, Cancel",
                        "Open, Hedge, Long, Credit"
                },
                1,
                "OHLC : Open (ouverture), High (plus haut), Low (plus bas), Close (clôture).");
        question(l1,
                "Un chandelier au corps vert signifie que...",
                new String[]{
                        "la clôture est au-dessus de l'ouverture",
                        "la clôture est sous l'ouverture",
                        "le volume est élevé",
                        "le marché est fermé"
                },
                0,
                "Un corps vert (haussier) indique que le prix a clôturé plus haut qu'à l'ouverture.");

        Lesson l2 = lesson(course, 2, 8,
                "Supports, résistances et tendances",
                """
                ## La tendance
                - Tendance haussière : une suite de sommets et de creux de plus en plus hauts.
                - Tendance baissière : des sommets et des creux de plus en plus bas.
                - Range : le prix oscille entre deux niveaux sans direction claire.

                Une idée simple : la tendance est votre alliée. Aller dans son sens est généralement plus facile que de la combattre.

                ## Supports et résistances
                - Support : un niveau où le prix a déjà rebondi, car les acheteurs y sont nombreux.
                - Résistance : un niveau où le prix a déjà buté, car les vendeurs y sont nombreux.

                Plus un niveau a été testé, plus il est suivi par les autres traders.

                ## Cassure
                Quand le prix franchit durablement une résistance, celle-ci devient souvent un support, et inversement. Méfiez-vous des fausses cassures : attendez une clôture au-delà du niveau.
                """);
        question(l2,
                "Quelle suite décrit une tendance haussière ?",
                new String[]{
                        "Sommets et creux de plus en plus bas",
                        "Sommets et creux de plus en plus hauts",
                        "Prix qui oscille entre deux niveaux fixes",
                        "Prix qui ne bouge pas"
                },
                1,
                "Une tendance haussière construit des sommets et des creux successivement plus hauts.");
        question(l2,
                "Qu'est-ce qu'un support ?",
                new String[]{
                        "Un niveau où le prix a déjà rebondi à la hausse",
                        "Un niveau où le prix a déjà buté à la baisse",
                        "Un type d'ordre",
                        "Le prix de clôture de la veille"
                },
                0,
                "Un support est un plancher : les acheteurs y sont suffisamment nombreux pour stopper la baisse.");

        Lesson l3 = lesson(course, 3, 8,
                "Moyennes mobiles et volume",
                """
                ## La moyenne mobile
                La moyenne mobile (MM) lisse le prix en calculant la moyenne des N dernières clôtures. Par exemple, une MM20 est la moyenne des 20 dernières clôtures.

                - Prix au-dessus de la MM : biais haussier.
                - Prix sous la MM : biais baissier.
                - Une MM courte (20) réagit vite, une MM longue (50 ou 200) donne la tendance de fond.

                ## Croisement de moyennes
                Quand la MM courte passe au-dessus de la MM longue, on parle de croisement haussier. L'inverse est un croisement baissier. Ces signaux arrivent avec retard : une moyenne décrit le passé.

                ## Le volume
                Le volume est la quantité échangée. Un mouvement de prix accompagné d'un volume élevé est plus fiable qu'un mouvement sur faible volume.

                ## Aucun indicateur n'est infaillible
                Combinez plusieurs éléments (tendance, niveaux, volume) et gardez toujours un stop-loss.
                """);
        question(l3,
                "Que calcule une moyenne mobile sur 20 périodes ?",
                new String[]{
                        "Le plus haut des 20 dernières périodes",
                        "La moyenne des 20 dernières clôtures",
                        "Le volume total des 20 derniers jours",
                        "L'écart entre l'ouverture et la clôture"
                },
                1,
                "La MM20 fait la moyenne des 20 dernières clôtures et lisse ainsi le bruit du prix.");
        question(l3,
                "Pourquoi un volume élevé renforce-t-il un mouvement de prix ?",
                new String[]{
                        "Parce qu'il montre une large participation des acteurs",
                        "Parce qu'il garantit que le prix continuera",
                        "Parce qu'il remplace le stop-loss",
                        "Parce qu'il réduit le spread à zéro"
                },
                0,
                "Beaucoup d'échanges signifient que le mouvement est soutenu par de nombreux participants, sans rien garantir pour la suite.");

        return course;
    }


    // =========================================================
    // 4. CRISES HISTORIQUES
    // =========================================================

    private Course crisesCourse() {

        Course course = course(
                "Apprendre des crises historiques",
                "Trois chocs majeurs, trois leçons : préparez-vous aux défis "
                        + "de la section Gamification.",
                CourseLevel.ADVANCED,
                "Histoire des marchés",
                4
        );

        Lesson l1 = lesson(course, 1, 8,
                "2008 et 2020 : deux krachs, deux dynamiques",
                """
                ## 2008 : la crise des subprimes
                Des crédits immobiliers accordés à des emprunteurs fragiles avaient été transformés en produits financiers complexes. Quand les défauts se sont multipliés, la confiance s'est effondrée, culminant avec la faillite de Lehman Brothers en septembre 2008. Le S&P 500 a perdu environ 57 % entre son sommet d'octobre 2007 et son plus bas de mars 2009.

                Leçon : une baisse lente et profonde peut durer des mois. Les rebonds sont violents mais trompeurs (bear market rallies).

                ## 2020 : le krach COVID
                En février-mars 2020, la pandémie a provoqué une chute de plus de 30 % du S&P 500 en environ cinq semaines, l'une des plus rapides de l'histoire. Les banques centrales et les États ont réagi massivement, et le marché a rebondi dès fin mars.

                Leçon : une baisse brutale peut être suivie d'un rebond tout aussi rapide. Les stop-loss serrés ont souvent été déclenchés au pire moment.

                ## À retenir
                Deux krachs, deux rythmes : lent et profond en 2008, éclair en 2020. Votre gestion du risque doit s'adapter au contexte.
                """);
        question(l1,
                "Quel événement a marqué le point culminant de la crise de 2008 ?",
                new String[]{
                        "La faillite de Lehman Brothers",
                        "L'introduction de l'euro",
                        "Le début de la pandémie",
                        "L'invasion de l'Ukraine"
                },
                0,
                "La faillite de Lehman Brothers, en septembre 2008, a déclenché une panique mondiale.");
        question(l1,
                "Quelle est la principale différence entre les krachs de 2008 et de 2020 ?",
                new String[]{
                        "2008 fut lent et profond, 2020 fut brutal puis suivi d'un rebond rapide",
                        "Il n'y a eu aucune baisse en 2020",
                        "2020 a duré plus longtemps que 2008",
                        "Les deux ont eu exactement le même rythme"
                },
                0,
                "2008 s'est étalé sur plus d'un an, tandis que 2020 a chuté en quelques semaines avant de rebondir vite.");

        Lesson l2 = lesson(course, 2, 7,
                "2022 : choc géopolitique et inflation",
                """
                ## Le contexte
                Début 2022, l'inflation était déjà élevée. L'invasion de l'Ukraine par la Russie en février a fait grimper les prix de l'énergie et des matières premières. Pour freiner l'inflation, la Réserve fédérale américaine a relevé ses taux d'intérêt de façon rapide.

                ## L'effet sur les marchés
                Des taux plus élevés pèsent sur les valorisations, surtout celles des entreprises technologiques. Le S&P 500 a perdu environ 25 % entre son sommet de janvier 2022 et son plus bas d'octobre 2022. Les obligations ont aussi reculé : la diversification classique a moins bien protégé qu'à l'habitude.

                ## Leçons
                - Les taux d'intérêt sont un moteur majeur des marchés.
                - Un marché peut baisser pendant des mois sans panique visible.
                - Quand plusieurs classes d'actifs chutent ensemble, la prudence sur la taille des positions devient essentielle.
                """);
        question(l2,
                "Quel levier la Réserve fédérale a-t-elle utilisé pour freiner l'inflation en 2022 ?",
                new String[]{
                        "La hausse de ses taux d'intérêt",
                        "La baisse de ses taux d'intérêt",
                        "La fermeture des marchés",
                        "L'interdiction des ordres limites"
                },
                0,
                "La Fed a relevé ses taux de manière rapide, ce qui a pesé sur les valorisations.");
        question(l2,
                "Que montre le marché de 2022 sur la diversification ?",
                new String[]{
                        "Elle supprime toute perte",
                        "Elle peut protéger moins bien quand plusieurs classes d'actifs baissent ensemble",
                        "Elle est inutile en toutes circonstances",
                        "Elle n'est utile que pour les cryptomonnaies"
                },
                1,
                "En 2022 actions et obligations ont reculé ensemble : diversifier réduit le risque mais ne l'élimine pas.");

        return course;
    }


    // =========================================================
    // OUTILS DE CONSTRUCTION
    // =========================================================

    private Course course(
            String title,
            String description,
            CourseLevel level,
            String category,
            int order
    ) {

        Course course = new Course();

        course.setTitle(title);
        course.setDescription(description);
        course.setLevel(level);
        course.setCategory(category);
        course.setDisplayOrder(order);
        course.setPublished(true);

        return course;
    }


    private Lesson lesson(
            Course course,
            int order,
            int minutes,
            String title,
            String content
    ) {

        Lesson lesson = new Lesson();

        lesson.setTitle(title);
        lesson.setContent(content.strip());
        lesson.setDurationMinutes(minutes);
        lesson.setDisplayOrder(order);

        course.addLesson(lesson);

        return lesson;
    }


    private void question(
            Lesson lesson,
            String text,
            String[] options,
            int correctIndex,
            String explanation
    ) {

        QuizQuestion question = new QuizQuestion();

        question.setQuestion(text);
        question.getOptions().addAll(java.util.List.of(options));
        question.setCorrectIndex(correctIndex);
        question.setExplanation(explanation);

        lesson.addQuestion(question);
    }
}