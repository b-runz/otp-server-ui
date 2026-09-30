// STUB: not upstream OTP code
package org.opentripplanner.routing.fares;

/**
 * Trivial concrete implementation of the {@link FareServiceFactory} marker stub, added by
 * otp-server-ui's Task 4 (throwaway graph-builder) so a real, non-null, classpath-resolvable
 * instance can be embedded in a saved {@code graph.obj} -- {@code FareServiceFactory} is a
 * non-{@code @Nullable} constructor argument of
 * {@link org.opentripplanner.routing.graph.SerializedGraphObject}, and any concrete class Kryo
 * serializes into the graph file must exist on this module's own classpath when the file is later
 * deserialized (a throwaway-project-only class would not be). Since the stub interface declares no
 * methods, there is nothing to implement.
 */
public class NoopFareServiceFactory implements FareServiceFactory {}
