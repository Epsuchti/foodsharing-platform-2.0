package network.foodsharing.history;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Exempts a mutating ({@code @Transactional}) service method from ArchUnit rule 4 ("mutating services record
 * history"). The reason is mandatory and should say where the change is recorded instead (or why it is not).
 */
@Target(ElementType.METHOD)
@Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface NoHistory {

    String reason();
}
