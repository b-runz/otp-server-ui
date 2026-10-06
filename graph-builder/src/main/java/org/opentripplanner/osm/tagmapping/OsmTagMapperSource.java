package org.opentripplanner.osm.tagmapping;

/**
 * This is the list of {@link OsmTagMapper} sources. The enum provide a mapping between the enum
 * name and the actual implementation.
 */
// NOTE (throwaway graph-builder, Task 4): the real upstream enum has ~10 more country-specific
// entries (NORWAY, UK, FINLAND, ...); all pruned here since this driver only ever builds a Danish
// extract. See docs/graph-build.md.
public enum OsmTagMapperSource {
  DEFAULT;

  public OsmTagMapper getInstance() {
    return switch (this) {
      case DEFAULT -> new OsmTagMapper();
    };
  }
}
