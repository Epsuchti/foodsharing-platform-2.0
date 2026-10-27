package network.foodsharing.common.error;

/** A field-level validation problem; mapped to the generated {@code FieldError} model. */
public record ApiFieldError(String field, String code, String message) {
}
