package com.genealogy.platform.services.research.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.genealogy.platform.libs.security.abac.AbacDecision;
import com.genealogy.platform.libs.security.abac.AbacDecisionCache;
import com.genealogy.platform.libs.security.abac.AbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.AbacRequest;
import com.genealogy.platform.libs.security.abac.DefaultAbacPolicyEngine;
import com.genealogy.platform.libs.security.abac.Jurisdiction;
import com.genealogy.platform.libs.security.abac.LivingStatus;
import com.genealogy.platform.libs.security.abac.OpenFgaAbacGuard;
import com.genealogy.platform.libs.security.abac.OpenFgaAbacGuard.OpenfgaCheckSupplier;
import com.genealogy.platform.libs.security.abac.PrivacyClass;
import com.genealogy.platform.libs.security.abac.ReasonCode;
import com.genealogy.platform.services.research.authorization.ResearchReAuthorizationPort.Action;
import com.genealogy.platform.services.research.authorization.ResearchReAuthorizationPort.ResearchReAuthorizationDeniedException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchReAuthorizationPortTest {

    private final AbacPolicyEngine engine = new DefaultAbacPolicyEngine();

    @Test
    @DisplayName("OpenFGA allow → ABAC allow → no exception")
    void openfgaAllowAbacAllow() {
        OpenfgaCheckSupplier supplier = (tenantId, subjectId, resourceType, resourceId, action) ->
                Optional.of("check-1");
        OpenFgaAbacGuard guard = new OpenFgaAbacGuard(engine, new AbacDecisionCache(), supplier);
        ResearchReAuthorizationPort port = new ResearchReAuthorizationPort(guard, engine);
        port.require("tenant-a", "actor-1", "ROLE_EDITOR", "citation", "cite-1",
                Action.CITATION_SUBMIT, null);
    }

    @Test
    @DisplayName("OpenFGA deny → throws with reasonCode OPENFGA_DENY")
    void openfgaDenyThrows() {
        OpenfgaCheckSupplier supplier = (tenantId, subjectId, resourceType, resourceId, action) ->
                Optional.empty();
        OpenFgaAbacGuard guard = new OpenFgaAbacGuard(engine, new AbacDecisionCache(), supplier);
        ResearchReAuthorizationPort port = new ResearchReAuthorizationPort(guard, engine);
        assertThatThrownBy(() -> port.require(
                "tenant-a", "actor-1", "ROLE_EDITOR", "citation", "cite-1",
                Action.CITATION_SUBMIT, null))
                .isInstanceOf(ResearchReAuthorizationDeniedException.class)
                .hasMessageContaining("openfga_deny");
    }

    @Test
    @DisplayName("Action enum is closed-set (no stray values)")
    void actionClosedSet() {
        assertThat(Action.values()).containsExactly(
                Action.CITATION_SUBMIT,
                Action.CITATION_APPROVE,
                Action.CONFLICT_PARTIAL_MERGE,
                Action.HYPOTHESIS_PROMOTE,
                Action.RESEARCH_TASK_ASSIGN);
    }

    @Test
    @DisplayName("Action wire() returns the lower_snake_case identifier")
    void actionWireFormat() {
        assertThat(Action.CITATION_SUBMIT.wire()).isEqualTo("citation_submit");
        assertThat(Action.CITATION_APPROVE.wire()).isEqualTo("citation_approve");
        assertThat(Action.CONFLICT_PARTIAL_MERGE.wire()).isEqualTo("conflict_partial_merge");
        assertThat(Action.HYPOTHESIS_PROMOTE.wire()).isEqualTo("hypothesis_promote");
        assertThat(Action.RESEARCH_TASK_ASSIGN.wire()).isEqualTo("research_task_assign");
    }

    @Test
    @DisplayName("TestFriendly seam: still wires OpenFGA + ABAC combo")
    void testFriendlySeam() {
        OpenfgaCheckSupplier supplier = (tenantId, subjectId, resourceType, resourceId, action) ->
                Optional.of("check-1");
        ResearchReAuthorizationPort.TestFriendly port =
                new ResearchReAuthorizationPort.TestFriendly(
                        () -> Optional.of("check-1"), engine);
        AbacRequest request = new AbacRequest(
                "tenant-a", "actor-1", "ROLE_EDITOR", PrivacyClass.PRIVATE,
                "citation", "cite-1", LivingStatus.unknown(), null,
                new Jurisdiction("GLOBAL"), false, false, false, false,
                java.util.Map.of());
        AbacDecision decision = port.evaluate(request, Action.CITATION_SUBMIT);
        assertThat(decision.effect()).isEqualTo(AbacDecision.Effect.ALLOW);
        assertThat(decision.openfgaCheckId()).contains("check-1");
        assertThat(decision.attributes()).containsEntry("action", "citation_submit");
    }

    @Test
    @DisplayName("TestFriendly seam: OpenFGA deny returns the deny decision")
    void testFriendlyDeny() {
        OpenfgaCheckSupplier supplier = (tenantId, subjectId, resourceType, resourceId, action) ->
                Optional.empty();
        ResearchReAuthorizationPort.TestFriendly port =
                new ResearchReAuthorizationPort.TestFriendly(
                        () -> Optional.empty(), engine);
        AbacRequest request = new AbacRequest(
                "tenant-a", "actor-1", "ROLE_EDITOR", PrivacyClass.PRIVATE,
                "citation", "cite-1", LivingStatus.unknown(), null,
                new Jurisdiction("GLOBAL"), false, false, false, false,
                java.util.Map.of());
        AbacDecision decision = port.evaluate(request, Action.CITATION_APPROVE);
        assertThat(decision.effect()).isEqualTo(AbacDecision.Effect.DENY);
        assertThat(decision.reasonCode()).isEqualTo(ReasonCode.OPENFGA_DENY);
    }
}
