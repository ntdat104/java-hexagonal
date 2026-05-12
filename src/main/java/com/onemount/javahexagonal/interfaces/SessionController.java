package com.onemount.javahexagonal.interfaces;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onemount.javahexagonal.application.dto.request.CreateSessionRequest;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.DefinitionDto;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest.NodeDto;
import com.onemount.javahexagonal.application.dto.request.SubmitStepRequest;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.enums.SessionChannel;
import com.onemount.javahexagonal.application.service.SessionExecutor;
import com.onemount.javahexagonal.domain.model.Session;
import com.onemount.javahexagonal.domain.model.SessionStep;
import com.onemount.javahexagonal.domain.model.WorkflowDefinition;
import com.onemount.javahexagonal.domain.repo.SessionRepo;
import com.onemount.javahexagonal.domain.repo.SessionStepRepo;
import com.onemount.javahexagonal.domain.repo.WorkflowDefinitionRepo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionExecutor sessionExecutor;
    private final SessionRepo sessionRepo;
    private final SessionStepRepo sessionStepRepo;
    private final WorkflowDefinitionRepo workflowDefinitionRepo;
    private final ObjectMapper objectMapper;

    private static final Set<String> USER_FACING_TYPES = Set.of(
            "CONSENT_COLLECT", "FORM_COLLECT", "DEVICE_CHECK", "DOC_CHECK",
            "QR_CODE", "NFC_CHECK", "SELFIE_CHECK"
    );

    @PostMapping
    public ResponseEntity<BaseResponse<Map<String, Object>>> createSession(
            @Valid @RequestBody CreateSessionRequest req) {
        WorkflowDefinition workflowDef = workflowDefinitionRepo.findByUid(req.getWorkflowUid())
                .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + req.getWorkflowUid()));

        Session session = sessionExecutor.createSession(
                workflowDef, req.getTenantId(), req.getUserRef(),
                SessionChannel.valueOf(req.getChannel()), req.getInputData());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(buildStateResponse(session, workflowDef)));
    }

    @PostMapping("/{sessionId}/steps")
    public ResponseEntity<BaseResponse<Map<String, Object>>> submitStep(
            @PathVariable String sessionId,
            @Valid @RequestBody SubmitStepRequest req) {
        Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        WorkflowDefinition workflowDef = workflowDefinitionRepo.findById(session.getWorkflowDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found"));

        session = sessionExecutor.submitStep(session, workflowDef,
                req.getNodeId(), req.getInputData());

        return ResponseEntity.ok(BaseResponse.ofSucceeded(buildStateResponse(session, workflowDef)));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<BaseResponse<Map<String, Object>>> getSession(
            @PathVariable String sessionId) {
        Session session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        WorkflowDefinition workflowDef = workflowDefinitionRepo.findById(session.getWorkflowDefinitionId())
                .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found"));
        List<SessionStep> steps = sessionStepRepo.findBySessionIdOrderByStartedAtAsc(sessionId);
        Map<String, Object> response = buildStateResponse(session, workflowDef);
        response.put("steps", steps);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(response));
    }

    private Map<String, Object> buildStateResponse(Session session, WorkflowDefinition workflowDef) {
        // Compute pendingUserSteps and waiting from activeNodeIds + DAG
        List<String> activeIds = parseActiveNodeIds(session);
        Map<String, NodeDto> nodeMap = buildNodeMap(workflowDef);
        Map<String, Map<String, Object>> nodeOutputs = parseNodeOutputs(session.getNodeOutputStore());

        List<Map<String, Object>> pendingUserSteps = activeIds.stream()
                .filter(id -> {
                    NodeDto node = nodeMap.get(id);
                    return node != null && USER_FACING_TYPES.contains(node.getType());
                })
                .filter(id -> !nodeOutputs.containsKey(id))
                .map(id -> {
                    NodeDto node = nodeMap.get(id);
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("node_id",   id);
                    m.put("node_type", node.getType());
                    m.put("config",    node.getConfig()); // Chứa thông tin Schema, fields, v.v. để FE hiển thị
                    return m;
                })
                .collect(Collectors.toList());

        // Logic kiểm tra waiting cho Parallel Join
        boolean waiting = activeIds.stream()
                .anyMatch(id -> {
                    NodeDto node = nodeMap.get(id);
                    return node != null && "PARALLEL_JOIN".equals(node.getType()) && !nodeOutputs.containsKey(id);
                });

        String uiHint = null;
        if (!pendingUserSteps.isEmpty()) uiHint = "INTERACTION_REQUIRED";
        else if (waiting || !activeIds.isEmpty()) uiHint = "PLEASE_WAIT";
        else if ("COMPLETED".equals(session.getStatus())) uiHint = "FINISHED";

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("session_id",          session.getId());
        resp.put("status",              session.getStatus());
        resp.put("current_node_id",     session.getCurrentNodeId());
        resp.put("active_node_ids",     activeIds);
        resp.put("pending_user_steps",  pendingUserSteps);
        resp.put("waiting",             waiting);
        resp.put("ui_hint",             uiHint);
        resp.put("workflow_uid",        session.getWorkflowUid());
        resp.put("user_ref",            session.getUserRef());
        resp.put("started_at",          session.getStartedAt());
        resp.put("completed_at",        session.getCompletedAt());
        resp.put("expires_at",          session.getExpiresAt());
        return resp;
    }

    private List<String> parseActiveNodeIds(Session session) {
        String json = session.getActiveNodeIds();
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> parseNodeOutputs(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<Map<String, Map<String, Object>>>() {}); }
        catch (Exception e) { return Map.of(); }
    }

    private Map<String, NodeDto> buildNodeMap(WorkflowDefinition workflowDef) {
        try {
            DefinitionDto def = objectMapper.readValue(workflowDef.getDefinition(), DefinitionDto.class);
            return def.getNodes().stream()
                    .collect(Collectors.toMap(NodeDto::getId, node -> node));
        } catch (Exception e) {
            return Map.of();
        }
    }
}
