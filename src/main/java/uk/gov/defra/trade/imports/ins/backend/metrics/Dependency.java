package uk.gov.defra.trade.imports.ins.backend.metrics;

/**
 * A dependency whose calls are recorded, with the value of its {@code dependency} tag and where its
 * availability comes from.
 */
public enum Dependency {
  MONGODB("mongodb", AvailabilitySource.MONGO_SERVER_CHECKS),
  SQS("sqs", AvailabilitySource.LAST_CALL);

  /** Where a dependency's availability comes from. */
  public enum AvailabilitySource {
    /** The servers the MongoDB driver monitors, as {@link MongoAvailability} reads them. */
    MONGO_SERVER_CHECKS,
    /** The outcome of the dependency's most recent call, as {@link LastCallOutcomes} holds it. */
    LAST_CALL
  }

  private final String tagValue;
  private final AvailabilitySource availabilitySource;

  Dependency(String tagValue, AvailabilitySource availabilitySource) {
    this.tagValue = tagValue;
    this.availabilitySource = availabilitySource;
  }

  public String tagValue() {
    return tagValue;
  }

  public AvailabilitySource availabilitySource() {
    return availabilitySource;
  }
}
