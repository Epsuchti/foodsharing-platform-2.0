package network.foodsharing.history;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks an entity field whose changes are recorded automatically as {@code <Module>Action.FIELD_CHANGED}
 * (field, old value, new value). The entity must implement {@link HistorySubject}. With {@code redact = true} the
 * values are stored as {@code ***}.
 */
@Target(ElementType.FIELD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface Tracked {

    boolean redact() default false;
}
