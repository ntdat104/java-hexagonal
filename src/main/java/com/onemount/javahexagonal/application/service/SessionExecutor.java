package com.onemount.javahexagonal.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javafaker.Faker;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.DefinitionDto;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.EdgeDto;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.FallbackCaseDto;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.InputMapEntryDto;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.NodeDto;
import com.onemount.javahexagonal.application.enums.*;
import com.onemount.javahexagonal.domain.model.*;
import com.onemount.javahexagonal.domain.repo.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionExecutor {

    private final ObjectMapper objectMapper;
    private final SessionRepo sessionRepo;
    private final SessionStepRepo sessionStepRepo;
    private final SessionSnapshotRepo sessionSnapshotRepo;
    private final SessionEventRepo sessionEventRepo;
    private final OutboxEventRepo outboxEventRepo;

    private static final Faker faker = new Faker(new Locale("vi"));

    // User-facing = client must submit a step; Background = auto-advance
    private static final Set<String> USER_FACING_TYPES = Set.of(
            "CONSENT_COLLECT", "FORM_COLLECT", "DEVICE_CHECK", "DOC_CHECK",
            "QR_CODE", "NFC_CHECK", "SELFIE_CHECK"
    );

    // ─── Public API ──────────────────────────────────────────────────────────

    @Transactional
    public Session createSession(WorkflowDefinition workflowDef, Long tenantId,
                                  String userRef, SessionChannel channel,
                                  Map<String, Object> initialInputs) {
        DefinitionDto dag = parseDag(workflowDef.getDefinition());

        Session session = new Session()
                .setId(UUID.randomUUID().toString())
                .setTenantId(tenantId)
                .setWorkflowDefinitionId(workflowDef.getId())
                .setWorkflowUid(workflowDef.getUid())
                .setWorkflowVersion(workflowDef.getVersion())
                .setUserRef(userRef)
                .setChannel(channel)
                .setStatus(SessionStatus.IN_PROGRESS)
                .setCurrentNodeId(dag.getStartNode())
                .setActiveNodeIds(toJson(List.of(dag.getStartNode())))
                .setSessionInputStore(toJson(initialInputs != null ? initialInputs : new HashMap<>()))
                .setNodeOutputStore(toJson(new HashMap<>()))
                .setStartedAt(new Date())
                .setExpiresAt(new Date(System.currentTimeMillis() + 30 * 60 * 1000L));

        session = sessionRepo.save(session);
        recordEvent(session, EventType.SESSION_CREATED, null, Map.of("workflow_uid", workflowDef.getUid()));
        advance(session, dag);
        return session;
    }

    @Transactional
    public Session submitStep(Session session, WorkflowDefinition workflowDef,
                               String nodeId, Map<String, Object> stepInputs) {
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not IN_PROGRESS");
        }
        DefinitionDto dag = parseDag(workflowDef.getDefinition());
        Map<String, Object> accumulated = parseJsonMap(session.getSessionInputStore());
        accumulated.putAll(stepInputs);
        session.setSessionInputStore(toJson(accumulated));
        sessionRepo.save(session);
        advance(session, dag);
        return session;
    }

    // ─── Core Execution Loop ─────────────────────────────────────────────────

    private void advance(Session session, DefinitionDto dag) {
        Map<String, NodeDto> nodeMap = buildNodeMap(dag);
        Map<String, Object> sessionInputs = parseJsonMap(session.getSessionInputStore());
        Map<String, Map<String, Object>> nodeOutputs = parseNodeOutputs(session.getNodeOutputStore());
        List<String> activeIds = parseActiveNodeIds(session, dag);

        NodeDto lastEndNode = null;
        boolean madeProgress = true;

        while (madeProgress) {
            madeProgress = false;
            // Dedup preserving order
            List<String> currentIds = activeIds.stream().distinct().collect(Collectors.toList());
            List<String> nextIds = new ArrayList<>();

            for (String nodeId : currentIds) {
                NodeDto node = nodeMap.get(nodeId);
                if (node == null) {
                    log.warn("[advance] Unknown node id: {}", nodeId);
                    continue;
                }
                String type = node.getType();

                // ── END ───────────────────────────────────────────────────────
                if ("END".equals(type)) {
                    if (!nodeOutputs.containsKey(nodeId)) {
                        nodeOutputs.put(nodeId, Map.of("end", nodeId));
                        recordEvent(session, EventType.SESSION_STEP_COMPLETED, nodeId, Map.of());
                    }
                    lastEndNode = node;
                    madeProgress = true;
                    // Don't add to nextIds → branch terminates
                    continue;
                }

                // ── PARALLEL_FORK ──────────────────────────────────────────────
                if ("PARALLEL_FORK".equals(type)) {
                    if (!nodeOutputs.containsKey(nodeId)) {
                        List<String> branchStarts = dag.getEdges().stream()
                                .filter(e -> nodeId.equals(e.getFrom()))
                                .map(EdgeDto::getTo)
                                .collect(Collectors.toList());
                        nodeOutputs.put(nodeId, Map.of("branched_to", branchStarts));
                        recordEvent(session, EventType.SESSION_STEP_COMPLETED, nodeId,
                                Map.of("branches", branchStarts));
                        nextIds.addAll(branchStarts);
                        madeProgress = true;
                    } else {
                        // Already executed — transition forward (should not normally happen)
                        nextIds.add(nodeId);
                    }
                    continue;
                }

                // ── PARALLEL_JOIN ──────────────────────────────────────────────
                if ("PARALLEL_JOIN".equals(type)) {
                    if (nodeOutputs.containsKey(nodeId)) {
                        // Already fired — pass through
                        String next = evaluateNextNode(dag, nodeId, nodeOutputs);
                        nextIds.add(next);
                        madeProgress = true;
                    } else {
                        List<?> waitFor = node.getConfig() != null
                                ? (List<?>) node.getConfig().getOrDefault("waitFor", List.of())
                                : List.of();
                        boolean allDone = waitFor.stream()
                                .allMatch(nid -> nodeOutputs.containsKey((String) nid));
                        if (allDone) {
                            Map<String, Object> joinOutput = executeJoin(node, nodeOutputs);
                            saveStep(session, node, Map.of(), joinOutput);
                            nodeOutputs.put(nodeId, joinOutput);
                            recordEvent(session, EventType.SESSION_STEP_COMPLETED, nodeId, joinOutput);
                            String next = evaluateNextNode(dag, nodeId, nodeOutputs);
                            nextIds.add(next);
                            session.setCurrentNodeId(next);
                            madeProgress = true;
                        } else {
                            nextIds.add(nodeId); // gate not ready
                        }
                    }
                    continue;
                }

                // ── Already executed → just transition ─────────────────────────
                if (nodeOutputs.containsKey(nodeId)) {
                    String next = evaluateNextNode(dag, nodeId, nodeOutputs);
                    nextIds.add(next);
                    madeProgress = true;
                    continue;
                }

                // ── User-facing or Background ──────────────────────────────────
                boolean userFacing = USER_FACING_TYPES.contains(type);
                if (!canResolveInputs(node, sessionInputs, nodeOutputs)) {
                    nextIds.add(nodeId); // blocked
                    continue;
                }

                // Resolve fallback case if declared
                if (node.getFallbackChain() != null && !node.getFallbackChain().isEmpty()) {
                    try {
                        resolveFallbackCase(node, sessionInputs); // throws if no viable case
                    } catch (RuntimeException e) {
                        log.warn("[advance] No viable fallback for node {}: {}", nodeId, e.getMessage());
                        nextIds.add(nodeId);
                        continue;
                    }
                }

                Map<String, Object> inputs = resolveInputs(node, sessionInputs, nodeOutputs);
                Map<String, Object> output = executeNode(node, inputs, sessionInputs);
                saveStep(session, node, inputs, output);
                nodeOutputs.put(nodeId, output);
                session.setNodeOutputStore(toJson(nodeOutputs));
                recordEvent(session, EventType.SESSION_STEP_COMPLETED, nodeId, output);
                session.setCurrentNodeId(nodeId);
                String nextNodeId = evaluateNextNode(dag, nodeId, nodeOutputs);
                nextIds.add(nextNodeId);
                session.setCurrentNodeId(nextNodeId);
                madeProgress = true;
            }

            activeIds = nextIds;
        }

        // Dedup and persist
        activeIds = activeIds.stream().distinct().collect(Collectors.toList());
        session.setActiveNodeIds(toJson(activeIds));
        session.setNodeOutputStore(toJson(nodeOutputs));

        // All branches terminated
        if (activeIds.isEmpty() && session.getStatus() == SessionStatus.IN_PROGRESS) {
            int totalSteps = sessionStepRepo.findBySessionIdOrderByStartedAtAsc(session.getId()).size();
            completeSession(session, lastEndNode, nodeOutputs, totalSteps);
        }

        sessionRepo.save(session);
    }

    // ─── Node Execution ───────────────────────────────────────────────────────

    private Map<String, Object> executeNode(NodeDto node, Map<String, Object> inputs,
                                             Map<String, Object> sessionInputs) {
        log.info("[SessionExecutor] Executing node: {} type: {}", node.getId(), node.getType());
        return switch (node.getType()) {
            // ── Background ──────────────────────────────────────────────────────
            case "LIVENESS_CHECK"    -> mockLiveness(inputs, sessionInputs);
            case "FACE_MATCH"        -> mockFaceMatch(inputs, sessionInputs);
            case "FACE_SEARCH"       -> mockFaceSearch(inputs, sessionInputs);
            case "FRAUD_CHECK"       -> mockFraudCheck(inputs, sessionInputs);
            case "NFC_VERIFY"        -> mockNfcVerify(inputs, sessionInputs);
            case "DECISION"          -> executeDecision(node, inputs);
            case "CONDITION_BRANCH"  -> Map.of("status", "EVALUATED");
            case "CUSTOM_API_CALL"   -> mockCustomApiCall(node, inputs);
            case "DOCUMENT_DEDUP"    -> mockDocumentDedup(inputs, sessionInputs);
            case "AML_SCREENING"     -> mockAmlScreening(inputs, sessionInputs);
            case "OCR_EXTRACT"       -> mockOcr(node, inputs);
            // ── User-facing (execute once user has submitted inputs) ─────────────
            case "CONSENT_COLLECT"   -> mockConsentCollect(inputs, sessionInputs);
            case "FORM_COLLECT"      -> mockFormCollect(node, inputs, sessionInputs);
            case "DEVICE_CHECK"      -> mockDeviceCheck(inputs, sessionInputs);
            case "DOC_CHECK"         -> mockDocCheck(node, inputs, sessionInputs);
            case "QR_CODE"           -> mockQrCode(inputs, sessionInputs);
            case "NFC_CHECK"         -> mockNfcCheck(inputs, sessionInputs);
            case "SELFIE_CHECK"      -> mockSelfieCheck(node, inputs, sessionInputs);
            default                  -> Map.of("status", "SKIPPED");
        };
    }

    // ─── Mock Implementations ─────────────────────────────────────────────────

    private Map<String, Object> mockConsentCollect(Map<String, Object> inputs,
                                                    Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_consent_given", "true");
        return Map.of("consent_given", !"false".equals(override));
    }

    private Map<String, Object> mockFormCollect(NodeDto node, Map<String, Object> inputs,
                                                  Map<String, Object> sessionInputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("collected", true);
        result.putAll(inputs); // echo back submitted form fields
        return result;
    }

    private Map<String, Object> mockDeviceCheck(Map<String, Object> inputs,
                                                  Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_risk_score", null);
        double score = override != null ? Double.parseDouble(override) : 0.2 + Math.random() * 0.4;
        return Map.of("risk_score", Math.round(score * 100.0) / 100.0);
    }

    private Map<String, Object> mockDocCheck(NodeDto node, Map<String, Object> inputs,
                                               Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_doc_quality", "GOOD");
        int maxAttempts = node.getConfig() != null
                ? ((Number) node.getConfig().getOrDefault("maxAttempts", 3)).intValue() : 3;
        String imagePath = "/tmp/doc_" + node.getId() + ".jpg";
        return Map.of(
                "ocr_result",   Map.of("id_number", "0" + faker.number().digits(11),
                                       "full_name",  faker.name().fullName().toUpperCase()),
                "ui_hint",      "GOOD".equals(override) ? null : "RETRY_FRONT_CAPTURE",
                "attempt_left", maxAttempts - 1,
                "image_path",   imagePath,
                "good_quality", "GOOD".equals(override)
        );
    }

    private Map<String, Object> mockQrCode(Map<String, Object> inputs,
                                            Map<String, Object> sessionInputs) {
        return Map.of("qr_code", "QR_" + faker.number().digits(12),
                      "decoded", true);
    }

    private Map<String, Object> mockNfcCheck(Map<String, Object> inputs,
                                               Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_nfc_hash_valid", "true");
        boolean hashValid = !"false".equals(override);
        return Map.of(
                "nfc_decoded_data",     Map.of("id_number", "0" + faker.number().digits(11)),
                "sod_value",            "BASE64_SOD_" + faker.number().digits(8),
                "attempt_left",         2,
                "hash_validation_result", hashValid
        );
    }

    private Map<String, Object> mockSelfieCheck(NodeDto node, Map<String, Object> inputs,
                                                  Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_selfie_quality", "GOOD");
        return Map.of(
                "quality_check_result", Map.of("score", 0.95, "passed", "GOOD".equals(override)),
                "ui_hint",              "GOOD".equals(override) ? null : "RETRY_SELFIE",
                "attempt_left",         2,
                "image_path",           "/tmp/selfie_" + node.getId() + ".jpg"
        );
    }

    private Map<String, Object> mockOcr(NodeDto node, Map<String, Object> inputs) {
        return Map.of(
                "id_number",   "0" + faker.number().digits(11),
                "full_name",   faker.name().fullName().toUpperCase(),
                "dob",         String.format("%02d/%02d/%d", faker.number().numberBetween(1, 28),
                                             faker.number().numberBetween(1, 12),
                                             faker.number().numberBetween(1970, 2000)),
                "gender",      faker.bool().bool() ? "NAM" : "NU",
                "nationality", "Viet Nam",
                "confidence",  0.97,
                "tamper_flag", false
        );
    }

    private Map<String, Object> mockLiveness(Map<String, Object> inputs,
                                              Map<String, Object> sessionInputs) {
        String forced = (String) sessionInputs.getOrDefault("_mock_liveness_result", null);
        String result  = forced != null ? forced : "LIVE";
        boolean isLive = "LIVE".equals(result);
        double score   = isLive ? 0.90 + Math.random() * 0.09 : 0.20 + Math.random() * 0.25;
        return Map.of(
                "liveness_result", result,
                "score",           Math.round(score * 100.0) / 100.0,
                "spoof_type",      isLive ? "NONE" : "REPLAY_ATTACK"
        );
    }

    private Map<String, Object> mockFaceMatch(Map<String, Object> inputs,
                                               Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_face_match_result", null);
        boolean matched = !"NOT_MATCHED".equals(override);
        double score = matched ? 0.90 + Math.random() * 0.09 : 0.20 + Math.random() * 0.25;
        return Map.of(
                "face_match_result", matched ? "MATCHED" : "NOT_MATCHED",
                "similarity_score",  Math.round(score * 100.0) / 100.0
        );
    }

    private Map<String, Object> mockFaceSearch(Map<String, Object> inputs,
                                                Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_face_search_result", "CLEAR");
        return Map.of("face_search_result", override);
    }

    private Map<String, Object> mockFraudCheck(Map<String, Object> inputs,
                                                Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_fraud_result", "CLEAR");
        return Map.of("fraud_check_result", override,
                      "fraud_score", 0.05 + Math.random() * 0.1);
    }

    private Map<String, Object> mockNfcVerify(Map<String, Object> inputs,
                                               Map<String, Object> sessionInputs) {
        String override = (String) sessionInputs.getOrDefault("_mock_nfc_verified", "true");
        return Map.of("verification_result", !"false".equals(override));
    }

    private Map<String, Object> mockDocumentDedup(Map<String, Object> inputs,
                                                   Map<String, Object> sessionInputs) {
        String forced = (String) sessionInputs.getOrDefault("_mock_dedup_verdict", "CLEAR");
        return Map.of("dedup_verdict", forced);
    }

    private Map<String, Object> mockAmlScreening(Map<String, Object> inputs,
                                                  Map<String, Object> sessionInputs) {
        String forced = (String) sessionInputs.getOrDefault("_mock_aml_verdict", "CLEAR");
        return Map.of("aml_verdict", forced);
    }

    private Map<String, Object> mockCustomApiCall(NodeDto node, Map<String, Object> inputs) {
        return Map.of("status_code", 200, "response", Map.of("success", true));
    }

    private Map<String, Object> executeDecision(NodeDto node, Map<String, Object> inputs) {
        if (node.getConfig() == null || !node.getConfig().containsKey("branches")) {
            return Map.of("branch", "DECLINE");
        }
        List<?> rawBranches = (List<?>) node.getConfig().get("branches");
        for (Object rawBranch : rawBranches) {
            Map<?, ?> branch = (Map<?, ?>) rawBranch;
            if (Boolean.TRUE.equals(branch.get("default"))) continue;
            List<?> conditions = (List<?>) branch.get("conditions");
            if (conditions != null && evaluateBranchConditions(conditions, inputs)) {
                return Map.of("branch", branch.get("name"));
            }
        }
        for (Object rawBranch : rawBranches) {
            Map<?, ?> branch = (Map<?, ?>) rawBranch;
            if (Boolean.TRUE.equals(branch.get("default"))) return Map.of("branch", branch.get("name"));
        }
        return Map.of("branch", "DECLINE");
    }

    private boolean evaluateBranchConditions(List<?> conditions, Map<String, Object> inputs) {
        for (Object rawCond : conditions) {
            Map<?, ?> cond = (Map<?, ?>) rawCond;
            String field = (String) cond.get("field");
            String op    = (String) cond.get("op");
            Object value = cond.get("value");
            if (!evalOp(inputs.get(field), op, value)) return false;
        }
        return true;
    }

    private Map<String, Object> executeJoin(NodeDto joinNode,
                                             Map<String, Map<String, Object>> nodeOutputs) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (joinNode.getConfig() == null) return merged;
        List<?> waitFor = (List<?>) joinNode.getConfig().get("waitFor");
        if (waitFor == null) return merged;
        for (Object nodeId : waitFor) {
            Map<String, Object> out = nodeOutputs.get((String) nodeId);
            if (out != null) merged.putAll(out);
        }
        return merged;
    }

    // ─── Edge Evaluation ─────────────────────────────────────────────────────

    private String evaluateNextNode(DefinitionDto dag, String fromNodeId,
                                     Map<String, Map<String, Object>> nodeOutputs) {
        List<EdgeDto> outgoing = dag.getEdges().stream()
                .filter(e -> fromNodeId.equals(e.getFrom()))
                .collect(Collectors.toList());
        for (EdgeDto edge : outgoing) {
            if (edge.getCondition() == null) return edge.getTo(); // unconditional → first match
            if (evaluateEdgeCondition(edge.getCondition(), fromNodeId, nodeOutputs)) return edge.getTo();
        }
        throw new IllegalStateException("No matching edge from node: " + fromNodeId);
    }

    // Condition format: {"field": "outcome", "op": "EQ", "value": "APPROVE"}
    // field is resolved from the FROM node's output (dot-path supported)
    private boolean evaluateEdgeCondition(Map<String, Object> cond, String fromNodeId,
                                           Map<String, Map<String, Object>> nodeOutputs) {
        String field    = (String) cond.get("field");
        String op       = (String) cond.get("op");
        Object expected = cond.get("value");
        Map<String, Object> fromOutput = nodeOutputs.get(fromNodeId);
        if (fromOutput == null) return false;
        Object actual = resolveField(fromOutput, field);
        return evalOp(actual, op != null ? op.toUpperCase() : "", expected);
    }

    private boolean evalOp(Object actual, String op, Object expected) {
        if (actual == null) return false;
        return switch (op.toUpperCase()) {
            case "EQ"  -> Objects.equals(actual.toString(),
                                          expected != null ? expected.toString() : null);
            case "NEQ" -> !Objects.equals(actual.toString(),
                                           expected != null ? expected.toString() : null);
            case "GTE" -> toDouble(actual) >= toDouble(expected);
            case "GT"  -> toDouble(actual) >  toDouble(expected);
            case "LTE" -> toDouble(actual) <= toDouble(expected);
            case "LT"  -> toDouble(actual) <  toDouble(expected);
            default    -> false;
        };
    }

    private double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return Double.parseDouble(v.toString());
    }

    // ─── Field / dot-path resolver ────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Object resolveField(Map<String, Object> map, String path) {
        if (path == null || map == null) return null;
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (!(current instanceof Map)) return null;
            current = ((Map<String, Object>) current).get(part);
            if (current == null) return null;
        }
        return current;
    }

    // ─── Input Resolution ────────────────────────────────────────────────────

    private boolean canResolveInputs(NodeDto node, Map<String, Object> sessionInputs,
                                      Map<String, Map<String, Object>> nodeOutputs) {
        if (node.getInputMap() == null) return true;
        for (Map.Entry<String, InputMapEntryDto> entry : node.getInputMap().entrySet()) {
            InputMapEntryDto m = entry.getValue();
            String bindingType = m.getType() != null ? m.getType() : "SESSION_INPUT";
            switch (bindingType) {
                case "SESSION_INPUT" -> {
                    String lookupKey = m.getKey() != null ? m.getKey() : entry.getKey();
                    if (!sessionInputs.containsKey(lookupKey)) return false;
                }
                case "NODE_OUTPUT" -> {
                    Map<String, Object> nodeOut = nodeOutputs.get(m.getNodeId());
                    if (nodeOut == null) return false;
                    if (m.getField() != null && resolveField(nodeOut, m.getField()) == null) return false;
                }
                case "TENANT_CONFIG", "CONFIG_VALUE" -> { /* always resolvable */ }
            }
        }
        return true;
    }

    private Map<String, Object> resolveInputs(NodeDto node, Map<String, Object> sessionInputs,
                                               Map<String, Map<String, Object>> nodeOutputs) {
        if (node.getInputMap() == null) return Map.of();
        Map<String, Object> resolved = new HashMap<>();
        for (Map.Entry<String, InputMapEntryDto> entry : node.getInputMap().entrySet()) {
            InputMapEntryDto m = entry.getValue();
            String bindingType = m.getType() != null ? m.getType() : "SESSION_INPUT";
            switch (bindingType) {
                case "SESSION_INPUT" -> {
                    String lookupKey = m.getKey() != null ? m.getKey() : entry.getKey();
                    resolved.put(entry.getKey(), sessionInputs.get(lookupKey));
                }
                case "NODE_OUTPUT" -> {
                    Map<String, Object> nodeOut = nodeOutputs.get(m.getNodeId());
                    if (nodeOut != null) {
                        Object val = m.getField() != null ? resolveField(nodeOut, m.getField()) : nodeOut;
                        resolved.put(entry.getKey(), val);
                    }
                }
                case "TENANT_CONFIG" -> resolved.put(entry.getKey(), "CONFIG:" + m.getKey());
                case "CONFIG_VALUE"  -> resolved.put(entry.getKey(), m.getKey());
            }
        }
        return resolved;
    }

    // ─── FallbackChain ────────────────────────────────────────────────────────

    private String resolveFallbackCase(NodeDto node, Map<String, Object> sessionInputs) {
        for (FallbackCaseDto fc : node.getFallbackChain()) {
            if (fc.getRequiredInputs() == null || fc.getRequiredInputs().isEmpty()) return fc.getName();
            boolean allPresent = fc.getRequiredInputs().stream().allMatch(sessionInputs::containsKey);
            if (allPresent) return fc.getName();
        }
        throw new RuntimeException("NO_VIABLE_FALLBACK for node: " + node.getId());
    }

    // ─── Session Completion ──────────────────────────────────────────────────

    private void completeSession(Session session, NodeDto endNode,
                                  Map<String, Map<String, Object>> nodeOutputs, int totalSteps) {
        String outcomeStr = (endNode != null && endNode.getConfig() != null)
                ? (String) endNode.getConfig().getOrDefault("outcome", "INCOMPLETE")
                : "INCOMPLETE";
        SessionOutcome outcome = switch (outcomeStr.toUpperCase()) {
            case "APPROVED" -> SessionOutcome.APPROVED;
            case "REVIEW"   -> SessionOutcome.REFERRED;
            case "DECLINED" -> SessionOutcome.DECLINED;
            default         -> SessionOutcome.INCOMPLETE;
        };

        session.setStatus(SessionStatus.COMPLETED).setCompletedAt(new Date());
        sessionRepo.save(session);

        Map<String, Object> decisionData = new HashMap<>();
        nodeOutputs.forEach((nid, output) ->
                output.forEach((k, v) -> decisionData.put(nid + "." + k, v)));

        SessionSnapshot snapshot = new SessionSnapshot()
                .setSessionId(session.getId())
                .setTenantId(session.getTenantId())
                .setWorkflowUid(session.getWorkflowUid())
                .setWorkflowVersion(session.getWorkflowVersion())
                .setUserRef(session.getUserRef())
                .setOutcome(outcome)
                .setDecisionData(toJson(decisionData))
                .setFinalNodeId(endNode != null ? endNode.getId() : null)
                .setTotalSteps(totalSteps)
                .setCompletedAt(new Date());
        sessionSnapshotRepo.save(snapshot);

        recordEvent(session, EventType.SESSION_COMPLETED, endNode != null ? endNode.getId() : null,
                Map.of("outcome", outcome.name(), "total_steps", totalSteps));

        OutboxEvent outbox = new OutboxEvent()
                .setTenantId(session.getTenantId())
                .setEventType("SESSION_COMPLETED")
                .setAggregateId(session.getId())
                .setAggregateType("SESSION")
                .setPayload(toJson(Map.of(
                        "session_id",   session.getId(),
                        "outcome",      outcome.name(),
                        "workflow_uid", session.getWorkflowUid(),
                        "user_ref",     session.getUserRef()
                )))
                .setStatus(OutboxStatus.PENDING)
                .setAttempts(0)
                .setCreatedAt(new Date())
                .setNextProcessAt(new Date());
        outboxEventRepo.save(outbox);
    }

    // ─── Persistence Helpers ─────────────────────────────────────────────────

    private void saveStep(Session session, NodeDto node,
                           Map<String, Object> inputs, Map<String, Object> output) {
        Date now = new Date();
        SessionStep step = new SessionStep()
                .setSessionId(session.getId())
                .setNodeId(node.getId())
                .setNodeType(node.getType())
                .setAttemptNumber(1)
                .setStatus(SessionStepStatus.COMPLETED)
                .setInputSnapshot(toJson(inputs))
                .setOutput(toJson(output))
                .setStartedAt(now)
                .setCompletedAt(now);
        sessionStepRepo.save(step);
    }

    private void recordEvent(Session session, EventType type, String nodeId,
                              Map<String, Object> payload) {
        SessionEvent event = new SessionEvent()
                .setSessionId(session.getId())
                .setTenantId(session.getTenantId())
                .setEventType(type)
                .setNodeId(nodeId)
                .setActorType("SYSTEM")
                .setPayload(toJson(payload))
                .setOccurredAt(new Date());
        sessionEventRepo.save(event);
    }

    // ─── DAG / JSON Helpers ───────────────────────────────────────────────────

    private DefinitionDto parseDag(String definitionJson) {
        try {
            return objectMapper.readValue(definitionJson, DefinitionDto.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse workflow definition: " + e.getMessage(), e);
        }
    }

    private Map<String, NodeDto> buildNodeMap(DefinitionDto dag) {
        return dag.getNodes().stream().collect(Collectors.toMap(NodeDto::getId, n -> n));
    }

    private List<String> parseActiveNodeIds(Session session, DefinitionDto dag) {
        String json = session.getActiveNodeIds();
        if (json == null || json.isBlank()) return new ArrayList<>(List.of(dag.getStartNode()));
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return new ArrayList<>(List.of(dag.getStartNode()));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {}); }
        catch (Exception e) { return new HashMap<>(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> parseNodeOutputs(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {}); }
        catch (Exception e) { return new HashMap<>(); }
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }
}
