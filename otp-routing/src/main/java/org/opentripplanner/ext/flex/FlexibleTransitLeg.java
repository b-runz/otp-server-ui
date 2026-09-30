// STUB: not upstream OTP code
package org.opentripplanner.ext.flex;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.locationtech.jts.geom.LineString;
import org.opentripplanner.ext.flex.edgetype.FlexTripEdge;
import org.opentripplanner.model.fare.FareOffer;
import org.opentripplanner.model.plan.Emission;
import org.opentripplanner.model.plan.Place;
import org.opentripplanner.model.plan.TransitLeg;
import org.opentripplanner.model.plan.leg.LegCallTime;
import org.opentripplanner.routing.alertpatch.TransitAlert;
import org.opentripplanner.transit.model.basic.TransitMode;
import org.opentripplanner.transit.model.site.StopLocation;

/**
 * Stub: the real flex-transit leg (org.opentripplanner.ext.flex.*) is out of scope for the
 * embedded otp-routing slice. This preserves only what {@code Itinerary} (an {@code instanceof}
 * check) and {@code StreetPathToLegsMapper} (the {@code of()} builder chain) need to compile.
 * Since flex graph-building is excluded, no {@code FlexTripEdge} is ever produced, so
 * {@code generateFlexLeg} is unreachable and a real {@code FlexibleTransitLeg} is never built —
 * accessors beyond what the builder stores throw.
 */
public class FlexibleTransitLeg implements TransitLeg {

  private final StopLocation fromStop;
  private final StopLocation toStop;
  private final ZonedDateTime startTime;
  private final ZonedDateTime endTime;
  private final int generalizedCost;

  private FlexibleTransitLeg(Builder builder) {
    this.fromStop = builder.fromStop;
    this.toStop = builder.toStop;
    this.startTime = builder.startTime;
    this.endTime = builder.endTime;
    this.generalizedCost = builder.generalizedCost;
  }

  public static Builder of() {
    return new Builder();
  }

  @Override
  public TransitMode mode() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public LegCallTime start() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public LegCallTime end() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public ZonedDateTime startTime() {
    return startTime;
  }

  @Override
  public ZonedDateTime endTime() {
    return endTime;
  }

  @Override
  public double distanceMeters() {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public Place from() {
    return Place.forStop(fromStop);
  }

  @Override
  public Place to() {
    return Place.forStop(toStop);
  }

  @Nullable
  @Override
  public LineString legGeometry() {
    return null;
  }

  @Override
  public Set<TransitAlert> listTransitAlerts() {
    return Set.of();
  }

  @Override
  public TransitLeg decorateWithAlerts(Set<TransitAlert> alerts) {
    throw new UnsupportedOperationException("stub");
  }

  @Nullable
  @Override
  public Emission emissionPerPerson() {
    return null;
  }

  @Override
  public org.opentripplanner.model.plan.Leg withEmissionPerPerson(Emission emissionPerPerson) {
    throw new UnsupportedOperationException("stub");
  }

  @Override
  public int generalizedCost() {
    return generalizedCost;
  }

  @Override
  public List<FareOffer> fareOffers() {
    return List.of();
  }

  public static class Builder {

    private StopLocation fromStop;
    private StopLocation toStop;
    private ZonedDateTime startTime;
    private ZonedDateTime endTime;
    private int generalizedCost;

    public Builder withFlexTripEdge(FlexTripEdge flexTripEdge) {
      return this;
    }

    public Builder withFromStop(StopLocation fromStop) {
      this.fromStop = fromStop;
      return this;
    }

    public Builder withToStop(StopLocation toStop) {
      this.toStop = toStop;
      return this;
    }

    public Builder withStartTime(ZonedDateTime startTime) {
      this.startTime = startTime;
      return this;
    }

    public Builder withEndTime(ZonedDateTime endTime) {
      this.endTime = endTime;
      return this;
    }

    public Builder withGeneralizedCost(int generalizedCost) {
      this.generalizedCost = generalizedCost;
      return this;
    }

    public FlexibleTransitLeg build() {
      return new FlexibleTransitLeg(this);
    }
  }
}
