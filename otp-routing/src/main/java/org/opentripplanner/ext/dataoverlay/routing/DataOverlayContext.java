// STUB: not upstream OTP code
package org.opentripplanner.ext.dataoverlay.routing;

import java.util.List;
import javax.annotation.Nullable;
import org.opentripplanner.ext.dataoverlay.api.DataOverlayParameters;
import org.opentripplanner.ext.dataoverlay.configuration.DataOverlayParameterBindings;
import org.opentripplanner.street.model.edge.ExtensionRequestContext;

/**
 * Stub: the real data-overlay sandbox feature (org.opentripplanner.ext.dataoverlay.*) is out of
 * scope for the embedded otp-routing slice. {@code DirectStreetRouter}, {@code DirectFlexRouter}
 * and {@code AccessEgressFetcher} call {@link #listExtensionRequestContexts} to build the
 * A*-search extension contexts; an empty list means "no data-overlay penalty/threshold applied",
 * matching what upstream returns when the feature is off.
 */
public class DataOverlayContext {

  public static List<ExtensionRequestContext> listExtensionRequestContexts(
    @Nullable DataOverlayParameters dataOverlayRequest,
    @Nullable DataOverlayParameterBindings dataOverlayParameterBindings
  ) {
    return List.of();
  }
}
