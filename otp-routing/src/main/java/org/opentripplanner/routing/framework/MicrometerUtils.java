package org.opentripplanner.routing.framework;

// MONITORING REMOVED: Micrometer is not a dependency of this module.
// import io.micrometer.core.instrument.Tag;
import java.util.Collection;
import java.util.List;
import org.opentripplanner.routing.api.request.RoutingTag;

public class MicrometerUtils {

  // MONITORING REMOVED:
  // public static List<Tag> mapTimingTags(Collection<RoutingTag> tags) {
  //   return tags
  //     .stream()
  //     .filter(RoutingTag::includeInMicrometerTiming)
  //     .map(t -> Tag.of(t.getCategory().name(), t.getTag()))
  //     .toList();
  // }
  // Tag is not on the classpath; kept as a no-op shell returning an empty list.
  public static List<Object> mapTimingTags(Collection<RoutingTag> tags) {
    return List.of();
  }
}
