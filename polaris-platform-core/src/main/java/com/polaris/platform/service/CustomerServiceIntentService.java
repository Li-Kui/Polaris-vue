package com.polaris.platform.service;

import com.polaris.ai.customer.application.CustomerServiceAiGateway.*;
import com.polaris.common.exception.ServiceException;
import com.polaris.platform.domain.CustomerServiceTurn;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;
import com.polaris.platform.service.CustomerServiceOperationService.InputIssue;
import com.polaris.platform.service.CustomerServiceOperationService.PreparedInput;

/** 模型只给出候选选择；补参状态、展示和显式执行都由服务端重新核验。 */
@Service
public class CustomerServiceIntentService {
    private final CustomerServiceOperationService operations;
    private final ObjectMapper json;

    public CustomerServiceIntentService(CustomerServiceOperationService operations, ObjectMapper json) {
        this.operations = operations;
        this.json = json;
    }

    public List<RouteCapability> options(String sessionId) {
        return operations.capabilities(sessionId).stream().filter(CustomerServiceOperationService.CapabilityView::available)
                .map(item -> new RouteCapability(item.code(), item.name(), item.description(), item.inputs().stream()
                        .map(this::routeParameter).toList())).toList();
    }

    private RouteParameter routeParameter(CustomerServiceCapabilityService.InputParameter input) {
        var constraints = input.constraints();
        if (constraints != null && constraints.path("enum").isArray() && constraints.path("enum").size() > 5) {
            var projected = json.createObjectNode();
            constraints.properties().forEach(entry -> { if (!"enum".equals(entry.getKey())) projected.set(entry.getKey(), entry.getValue()); });
            var preview = projected.putArray("enumPreview");
            for (int index = 0; index < 5; index++) preview.add(constraints.path("enum").get(index));
            projected.put("enumCount", constraints.path("enum").size());
            constraints = projected;
        }
        return new RouteParameter(input.name(), input.type(), input.required(), input.label(), input.description(), input.example(), constraints);
    }

    public RoutePending pending(String sessionId, List<CustomerServiceTurn> turns, int turnNo, List<RouteCapability> options) {
        var previous = turns.stream().filter(turn -> turn.getTurnNo() < turnNo).max(java.util.Comparator.comparing(CustomerServiceTurn::getTurnNo)).orElse(null);
        if (previous == null || !"COMPLETED".equals(previous.getStatus()) || previous.getIntentJson() == null) return null;
        var stored = decode(previous);
        if (!Set.of("COLLECTING", "READY").contains(stored.state())
                || options.stream().noneMatch(item -> item.code().equals(stored.capabilityCode()))
                || operations.find(sessionId, operationRequest(previous)) != null) return null;
        var checked = checked(sessionId, stored.capabilityCode(), stored.input());
        return new RoutePending(checked.capabilityCode(), checked.input().deepCopy(), checked.issues().stream()
                .map(issue -> new RouteIssue(issue.name(), issue.label(), issue.message())).toList());
    }

    public Intent decide(String sessionId, RouteDecision decision, List<RouteCapability> options, RoutePending pending) {
        if (decision == null || decision.kind() == null || decision.input() == null || !decision.input().isObject()) throw invalid();
        if (!"CAPABILITY".equals(decision.kind())) {
            if (!Set.of("KNOWLEDGE", "CLARIFY", "CANCEL").contains(decision.kind())
                    || decision.capabilityCode() != null || !decision.input().isEmpty()) throw invalid();
            return new Intent(decision.kind(), null, json.createObjectNode(), List.of(), List.of());
        }
        if (options.stream().noneMatch(item -> item.code().equals(decision.capabilityCode()))) throw invalid();
        // 先单独检查新增字段，禁止用合并过程掩盖未声明字段、身份值或类型错误。
        prepare(sessionId, decision.capabilityCode(), decision.input());
        var merged = json.createObjectNode();
        if (pending != null && pending.capabilityCode().equals(decision.capabilityCode())) {
            pending.input().properties().forEach(item -> merged.set(item.getKey(), item.getValue().deepCopy()));
        }
        decision.input().properties().forEach(item -> merged.set(item.getKey(), item.getValue().deepCopy()));
        return checked(sessionId, decision.capabilityCode(), merged);
    }

    public Intent revalidate(String sessionId, Intent stored) {
        if (Set.of("COLLECTING", "READY").contains(stored.state())) return checked(sessionId, stored.capabilityCode(), stored.input());
        return stored;
    }

    public String answer(String sessionId, Intent intent, List<RouteCapability> options) {
        return switch (intent.state()) {
            case "COLLECTING" -> collecting(sessionId, intent);
            case "READY" -> "测试参数已收齐，请核对下方能力和参数，点击“运行此测试”后再启动任务。";
            case "CANCEL" -> "已取消尚未运行的补参。已受理任务请使用任务卡片的取消按钮核对取消结果。";
            case "CLARIFY" -> "请说明您想咨询的知识问题，或明确要测试的能力："
                    + String.join("、", options.stream().map(RouteCapability::name).toList()) + "。真实订单业务尚未接入。";
            default -> throw invalid();
        };
    }

    public IntentView view(String sessionId, CustomerServiceTurn turn) {
        if (turn.getIntentJson() == null || !"COMPLETED".equals(turn.getStatus())) return null;
        var stored = decode(turn);
        if (!Set.of("COLLECTING", "READY").contains(stored.state())) return null;
        var input = prepare(sessionId, stored.capabilityCode(), stored.input());
        return new IntentView(ready(input) ? "READY" : "COLLECTING", stored.capabilityCode(), input.name(),
                input.input(), input.missingFields(), operations.find(sessionId, operationRequest(turn)) != null, input.issues(), input.parameters());
    }

    public CustomerServiceOperationService.StartView start(String sessionId, CustomerServiceTurn turn) {
        var stored = decode(turn);
        if (!"READY".equals(stored.state())) throw invalid();
        var checked = checked(sessionId, stored.capabilityCode(), stored.input());
        if (!"READY".equals(checked.state())) return new CustomerServiceOperationService.StartView("INPUT_INVALID", checked.missingFields(), null,
                "INPUT_CONSTRAINT_FAILED", checked.issues());
        return operations.start(sessionId, operationRequest(turn), checked.capabilityCode(), checked.input());
    }

    public boolean started(String sessionId, CustomerServiceTurn turn) {
        return operations.find(sessionId, operationRequest(turn)) != null;
    }

    public boolean knowledge(CustomerServiceTurn turn) { return "KNOWLEDGE".equals(decode(turn).state()); }

    private Intent checked(String sessionId, String code, JsonNode input) {
        var prepared = prepare(sessionId, code, input);
        return new Intent(ready(prepared) ? "READY" : "COLLECTING", code, prepared.input(), prepared.missingFields(), prepared.issues());
    }

    private boolean ready(PreparedInput input) { return input.missingFields().isEmpty() && input.issues().isEmpty(); }

    private String collecting(String sessionId, Intent intent) {
        var prepared = prepare(sessionId, intent.capabilityCode(), intent.input());
        StringBuilder answer = new StringBuilder("准备测试“").append(prepared.name()).append("”。");
        if (!prepared.missingFields().isEmpty()) {
            var labels = prepared.missingFields().stream().map(name -> prepared.parameters().stream().filter(item -> item.name().equals(name))
                    .map(CustomerServiceCapabilityService.InputParameter::label).findFirst().orElse(name)).toList();
            answer.append("请补充参数：").append(String.join("、", labels)).append("。");
        }
        if (!prepared.issues().isEmpty()) answer.append("请修改参数：")
                .append(String.join("、", prepared.issues().stream().map(InputIssue::label).toList())).append("，按下方说明填写。");
        return answer.append("此时尚未运行任务。").toString();
    }

    private CustomerServiceOperationService.PreparedInput prepare(String sessionId, String code, JsonNode input) {
        try { return operations.prepare(sessionId, code, input); }
        catch (ServiceException invalid) {
            if (invalid.getMessage() != null && invalid.getMessage().startsWith("INVALID_INPUT:")) throw invalid();
            throw invalid;
        }
    }

    private Intent decode(CustomerServiceTurn turn) {
        try { return json.readValue(turn.getIntentJson(), Intent.class); }
        catch (RuntimeException malformed) { throw invalid(); }
    }

    private String operationRequest(CustomerServiceTurn turn) { return "dialog:" + turn.getId(); }
    private ServiceException invalid() { return new ServiceException("ROUTE_INVALID: 能力或参数未通过核验，请明确需求后重试", 409); }

    public record Intent(String state, String capabilityCode, JsonNode input, List<String> missingFields, List<InputIssue> issues) {
        public Intent { issues = issues == null ? List.of() : List.copyOf(issues); }
    }
    public record IntentView(String state, String capabilityCode, String name, JsonNode input, List<String> missingFields, boolean started,
                             List<InputIssue> issues, List<CustomerServiceCapabilityService.InputParameter> parameters) { }
}
