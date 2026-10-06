package org.opentripplanner.gtfs.mapping;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.valid.IsValidOp;
import org.onebusaway.gtfs.model.Location;
import org.opentripplanner.core.model.i18n.NonLocalizedString;
import org.opentripplanner.graph_builder.issue.api.DataImportIssueStore;
import org.opentripplanner.graph_builder.issue.api.Issue;
import org.opentripplanner.street.geometry.GeometryUtils;
import org.opentripplanner.street.geometry.UnsupportedGeometryException;
import org.opentripplanner.transit.model.site.AreaStop;
import org.opentripplanner.transit.service.SiteRepositoryBuilder;
import org.opentripplanner.utils.collection.MapUtils;

/** Responsible for mapping GTFS Location into the OTP model. */
class LocationMapper {

  private final IdFactory idFactory;
  private final Map<Location, AreaStop> mappedLocations = new HashMap<>();
  private final SiteRepositoryBuilder siteRepositoryBuilder;
  private final DataImportIssueStore issueStore;

  public LocationMapper(
    IdFactory idFactory,
    SiteRepositoryBuilder siteRepositoryBuilder,
    DataImportIssueStore issueStore
  ) {
    this.idFactory = idFactory;
    this.siteRepositoryBuilder = siteRepositoryBuilder;
    this.issueStore = issueStore;
  }

  Collection<AreaStop> map(Collection<Location> allLocations) {
    return MapUtils.mapToList(allLocations, this::map);
  }

  /** Map from GTFS to OTP model, {@code null} safe. */
  AreaStop map(Location orginal) {
    return orginal == null ? null : mappedLocations.computeIfAbsent(orginal, this::doMap);
  }

  private AreaStop doMap(Location gtfsLocation) {
    var name = NonLocalizedString.ofNullable(gtfsLocation.getName());
    // NOTE (throwaway graph-builder, Task 4): the real upstream GeometryUtils declares
    // `convertGeoJsonToJtsGeometry(...) throws UnsupportedGeometryException`; the copy already
    // vendored into otp-street (Task 2) doesn't, so the try/catch around it was dropped here (it
    // would otherwise be unreachable-catch-block dead code). See docs/graph-build.md.
    var id = idFactory.createId(gtfsLocation.getId(), "location");
    Geometry geometry = GeometryUtils.convertGeoJsonToJtsGeometry(gtfsLocation.getGeometry());
    var isValidOp = new IsValidOp(geometry);
    if (!isValidOp.isValid()) {
      var error = isValidOp.getValidationError();
      issueStore.add(
        Issue.issue(
          "InvalidFlexAreaGeometry",
          "GTFS flex location %s has an invalid geometry: %s at (lat: %s, lon: %s)",
          id,
          error.getMessage(),
          error.getCoordinate().y,
          error.getCoordinate().x
        )
      );
    }
    return siteRepositoryBuilder
      .areaStop(id)
      .withName(name)
      .withUrl(NonLocalizedString.ofNullable(gtfsLocation.getUrl()))
      .withDescription(NonLocalizedString.ofNullable(gtfsLocation.getDescription()))
      .withZoneId(gtfsLocation.getZoneId())
      .withGeometry(geometry)
      .build();
  }
}
