package tn.esprit.simulateurbackend.entity;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Journal des actions d'administration du module d'actuariat (§4 traçabilité,
 * §5 administration) : qui a changé quoi, quand, avec l'état avant/après.
 *
 * <p>Le type d'action est stocké en texte via un {@link AttributeConverter}
 * et non avec {@code @Enumerated} : Hibernate 6 génère sinon une contrainte
 * CHECK figée que {@code ddl-auto=update} ne met jamais à jour, si bien que
 * tout nouveau type d'action ferait échouer l'insertion.
 */
@Entity
@Table(
        name = "actuarial_admin_actions",
        indexes = @Index(name = "idx_actuarial_admin_action_created", columnList = "created_at")
)
public class ActuarialAdminAction {

    public enum Type {
        ASSUMPTIONS_UPDATED,
        CURRENCY_UPDATED,
        MODELS_RELOADED
    }

    @Converter
    public static class TypeConverter implements AttributeConverter<Type, String> {
        @Override
        public String convertToDatabaseColumn(Type type) {
            return type == null ? null : type.name();
        }

        @Override
        public Type convertToEntityAttribute(String value) {
            return value == null ? null : Type.valueOf(value);
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = TypeConverter.class)
    @Column(name = "action_type", nullable = false, length = 40)
    private Type type;

    @Column(name = "actor_email", nullable = false, length = 255)
    private String actorEmail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Détails lisibles (ex. hypothèses avant → après), au format JSON. */
    @Column(columnDefinition = "TEXT")
    private String details;

    protected ActuarialAdminAction() {
        // Constructeur requis par JPA.
    }

    public static ActuarialAdminAction of(Type type, String actorEmail, String details, Instant createdAt) {
        ActuarialAdminAction action = new ActuarialAdminAction();
        action.type = type;
        action.actorEmail = actorEmail;
        action.details = details;
        action.createdAt = createdAt;
        return action;
    }

    public Long getId() { return id; }
    public Type getType() { return type; }
    public String getActorEmail() { return actorEmail; }
    public Instant getCreatedAt() { return createdAt; }
    public String getDetails() { return details; }
}
