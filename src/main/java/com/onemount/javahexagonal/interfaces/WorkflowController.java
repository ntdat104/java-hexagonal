package com.onemount.javahexagonal.interfaces;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onemount.javahexagonal.application.dto.request.CreateWorkflowRequest;
import com.onemount.javahexagonal.application.dto.response.BaseResponse;
import com.onemount.javahexagonal.application.dto.response.WorkflowDefinitionResponse;
import com.onemount.javahexagonal.application.enums.WorkflowStatus;
import com.onemount.javahexagonal.domain.model.WorkflowDefinition;
import com.onemount.javahexagonal.domain.repo.WorkflowDefinitionRepo;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workflows")
public class WorkflowController {

    private final WorkflowDefinitionRepo workflowDefinitionRepo;
    private final ObjectMapper objectMapper;

    @PatchMapping("/{uid}/status")
    @Transactional
    public ResponseEntity<BaseResponse<Void>> updateStatus(
            @PathVariable String uid,
            @RequestBody Map<String, String> payload) {

        String newStatusStr = payload.get("status");
        if (newStatusStr == null) {
            throw new IllegalArgumentException("Trường 'status' là bắt buộc");
        }

        WorkflowDefinition existing = workflowDefinitionRepo.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("Workflow không tồn tại: " + uid));

        WorkflowStatus currentStatus = existing.getStatus();
        WorkflowStatus newStatus;
        try {
            newStatus = WorkflowStatus.valueOf(newStatusStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái không hợp lệ: " + newStatusStr);
        }

        // --- Kiểm tra Logic chuyển đổi trạng thái (State Machine) ---
        if (currentStatus == newStatus) {
            return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
        }

        // Ví dụ logic: Đã DEPRECATED thì không được quay lại ACTIVE
        if (currentStatus == WorkflowStatus.DEPRECATED) {
            throw new IllegalStateException("Workflow đã DEPRECATED thì không thể thay đổi trạng thái khác.");
        }

        // Ví dụ logic: Phải từ DRAFT mới lên được ACTIVE
        if (newStatus == WorkflowStatus.ACTIVE && currentStatus != WorkflowStatus.DRAFT) {
            throw new IllegalStateException("Workflow khác DRAFT thì không thể thay đổi trạng thái khác.");
        }

        existing.setStatus(newStatus);
        workflowDefinitionRepo.save(existing);

        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }

    @PutMapping("/{uid}")
    @Transactional
    public ResponseEntity<BaseResponse<WorkflowDefinitionResponse>> updateWorkflow(
            @PathVariable String uid,
            @Valid @RequestBody CreateWorkflowRequest req) {

        // 1. Kiểm tra tồn tại
        WorkflowDefinition existing = workflowDefinitionRepo.findByUid(uid)
                .orElseThrow(() -> new IllegalArgumentException("Workflow không tồn tại: " + uid));

        // 2. Chặn cập nhật nếu workflow đã ở trạng thái DEPRECATED (tùy nghiệp vụ)
        if (WorkflowStatus.DEPRECATED.equals(existing.getStatus())) {
            throw new IllegalStateException("Không thể chỉnh sửa workflow đã bị ngưng hoạt động (DEPRECATED)");
        }

        // 3. Re-validate cấu trúc DAG mới
        List<String> errors = validateDag(req.getDefinition());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        // 4. Serialize definition
        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(req.getDefinition());
        } catch (Exception e) {
            throw new IllegalArgumentException("Lỗi serialize definition: " + e.getMessage());
        }

        // 5. Cập nhật các trường thông tin
        existing.setName(req.getName())
                .setTenantId(req.getTenantId())
                .setTimeout(req.getTimeout() != null ? req.getTimeout() : 3600L)
                .setDefinition(definitionJson)
                .setVersion(req.getVersion());

        WorkflowDefinition saved = workflowDefinitionRepo.save(existing);
        WorkflowDefinitionResponse response = WorkflowDefinitionResponse.of(saved, req.getDefinition());

        return ResponseEntity.ok(BaseResponse.ofSucceeded(response));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<BaseResponse<WorkflowDefinitionResponse>> createWorkflow(
            @Valid @RequestBody CreateWorkflowRequest req) {

        List<String> errors = validateDag(req.getDefinition());
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }

        String uid = UUID.randomUUID().toString();
        String definitionJson;
        try {
            definitionJson = objectMapper.writeValueAsString(req.getDefinition());
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize definition: " + e.getMessage());
        }

        WorkflowDefinition entity = new WorkflowDefinition()
                .setUid(uid)
                .setTenantId(req.getTenantId())
                .setName(req.getName())
                .setStatus(WorkflowStatus.DRAFT)
                .setTimeout(req.getTimeout() != null ? req.getTimeout() : 3600L)
                .setDefinition(definitionJson)
                .setVersion(req.getVersion());

        WorkflowDefinition saved = workflowDefinitionRepo.save(entity);

        WorkflowDefinitionResponse response = WorkflowDefinitionResponse.of(saved, req.getDefinition());

        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(response));
    }

    // ─── DAG Validation ───────────────────────────────────────────────────────

    private List<String> validateDag(CreateWorkflowRequest.DefinitionDto def) {
        List<String> errors = new ArrayList<>();

        // ── 1. Duplicate node ids ─────────────────────────────────────────────
        Set<String> nodeIds = new LinkedHashSet<>();
        Map<String, String> nodeTypeById = new HashMap<>();
        for (CreateWorkflowRequest.NodeDto node : def.getNodes()) {
            if (!nodeIds.add(node.getId())) {
                errors.add("Duplicate node id: '" + node.getId() + "'");
            }
            nodeTypeById.put(node.getId(), node.getType());
        }
        if (!errors.isEmpty()) return errors;

        // ── 2. startNode must exist ───────────────────────────────────────────
        if (!nodeIds.contains(def.getStartNode())) {
            errors.add("startNode '" + def.getStartNode() + "' not found in nodes");
        }

        // ── 3. Edge references and self-loops ─────────────────────────────────
        for (CreateWorkflowRequest.EdgeDto edge : def.getEdges()) {
            if (!nodeIds.contains(edge.getFrom()))
                errors.add("Edge references unknown source node: '" + edge.getFrom() + "'");
            if (!nodeIds.contains(edge.getTo()))
                errors.add("Edge references unknown target node: '" + edge.getTo() + "'");
            if (edge.getFrom().equals(edge.getTo()))
                errors.add("Self-loop not allowed on node: '" + edge.getFrom() + "'");
        }
        if (!errors.isEmpty()) return errors;

        // ── 4. Build degree maps ───────────────────────────────────────────────
        Map<String, Long> inDegree  = def.getEdges().stream()
                .collect(Collectors.groupingBy(CreateWorkflowRequest.EdgeDto::getTo,   Collectors.counting()));
        Map<String, Long> outDegree = def.getEdges().stream()
                .collect(Collectors.groupingBy(CreateWorkflowRequest.EdgeDto::getFrom, Collectors.counting()));

        // ── 5. Single entry point: exactly one node with no incoming edges ─────
        List<String> entryPoints = nodeIds.stream()
                .filter(id -> inDegree.getOrDefault(id, 0L) == 0)
                .collect(Collectors.toList());
        if (entryPoints.size() != 1) {
            errors.add("Workflow must have exactly 1 entry point (node with no incoming edges), found: " + entryPoints);
        } else if (!entryPoints.get(0).equals(def.getStartNode())) {
            errors.add("Entry point '" + entryPoints.get(0) + "' must equal startNode '" + def.getStartNode() + "'");
        }

        // ── 6. Dead-end: non-END / non-PARALLEL_FORK node must have outgoing edge
        for (CreateWorkflowRequest.NodeDto node : def.getNodes()) {
            String type = node.getType();
            if ("END".equals(type) || "PARALLEL_FORK".equals(type)) continue;
            if (outDegree.getOrDefault(node.getId(), 0L) == 0) {
                errors.add("Dead-end: node '" + node.getId() + "' (type=" + type + ") has no outgoing edges");
            }
        }

        // ── 7. END nodes must have no outgoing edges ───────────────────────────
        for (CreateWorkflowRequest.EdgeDto edge : def.getEdges()) {
            if ("END".equals(nodeTypeById.get(edge.getFrom()))) {
                errors.add("END node '" + edge.getFrom() + "' must not have outgoing edges");
            }
        }

        // ── 8. PARALLEL_FORK rules ─────────────────────────────────────────────
        for (CreateWorkflowRequest.NodeDto node : def.getNodes()) {
            if (!"PARALLEL_FORK".equals(node.getType())) continue;
            long out = outDegree.getOrDefault(node.getId(), 0L);
            if (out < 2) {
                errors.add("PARALLEL_FORK node '" + node.getId() + "' must have ≥2 outgoing edges (found " + out + ")");
            }
            // Outgoing edges must be unconditional
            def.getEdges().stream()
                    .filter(e -> node.getId().equals(e.getFrom()) && e.getCondition() != null)
                    .forEach(e -> errors.add("PARALLEL_FORK node '" + node.getId() + "' must have unconditional outgoing edges"));
        }

        // ── 9. PARALLEL_JOIN rules ─────────────────────────────────────────────
        for (CreateWorkflowRequest.NodeDto node : def.getNodes()) {
            if (!"PARALLEL_JOIN".equals(node.getType())) continue;
            long in  = inDegree.getOrDefault(node.getId(), 0L);
            long out = outDegree.getOrDefault(node.getId(), 0L);
            if (in < 2)  errors.add("PARALLEL_JOIN node '" + node.getId() + "' must have ≥2 incoming edges (found " + in + ")");
            if (out != 1) errors.add("PARALLEL_JOIN node '" + node.getId() + "' must have exactly 1 outgoing edge (found " + out + ")");
            if (node.getConfig() == null || !node.getConfig().containsKey("waitFor")) {
                errors.add("PARALLEL_JOIN node '" + node.getId() + "' must have config.waitFor");
            } else {
                List<?> waitFor = (List<?>) node.getConfig().get("waitFor");
                if (waitFor == null || waitFor.size() < 2) {
                    errors.add("PARALLEL_JOIN node '" + node.getId() + "' config.waitFor must list ≥2 node IDs");
                } else {
                    waitFor.stream()
                            .filter(ref -> !nodeIds.contains((String) ref))
                            .forEach(ref -> errors.add("PARALLEL_JOIN node '" + node.getId()
                                    + "' config.waitFor references unknown node: '" + ref + "'"));
                }
            }
        }
        if (!errors.isEmpty()) return errors;

        // ── 10. Reachability: all nodes reachable from startNode ───────────────
        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(def.getStartNode());
        Map<String, List<String>> adj = new HashMap<>();
        nodeIds.forEach(id -> adj.put(id, new ArrayList<>()));
        def.getEdges().forEach(e -> adj.get(e.getFrom()).add(e.getTo()));
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (reachable.add(cur)) {
                adj.getOrDefault(cur, Collections.emptyList()).forEach(queue::add);
            }
        }
        nodeIds.stream()
                .filter(id -> !reachable.contains(id))
                .forEach(id -> errors.add("Node '" + id + "' is not reachable from startNode '" + def.getStartNode() + "'"));
        if (!errors.isEmpty()) return errors;

        // ── 11. Cycle detection (DFS) ──────────────────────────────────────────
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        for (String nodeId : nodeIds) {
            if (!visited.contains(nodeId)) {
                List<String> cycle = new ArrayList<>();
                if (dfsCycleDetect(nodeId, adj, visited, inStack, cycle)) {
                    errors.add("Cycle detected: " + cycle);
                    break;
                }
            }
        }

        return errors;
    }

    private boolean dfsCycleDetect(String node, Map<String, List<String>> adj,
                                    Set<String> visited, Set<String> inStack, List<String> cycle) {
        visited.add(node);
        inStack.add(node);
        for (String neighbor : adj.getOrDefault(node, Collections.emptyList())) {
            if (!visited.contains(neighbor)) {
                if (dfsCycleDetect(neighbor, adj, visited, inStack, cycle)) {
                    cycle.add(0, node);
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                cycle.add(neighbor);
                cycle.add(0, node);
                return true;
            }
        }
        inStack.remove(node);
        return false;
    }
}
