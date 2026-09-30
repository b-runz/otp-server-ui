// STUB: not upstream OTP code
package org.opentripplanner.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.opentripplanner.standalone.config.BuildConfig;

/**
 * Stub: the real upstream {@code BuildConfigSerializer} serializes {@link BuildConfig}'s
 * underlying JSON config tree by round-tripping it through
 * {@code org.opentripplanner.standalone.config.framework.file.ConfigFileLoader}, because the real
 * {@code BuildConfig} has no default constructor and is built from a parsed JSON node. That whole
 * config-parsing framework is out of scope for this project (see {@code standalone.config}
 * stubs). Since this module's own {@link BuildConfig} stub is an empty class with a public no-arg
 * constructor and no state, this stub serializer writes nothing and reconstructs a fresh instance
 * on read.
 */
public class BuildConfigSerializer extends Serializer<BuildConfig> {

  @Override
  public void write(Kryo kryo, Output output, BuildConfig object) {}

  @Override
  public BuildConfig read(Kryo kryo, Input input, Class<? extends BuildConfig> type) {
    return new BuildConfig();
  }
}
