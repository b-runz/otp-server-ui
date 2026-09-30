// STUB: not upstream OTP code
package org.opentripplanner.datastore.api;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Stub of upstream's {@code DataSource} (a generalized file/blob abstraction used by the OTP
 * graph-building datastore layer: local filesystem, zip archives, cloud storage, etc. — see
 * upstream {@code org.opentripplanner.datastore.OtpDataStore}). This is not enumerated in this
 * task's brief; it surfaced as an additional unresolved import once
 * {@code SerializedGraphObject.java} was copied.
 * <p>
 * {@link org.opentripplanner.routing.graph.SerializedGraphObject} only uses this type as a
 * parameter to {@code load(DataSource)}/{@code save(DataSource)}/
 * {@code verifyTheOutputGraphIsWritableIfDataSourceExist(DataSource)}, all of which are unused
 * public entry points in this module (this project loads graphs via {@code File}, not via the
 * datastore abstraction). Nothing in this module constructs a {@code DataSource}, so only the
 * methods {@code SerializedGraphObject} actually calls are declared here, with no bodies (real
 * upstream defaults for these are trivial one-liners like "return true"/"throw
 * UnsupportedOperationException", not feature bodies), matching only what compiles. A real
 * implementation would need the excluded datastore/graph_builder ingestion slice.
 */
public interface DataSource {
  String name();

  String path();

  boolean exists();

  boolean isWritable();

  InputStream asInputStream();

  OutputStream asOutputStream();

  long size();
}
