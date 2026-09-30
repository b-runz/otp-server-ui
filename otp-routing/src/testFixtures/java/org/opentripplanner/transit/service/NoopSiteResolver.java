package org.opentripplanner.transit.service;

import org.opentripplanner.core.model.id.FeedScopedId;
import org.opentripplanner.transit.SiteResolver;
import org.opentripplanner.transit.model.site.Entrance;
import org.opentripplanner.transit.model.site.RegularStop;
import org.opentripplanner.transit.model.site.StopLocation;

public class NoopSiteResolver implements SiteResolver {

  @Override
  public RegularStop getStop(FeedScopedId id) {
    // Authorized edit: replaced commons-lang3 NotImplementedException (forbidden dependency)
    throw new UnsupportedOperationException();
  }

  @Override
  public StopLocation getStopLocation(FeedScopedId id) {
    // Authorized edit: replaced commons-lang3 NotImplementedException (forbidden dependency)
    throw new UnsupportedOperationException();
  }

  @Override
  public Entrance getEntrance(FeedScopedId id) {
    // Authorized edit: replaced commons-lang3 NotImplementedException (forbidden dependency)
    throw new UnsupportedOperationException();
  }
}
