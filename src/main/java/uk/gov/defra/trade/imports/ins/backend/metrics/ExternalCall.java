package uk.gov.defra.trade.imports.ins.backend.metrics;

import java.util.Objects;
import java.util.Set;

/**
 * One call to a system the service depends on.
 *
 * @param dependency the system called
 * @param operation the operation on that system, for example {@code get-object}
 */
public record ExternalCall(Dependency dependency, String operation) {

  public ExternalCall {
    Objects.requireNonNull(dependency, "dependency must not be null");
    Objects.requireNonNull(operation, "operation must not be null");
  }

  /**
   * A call whose operation is bounded to the known operations.
   *
   * @param dependency the system called
   * @param operation the operation on that system, or null when the call does not name one
   * @param knownOperations the operations reported by name
   * @return the call, with {@code operation} when it is known, else {@code other}
   */
  public static ExternalCall boundedTo(
      Dependency dependency, String operation, Set<String> knownOperations) {
    boolean known = operation != null && knownOperations.contains(operation);
    return new ExternalCall(dependency, known ? operation : "other");
  }
}
