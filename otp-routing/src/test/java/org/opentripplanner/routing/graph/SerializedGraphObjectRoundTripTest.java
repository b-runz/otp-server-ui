package org.opentripplanner.routing.graph;

import static com.google.common.truth.Truth.assertThat;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opentripplanner.transit.model.basic.SubMode;

class SerializedGraphObjectRoundTripTest {

  // SubMode keeps a process-global static cache (ALL / COUNTER) that SerializedGraphObject.load()
  // permanently mutates via SubMode.deserializeSubModeCache(...) (see SubMode.java) -- real
  // submodes from this test's real deserialized graph get added, and since JUnit runs all test
  // classes in one JVM, that pollution would otherwise leak into any other test that also uses
  // SubMode (e.g. SubModeTest), causing spurious index collisions there. Snapshot + restore this
  // static state around the test so this test's use of a REAL graph stays isolated, the same way
  // it would be if this graph had never been loaded.
  private Map<String, SubMode> subModeAllSnapshot;
  private int subModeCounterSnapshot;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void snapshotSubModeCache() throws Exception {
    Field allField = SubMode.class.getDeclaredField("ALL");
    allField.setAccessible(true);
    subModeAllSnapshot = Map.copyOf((Map<String, SubMode>) allField.get(null));

    Field counterField = SubMode.class.getDeclaredField("COUNTER");
    counterField.setAccessible(true);
    subModeCounterSnapshot = ((AtomicInteger) counterField.get(null)).get();
  }

  @AfterEach
  @SuppressWarnings("unchecked")
  void restoreSubModeCache() throws Exception {
    Field allField = SubMode.class.getDeclaredField("ALL");
    allField.setAccessible(true);
    Map<String, SubMode> all = (Map<String, SubMode>) allField.get(null);
    all.clear();
    all.putAll(subModeAllSnapshot);

    Field counterField = SubMode.class.getDeclaredField("COUNTER");
    counterField.setAccessible(true);
    ((AtomicInteger) counterField.get(null)).set(subModeCounterSnapshot);
  }

  @Test
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
