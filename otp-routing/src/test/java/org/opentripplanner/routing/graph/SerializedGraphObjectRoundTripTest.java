package org.opentripplanner.routing.graph;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class SerializedGraphObjectRoundTripTest {

  @Test
  @Disabled("enabled by Task 4 once tiny-fixture-graph.obj exists")
  void loadsARealSerializedGraph() throws Exception {
    // NOTE: the task brief's sample test called SerializedGraphObject.load(InputStream), but the
    // real copied SerializedGraphObject.java has no public single-argument InputStream overload
    // -- only load(File) and load(DataSource) are public (the InputStream+description overload is
    // private). Using the real public File-based API here per the brief's own instruction to
    // "use its real API ... don't guess a different shape".
    SerializedGraphObject loaded = SerializedGraphObject.load(
      new File("src/test/resources/tiny-fixture-graph.obj")
    );
    assertThat(loaded.graph).isNotNull();
  }
}
