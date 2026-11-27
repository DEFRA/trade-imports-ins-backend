package uk.gov.defra.trade.imports.ins.backend.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * The outcome of the most recent call to each dependency whose availability source is {@link
 * Dependency.AvailabilitySource#LAST_CALL}. Calls to any other dependency are not held. The outcome
 * has no time attached: one failed call, on any of the dependency's operations, marks the whole
 * dependency as unavailable until its next call succeeds.
 */
@Component
public class LastCallOutcomes {

  private final Map<Dependency, Availability> availabilityByDependency = new ConcurrentHashMap<>();

  public void recordSuccess(Dependency dependency) {
    storeIfReadFromLastCall(dependency, Availability.AVAILABLE);
  }

  public void recordFailure(Dependency dependency) {
    storeIfReadFromLastCall(dependency, Availability.UNAVAILABLE);
  }

  private void storeIfReadFromLastCall(Dependency dependency, Availability availability) {
    if (dependency.availabilitySource() == Dependency.AvailabilitySource.LAST_CALL) {
      availabilityByDependency.put(dependency, availability);
    }
  }

  /**
   * The dependency's availability as its most recent call left it.
   *
   * @param dependency the system called
   * @return available after a success, unavailable after a failure, and unknown when the
   *     dependency has not been called since start-up or its availability comes from elsewhere
   */
  public Availability lastCallAvailability(Dependency dependency) {
    return availabilityByDependency.getOrDefault(dependency, Availability.UNKNOWN);
  }
}
