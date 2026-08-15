package com.genealogy.platform.services.operations.capacity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.genealogy.platform.services.operations.capacity.CapacityGuard.ConnectionPoolSnapshot;
import com.genealogy.platform.services.operations.capacity.CapacityGuard.CwvSnapshot;
import com.genealogy.platform.services.operations.capacity.CapacityGuard.Outcome;
import com.genealogy.platform.services.operations.capacity.CapacityGuard.RegressionSnapshot;
import org.junit.jupiter.api.Test;

class CapacityGuardTest {

  @Test
  void unknownWorkloadIsRejected() {
    Outcome out = CapacityGuard.validateWorkloadClass("made_up");
    assertEquals(CapacityGuard.STATE_INVALID, out.state);
  }

  @Test
  void knownWorkloadIsAccepted() {
    Outcome out = CapacityGuard.validateWorkloadClass("browse_tree");
    assertEquals(CapacityGuard.STATE_OK, out.state);
  }

  @Test
  void hpaMetricClosedSetIsEnforced() {
    assertEquals(CapacityGuard.STATE_OK,
        CapacityGuard.validateHpaMetric("rps").state);
    assertEquals(CapacityGuard.STATE_INVALID,
        CapacityGuard.validateHpaMetric("manualScaling").state);
  }

  @Test
  void connectionPoolOverCeilingIsRejected() {
    Outcome out = CapacityGuard.validateConnectionPool(
        new ConnectionPoolSnapshot(76, 100, 100, 100, 100, 50));
    assertEquals(CapacityGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("postgres"));
  }

  @Test
  void connectionPoolUnderCeilingIsAccepted() {
    Outcome out = CapacityGuard.validateConnectionPool(
        new ConnectionPoolSnapshot(50, 100, 100, 100, 100, 50));
    assertEquals(CapacityGuard.STATE_OK, out.state);
  }

  @Test
  void kafkaPartitionsRespectCeiling() {
    Outcome out = CapacityGuard.validateKafkaPartitions(257, 1000);
    assertEquals(CapacityGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("KAFKA_PARTITIONS_OVER_CAP"));
  }

  @Test
  void kafkaPartitionsUnderRequired() {
    Outcome out = CapacityGuard.validateKafkaPartitions(3, 30000);
    assertEquals(CapacityGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("UNDER_REQUIRED"));
  }

  @Test
  void kafkaPartitionsRightSized() {
    Outcome out = CapacityGuard.validateKafkaPartitions(30, 30000);
    assertEquals(CapacityGuard.STATE_OK, out.state);
  }

  @Test
  void temporalWorkersUnderRequired() {
    Outcome out = CapacityGuard.validateTemporalWorkers(2, 200);
    assertEquals(CapacityGuard.STATE_OVER_LIMIT, out.state);
  }

  @Test
  void temporalWorkersOverMax() {
    Outcome out = CapacityGuard.validateTemporalWorkers(101, 100);
    assertEquals(CapacityGuard.STATE_OVER_LIMIT, out.state);
    assertTrue(out.violationCode.contains("OVER_MAX"));
  }

  @Test
  void temporalWorkersRightSized() {
    Outcome out = CapacityGuard.validateTemporalWorkers(10, 200);
    assertEquals(CapacityGuard.STATE_OK, out.state);
  }

  @Test
  void regressionBlocksCanaryAtTenPercent() {
    RegressionSnapshot snap = new RegressionSnapshot(15.0, 0.0, 0.0, 0.0);
    assertTrue(CapacityGuard.blocksCanary(snap));
  }

  @Test
  void regressionUnderTenPercentDoesNotBlock() {
    RegressionSnapshot snap = new RegressionSnapshot(5.0, 5.0, 5.0, 1.0);
    assertFalse(CapacityGuard.blocksCanary(snap));
  }

  @Test
  void syntheticDatasetRequiresMarker() {
    Outcome out = CapacityGuard.validateSyntheticDataset(
        "vi-VN", false, false, false);
    assertEquals(CapacityGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("SYNTHETIC_ONLY"));
  }

  @Test
  void syntheticDatasetRejectsRealDna() {
    Outcome out = CapacityGuard.validateSyntheticDataset(
        "vi-VN", true, false, true);
    assertEquals(CapacityGuard.STATE_INVALID, out.state);
    assertTrue(out.violationCode.contains("REAL_DNA"));
  }

  @Test
  void syntheticDatasetAcceptsValid() {
    Outcome out = CapacityGuard.validateSyntheticDataset(
        "vi-VN", true, false, false);
    assertEquals(CapacityGuard.STATE_OK, out.state);
  }

  @Test
  void cwvRejectsLcpOverBudget() {
    Outcome out = CapacityGuard.validateCwv(new CwvSnapshot(
        2600, 0.05, 100, 500, 2000, 100));
    assertEquals(CapacityGuard.STATE_REGRESSION, out.state);
    assertTrue(out.violationCode.contains("lcp"));
  }

  @Test
  void cwvAcceptsInBudget() {
    Outcome out = CapacityGuard.validateCwv(new CwvSnapshot(
        2000, 0.05, 100, 500, 2000, 100));
    assertEquals(CapacityGuard.STATE_OK, out.state);
  }
}