(() => {
  "use strict";

  let bearerToken = "";
  let sessionRole = "";
  const byId = (id) => document.getElementById(id);
  const number = new Intl.NumberFormat("ko-KR");

  const setText = (id, value) => { byId(id).textContent = String(value ?? "—"); };
  const formatNumber = (value) => number.format(Number(value || 0));
  const compactDigest = (value) => value && value !== "NOT_RUN"
    ? `${value.slice(0, 10)}…${value.slice(-8)}` : "NOT_RUN";

  function setStatus(element, value) {
    const normalized = String(value || "UNVERIFIED").toUpperCase();
    element.textContent = normalized;
    element.className = "status " + (
      ["RUNNING", "READY", "AVAILABLE", "PASS", "VALID"].includes(normalized) ? "good" :
      ["FAIL", "FAILED", "UNAVAILABLE", "INVALID"].includes(normalized) ? "bad" :
      ["BLOCKED", "HOLD", "DEGRADED"].includes(normalized) ? "warn" : "neutral"
    );
  }

  function renderPrograms(programs) {
    const body = byId("program-rows");
    body.replaceChildren();
    byId("program-empty").hidden = programs.length !== 0;
    for (const program of programs) {
      const validation = program.latest_validation || {};
      const row = document.createElement("tr");
      const identity = document.createElement("td");
      const name = document.createElement("strong");
      name.textContent = program.program_name || program.program_id;
      const id = document.createElement("small");
      id.textContent = `${program.project_id} / ${program.program_id}`;
      identity.append(name, id);
      row.append(identity);
      row.append(cell(program.program_type || "UNVERIFIED"));

      const decision = document.createElement("td");
      const badge = document.createElement("span");
      setStatus(badge, validation.decision || program.validation_state);
      decision.append(badge);
      const time = document.createElement("small");
      time.textContent = validation.generated_at || "실행 이력 없음";
      decision.append(time);
      row.append(decision);
      row.append(cell(formatNumber(validation.finding_count)));
      row.append(cell(formatNumber(program.improvement_candidate_count)));
      row.append(cell(`${formatNumber(validation.evidence_count)}건`));
      const actions = document.createElement("td");
      const run = document.createElement("button");
      run.type = "button";
      run.className = "button secondary compact-button";
      run.textContent = "검증";
      run.disabled = !["ADMIN", "OPERATOR"].includes(sessionRole);
      run.addEventListener("click", () => validateProgram(program.project_id, program.program_id, run));
      const understand = document.createElement("button");
      understand.type = "button";
      understand.className = "button secondary compact-button";
      understand.textContent = "자동 이해";
      understand.disabled = !["ADMIN", "OPERATOR"].includes(sessionRole);
      understand.addEventListener("click", () => understandProgram(
        program.project_id, program.program_id, understand));
      actions.append(understand, run);
      row.append(actions);
      body.append(row);
    }
  }

  function renderScorecards(programs) {
    const root = byId("scorecard-list");
    root.replaceChildren();
    const scored = programs.filter((program) => program.latest_validation?.scorecard?.contract === "ONSURE_VALIDATION_SCORECARD_V1");
    byId("scorecard-empty").hidden = scored.length !== 0;
    for (const program of scored) {
      const validation = program.latest_validation;
      const score = validation.scorecard;
      const article = document.createElement("article");
      article.className = "trust-card";
      const header = document.createElement("div");
      header.className = "trust-card-header";
      const title = document.createElement("div");
      const heading = document.createElement("h3");
      heading.textContent = program.program_name || program.program_id;
      const provenance = document.createElement("small");
      provenance.textContent = `source ${compactDigest(validation.source_sha256)} · receipt ${compactDigest(validation.receipt_sha256)}`;
      title.append(heading, provenance);
      const total = document.createElement("strong");
      total.className = "trust-total";
      total.textContent = `${score.earned_points ?? 0} / ${score.max_points ?? 100}`;
      header.append(title, total);
      article.append(header);
      const boundary = document.createElement("p");
      boundary.className = "trust-boundary";
      boundary.textContent = `${score.validation_outcome} · OTester ${score.trust_gate?.independent_otester || "NOT_RUN"} · OAudit ${score.trust_gate?.independent_oaudit || "NOT_RUN"} · Final claim DENIED`;
      article.append(boundary);
      article.append(scoreNodes("평가 영역", score.assessment_domains || []));
      article.append(scoreNodes("4차 검증 단계", score.phases || []));
      article.append(scoreNodes("세부 검사항목", score.assessment_areas || []));
      article.append(scoreNodes("최종 실행 Step", score.steps || []));
      const comparison = validation.comparison || {};
      const compare = document.createElement("p");
      compare.className = "comparison-line";
      compare.textContent = comparison.contract
        ? `이전 실행 대비 ${comparison.state}: ${comparison.total_delta_points >= 0 ? "+" : ""}${comparison.total_delta_points}점 · 개선 ${comparison.improved_node_count} · 퇴보 ${comparison.regressed_node_count}`
        : "비교 기준 실행 없음: 다음 재검증부터 Before/After를 표시합니다.";
      article.append(compare);
      root.append(article);
    }
  }

  function renderUnderstandingPortfolio(programs) {
    const root = byId("understanding-list");
    root.replaceChildren();
    const inferred = programs.filter((program) => program.program_understanding?.contract === "ONSURE_PROGRAM_UNDERSTANDING_CANDIDATE_V1");
    byId("understanding-empty").hidden = inferred.length !== 0;
    for (const program of inferred) root.append(understandingCard(program));
  }

  function understandingCard(program) {
    const name = program.program_name || program.program_id;
    const understanding = program.program_understanding;
    const article = document.createElement("article");
    article.className = "understanding-card";
    const heading = document.createElement("div");
    heading.className = "trust-card-header";
    const title = document.createElement("h3");
    title.textContent = name;
    const count = document.createElement("strong");
    count.className = "understanding-count";
    count.textContent = `${understanding.flow_candidate_count || 0} Flow`;
    heading.append(title, count);
    article.append(heading);
    const boundary = document.createElement("p");
    boundary.className = "trust-boundary";
    boundary.textContent = `추론=${understanding.inference_method} · 실행=${understanding.automatic_execution} · PASS 증적=${understanding.inferences_are_pass_evidence}`;
    article.append(boundary);
    const risks = understanding.risk_flags || [];
    if (risks.length) {
      const risk = document.createElement("p");
      risk.className = "inference-risk";
      risk.textContent = `자동실행 차단 위험: ${risks.join(" · ")}`;
      article.append(risk);
    }
    for (const lifecycle of understanding.api_lifecycle_candidates || []) {
      const lifecycleBox = document.createElement("div");
      lifecycleBox.className = "lifecycle-candidate";
      const lifecycleTitle = document.createElement("strong");
      lifecycleTitle.textContent = `${lifecycle.business_object} 생명주기 · ${lifecycle.coverage_state}`;
      const actions = document.createElement("p");
      actions.textContent = `API 연결 후보: ${(lifecycle.actions || []).join(" → ") || "미분류"} · ${lifecycle.execution_state}`;
      lifecycleBox.append(lifecycleTitle, actions);
      article.append(lifecycleBox);
    }
    for (const flow of understanding.flow_candidates || []) {
      const details = document.createElement("details");
      details.className = "score-details";
      const summary = document.createElement("summary");
      summary.textContent = `${flow.name} · 신뢰도 ${flow.inference_confidence} · ${flow.semantic_state}`;
      const body = document.createElement("div");
      body.className = "inference-body";
      const actor = document.createElement("p");
      actor.textContent = `Actor: ${flow.inferred_actor} · 업무 객체: ${flow.inferred_business_object}`;
      const stages = document.createElement("p");
      stages.textContent = `제안 Flow: ${(flow.stages || []).join(" → ") || "미분류"}`;
      body.append(actor, stages);
      if (flow.operation) {
        const operation = document.createElement("p");
        operation.textContent = `API: ${flow.operation.http_method || "?"} ${flow.operation.http_path || "?"} · ${flow.operation.lifecycle_action || "INVOKE"}`;
        body.append(operation);
      }
      details.append(summary, body);
      article.append(details);
    }
    const questions = document.createElement("div");
    questions.className = "question-list";
    const qtitle = document.createElement("strong");
    qtitle.textContent = "실행 전 최소 확인";
    questions.append(qtitle);
    const answerControls = [];
    for (const question of understanding.minimal_questions || []) {
      const item = document.createElement("div");
      item.className = "question-answer";
      const prompt = document.createElement("p");
      prompt.textContent = `${question.question_id}: ${question.prompt}`;
      const state = document.createElement("select");
      for (const value of ["UNAVAILABLE", "CONFIRMED", "REJECTED"]) {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        state.append(option);
      }
      const reference = document.createElement("input");
      reference.placeholder = "증적 참조 ID (예: env:ONSURE_TARGET_BASE_URL)";
      reference.pattern = "[A-Za-z0-9._:/-]{1,256}";
      reference.maxLength = 256;
      reference.autocomplete = "off";
      item.append(prompt, state, reference);
      questions.append(item);
      answerControls.push({questionId: question.question_id, state, reference});
    }
    if (answerControls.length) {
      const submit = document.createElement("button");
      submit.type = "button";
      submit.className = "button secondary compact-button";
      submit.textContent = "답변을 검토 초안에 결속";
      submit.disabled = !["ADMIN", "OPERATOR"].includes(sessionRole);
      submit.addEventListener("click", () => reviewProgramUnderstanding(
        program, understanding, answerControls, submit));
      questions.append(submit);
    }
    if (understanding.review) {
      const review = document.createElement("p");
      review.className = "review-state";
      review.textContent = `검토=${understanding.review.review_state} · 승인=${understanding.review.approval_state} · 실행=${understanding.review.execution_state} · 미해결 ${(understanding.review.unresolved_blocking_questions || []).length}`;
      questions.append(review);
      if (understanding.review.review_state === "READY_FOR_SEPARATE_APPROVAL") {
        const requestApproval = document.createElement("button");
        requestApproval.type = "button";
        requestApproval.className = "button secondary compact-button";
        requestApproval.textContent = "10분 유효 별도 승인 요청";
        requestApproval.disabled = !["ADMIN", "OPERATOR"].includes(sessionRole);
        requestApproval.addEventListener("click", () => requestProgramUnderstandingApproval(
          program, understanding, requestApproval));
        questions.append(requestApproval);
      }
    }
    article.append(questions);
    return article;
  }

  function scoreNodes(label, nodes) {
    const details = document.createElement("details");
    details.className = "score-details";
    const summary = document.createElement("summary");
    summary.textContent = `${label} (${nodes.length})`;
    details.append(summary);
    for (const node of nodes) {
      const row = document.createElement("div");
      row.className = "score-node";
      const identity = document.createElement("strong");
      identity.textContent = node.area_id || node.phase || node.group || node.step_id || "UNVERIFIED";
      const points = document.createElement("span");
      points.textContent = `${node.earned_points ?? 0} / ${node.possible_points ?? 0} · ${node.outcome || "NOT_RUN"}`;
      const diagnosis = document.createElement("p");
      diagnosis.textContent = node.diagnosis || "진단 정보 없음";
      const guide = document.createElement("p");
      guide.className = "improvement-guide";
      guide.textContent = `개선: ${node.improvement_guide || "추가 증적이 필요합니다."}`;
      row.append(identity, points, diagnosis, guide);
      details.append(row);
    }
    return details;
  }

  function renderGatewayRequests(data) {
    const body = byId("gateway-request-rows");
    const requests = Array.isArray(data.requests) ? data.requests : [];
    body.replaceChildren();
    byId("gateway-request-empty").hidden = requests.length !== 0;
    const current = data.current || {};
    if (current.model) byId("gateway-model").value = current.model;
    if (current.provider) byId("gateway-provider").value = current.provider;
    byId("gateway-rate").value = current.requests_per_second ?? 20;
    byId("gateway-cost").value = current.cost_per_token_micros ?? 0;
    for (const item of requests) {
      const row = document.createElement("tr");
      row.append(cell(compactDigest(item.request_sha256)));
      const change = item.change || {};
      row.append(cell(`${change.provider || "—"} / ${change.model || "—"}`));
      const state = document.createElement("td");
      const badge = document.createElement("span");
      setStatus(badge, item.state);
      state.append(badge);
      if (sessionRole === "APPROVER" && item.state === "AWAITING_APPROVAL") {
        const approve = document.createElement("button");
        approve.type = "button";
        approve.className = "text-button inline-action";
        approve.textContent = "승인";
        approve.addEventListener("click", () => decideGateway(item.request_id, "APPROVE", approve));
        const reject = document.createElement("button");
        reject.type = "button";
        reject.className = "text-button inline-action danger-text";
        reject.textContent = "거절";
        reject.addEventListener("click", () => decideGateway(item.request_id, "REJECT", reject));
        state.append(approve, reject);
      }
      row.append(state);
      row.append(cell(item.requested_by || "—"));
      body.append(row);
    }
  }

  function renderAudit(data) {
    const body = byId("audit-rows");
    const events = Array.isArray(data.events) ? data.events : [];
    body.replaceChildren();
    byId("audit-empty").hidden = events.length !== 0;
    setStatus(byId("audit-chain"), data.chain_valid ? "VALID" : "INVALID");
    for (const event of events) {
      const row = document.createElement("tr");
      row.append(cell(event.observed_at || "—"));
      row.append(cell(`${event.actor || "—"} / ${event.role || "—"}`));
      row.append(cell(event.action || "—"));
      row.append(cell(event.outcome || "—"));
      body.append(row);
    }
  }

  function cell(value) {
    const item = document.createElement("td");
    item.textContent = value;
    return item;
  }

  function render(data) {
    const gateway = data.gateway || {};
    const settings = gateway.settings || {};
    const metrics = gateway.metrics || {};
    const assurance = data.assurance || {};
    setText("program-count", formatNumber(data.program_count));
    setText("validated-count", formatNumber(data.validated_program_count));
    setText("token-count", formatNumber(metrics.total_tokens));
    setText("cost-count", `${formatNumber(metrics.actual_cost_micros)} µ`);
    setStatus(byId("gateway-state"), gateway.state);
    setText("provider", settings.provider);
    setText("model", settings.model);
    setText("binding", `${settings.binding || "127.0.0.1"}:${settings.port || "—"}`);
    setText("credential", settings.credential_configured ? "CONFIGURED" : "NOT_CONFIGURED");
    setText("network-policy", settings.network_egress_default_approved ? "APPROVED" : "DENY");
    setText("fallback", settings.fallback_enabled ? "ENABLED" : "DISABLED");
    setStatus(byId("chain-state"), metrics.chain_valid ? "VALID" : metrics.state || "NOT_RUN");
    setText("request-result", `${formatNumber(metrics.success_count)} / ${formatNumber(metrics.failure_count)}`);
    setText("token-split", `${formatNumber(metrics.input_tokens)} / ${formatNumber(metrics.output_tokens)}`);
    setText("duration", `${formatNumber(metrics.total_duration_millis)} ms`);
    setText("average-retry", `${formatNumber(metrics.average_duration_millis)} ms / ${formatNumber(metrics.retryable_failure_count)}`);
    setText("ledger-size", `${formatNumber(metrics.ledger_bytes)} / ${formatNumber(metrics.last_sequence)}`);
    setText("chain-head", compactDigest(metrics.chain_head_sha256));
    setText("content-storage", metrics.prompt_or_completion_content_recorded ? "ON" : "OFF");
    setText("improvement-count", `개선 후보 ${formatNumber(data.improvement_candidate_count)}`);
    const programs = Array.isArray(data.programs) ? data.programs : [];
    renderPrograms(programs);
    renderScorecards(programs);
    renderUnderstandingPortfolio(programs);
    setText("self-validation", assurance.self_validation);
    setText("otester", assurance.independent_otester);
    setText("oaudit", assurance.independent_oaudit);
    setText("production-go", assurance.production_go ? "GO" : "NOT_GRANTED");
    setText("generated-at", `마지막 조회: ${data.generated_at || "—"}`);
    byId("dashboard").hidden = false;
    byId("auth-panel").hidden = true;
    byId("refresh").disabled = false;
    setStatus(byId("connection-state"), "RUNNING");
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: {
        "Authorization": `Bearer ${bearerToken}`,
        "Accept": "application/json",
        ...(options.body ? {"Content-Type": "application/json"} : {}),
        ...(options.headers || {})
      },
      cache: "no-store",
      credentials: "omit"
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.message || `요청 실패 (${response.status})`);
    return body;
  }

  async function loadOverview() {
    if (!bearerToken) return;
    byId("refresh").disabled = true;
    byId("auth-error").textContent = "";
    try {
      const session = await api("/v1/session");
      sessionRole = session.role || "";
      byId("session-identity").textContent = `${session.actor || "—"} / ${sessionRole || "—"}`;
      const [overview, settings, approvals, audit] = await Promise.all([
        api("/v1/management-overview"), api("/v1/gateway-settings/requests"),
        api("/v1/programs/understand/approval-requests"), api("/v1/audit-events")
      ]);
      render(overview);
      renderGatewayRequests(settings);
      renderProgramApprovalRequests(approvals);
      renderAudit(audit);
      byId("program-form").querySelector("button").disabled = !["ADMIN", "OPERATOR"].includes(sessionRole);
      byId("gateway-form").querySelector("button").disabled = sessionRole !== "ADMIN";
    } catch (error) {
      setStatus(byId("connection-state"), "UNAVAILABLE");
      byId("auth-error").textContent = error instanceof Error ? error.message : "조회에 실패했습니다.";
      if (byId("dashboard").hidden) byId("auth-panel").hidden = false;
    } finally {
      byId("refresh").disabled = !bearerToken;
    }
  }

  async function validateProgram(projectId, targetId, button) {
    button.disabled = true;
    setText("program-action-state", "격리 snapshot 검증 실행 중…");
    try {
      const request = {
        project_id: projectId, target_id: targetId, profile: byId("validation-profile").value
      };
      const environmentProfile = byId("environment-profile").value.trim();
      if (request.profile === "UNIVERSAL" && environmentProfile) {
        request.environment_profile_file = environmentProfile;
      }
      const executionProfile = byId("execution-profile").value.trim();
      if (request.profile === "UNIVERSAL" && executionProfile) {
        request.execution_profile_file = executionProfile;
      }
      const result = await api("/v1/programs/validate", {method: "POST", body: JSON.stringify({
        ...request
      })});
      setText("program-action-state", `${result.profile || request.profile} / ${result.decision} / finding ${result.finding_count} / source mutation ${result.source_mutation_detected}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "검증 실패");
    } finally { button.disabled = false; }
  }

  async function understandProgram(projectId, targetId, button) {
    button.disabled = true;
    setText("program-action-state", "업무 Flow와 E2E 계획 후보 추론 중…");
    try {
      const result = await api("/v1/programs/understand", {method: "POST", body: JSON.stringify({
        project_id: projectId, target_id: targetId
      })});
      const understanding = result.program_understanding || {};
      setText("program-action-state", `Flow 후보 ${understanding.flow_candidate_count || 0} · 질문 ${(understanding.minimal_questions || []).length} · 실행 ${result.automatic_execution}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "자동 이해 실패");
    } finally { button.disabled = false; }
  }

  async function reviewProgramUnderstanding(program, understanding, controls, button) {
    button.disabled = true;
    setText("program-action-state", "답변을 소스·추론 digest에 결속 중…");
    try {
      const answers = controls.map((control) => {
        const answer = {question_id: control.questionId, answer_state: control.state.value};
        const reference = control.reference.value.trim();
        if (reference) answer.evidence_reference_id = reference;
        return answer;
      });
      const result = await api("/v1/programs/understand/reviews", {
        method: "POST",
        body: JSON.stringify({
          project_id: program.project_id,
          target_id: program.program_id,
          profile_file_sha256: understanding.profile_file_sha256,
          answers
        })
      });
      setText("program-action-state", `검토 ${result.review_state} · 미해결 ${(result.unresolved_blocking_questions || []).length} · 승인 ${result.approval_state} · 실행 ${result.execution_state}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "추론 검토 저장 실패");
    } finally { button.disabled = false; }
  }

  async function requestProgramUnderstandingApproval(program, understanding, button) {
    button.disabled = true;
    try {
      const result = await api("/v1/programs/understand/approval-requests", {
        method: "POST", body: JSON.stringify({
          project_id: program.project_id, target_id: program.program_id,
          profile_file_sha256: understanding.profile_file_sha256,
          review_sha256: understanding.review.review_sha256,
          reason: "관리화면에서 격리 합성 실행 초안 승인 요청", ttl_seconds: 600
        })
      });
      setText("program-action-state", `승인 요청 ${result.request_id} · 만료 ${result.expires_at} · 실행 ${result.execution_state}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "승인 요청 실패");
    } finally { button.disabled = false; }
  }

  function renderProgramApprovalRequests(payload) {
    const body = byId("program-approval-rows");
    body.replaceChildren();
    const requests = payload.requests || [];
    byId("program-approval-empty").hidden = requests.length !== 0;
    for (const request of requests) {
      const row = document.createElement("tr");
      row.append(cell(request.request_id), cell(`${request.project_id}/${request.target_id}`),
        cell(request.state), cell(request.expires_at), cell(request.execution_state));
      const actions = document.createElement("td");
      for (const decision of ["APPROVE", "REJECT"]) {
        const button = document.createElement("button");
        button.type = "button"; button.className = "button secondary compact-button";
        button.textContent = decision;
        button.disabled = sessionRole !== "APPROVER" || request.state !== "AWAITING_APPROVAL";
        button.addEventListener("click", () => decideProgramUnderstandingApproval(
          request.request_id, decision, button));
        actions.append(button);
      }
      const consume = document.createElement("button");
      consume.type = "button"; consume.className = "button secondary compact-button";
      consume.textContent = "1회 실행권한 생성";
      consume.disabled = !["ADMIN", "OPERATOR"].includes(sessionRole)
        || request.state !== "APPROVED_NOT_EXECUTED";
      consume.addEventListener("click", () => consumeProgramUnderstandingApproval(request, consume));
      actions.append(consume);
      const run = document.createElement("button");
      run.type = "button"; run.className = "button secondary compact-button";
      run.textContent = "합성 Loopback E2E 실행";
      run.disabled = !["ADMIN", "OPERATOR"].includes(sessionRole)
        || request.state !== "CONSUMED_FOR_EXECUTION_AUTHORIZATION"
        || request.execution_state !== "NOT_RUN";
      run.addEventListener("click", () => runInferredE2E(request, run));
      actions.append(run);
      row.append(actions); body.append(row);
    }
  }

  async function decideProgramUnderstandingApproval(requestId, decision, button) {
    button.disabled = true;
    try {
      const result = await api("/v1/programs/understand/approval-decisions", {
        method: "POST", body: JSON.stringify({request_id: requestId, decision,
          reason: "별도 승인자가 source/review digest와 격리 경계를 검토함"})
      });
      setText("program-action-state", `${result.state} · 실행 ${result.execution_state} · receipt ${result.receipt_sha256}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "승인 결정 실패");
    } finally { button.disabled = false; }
  }

  async function consumeProgramUnderstandingApproval(request, button) {
    button.disabled = true;
    try {
      const result = await api("/v1/programs/understand/approval-consumptions", {
        method: "POST", body: JSON.stringify({request_id: request.request_id,
          receipt_sha256: request.receipt_sha256,
          execution_scope: "ISOLATED_SYNTHETIC_LOOPBACK"})
      });
      setText("program-action-state", `1회 실행권한 ${result.execution_authorization_id} · 실제 실행 ${result.execution_state}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "승인 소비 실패");
    } finally { button.disabled = false; }
  }

  async function runInferredE2E(request, button) {
    button.disabled = true;
    try {
      const result = await api("/v1/programs/understand/inferred-e2e-runs", {
        method: "POST", body: JSON.stringify({
          execution_authorization_id: request.execution_authorization_id,
          execution_plan_sha256: request.execution_plan_sha256,
          base_url_reference_id: "env:ONSURE_INFERRED_E2E_BASE_URL"
        })
      });
      const schemaFailures = (result.steps || []).filter((step) =>
        (step.response_schema_errors || []).length > 0).length;
      setText("program-action-state", `E2E ${result.outcome} · 실행 ${result.executed_step_count}/${result.step_count} · Schema 오류 ${schemaFailures} · receipt ${result.runtime_receipt_sha256}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "Loopback E2E 실행 실패");
    } finally { button.disabled = false; }
  }

  async function decideGateway(requestId, decision, button) {
    button.disabled = true;
    try {
      await api("/v1/gateway-settings/approvals", {method: "POST", body: JSON.stringify({
        request_id: requestId, decision, reason: "관리화면에서 별도 승인자 검토"
      })});
      setText("gateway-action-state", `${requestId}: ${decision}`);
      await loadOverview();
    } catch (error) {
      setText("gateway-action-state", error instanceof Error ? error.message : "승인 처리 실패");
    } finally { button.disabled = false; }
  }

  byId("auth-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const input = byId("token");
    bearerToken = input.value;
    input.value = "";
    loadOverview();
  });
  byId("refresh").addEventListener("click", loadOverview);
  byId("program-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = event.currentTarget.querySelector("button");
    button.disabled = true;
    try {
      const result = await api("/v1/programs", {method: "POST", body: JSON.stringify({
        workspace_id: "local-runtime", workspace_name: "Local runtime",
        project_id: "onsure-validation", project_name: "ONSure validation",
        target_id: byId("program-id").value, target_name: byId("program-name").value,
        target_type: byId("program-type").value, source_root: byId("program-source").value
      })});
      setText("program-action-state", `${result.target_id} 등록 완료 / read-only ${result.read_only_registration}`);
      await loadOverview();
    } catch (error) {
      setText("program-action-state", error instanceof Error ? error.message : "등록 실패");
    } finally { button.disabled = false; }
  });
  byId("gateway-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const button = event.currentTarget.querySelector("button");
    button.disabled = true;
    try {
      const result = await api("/v1/gateway-settings/requests", {method: "POST", body: JSON.stringify({
        provider: byId("gateway-provider").value, model: byId("gateway-model").value,
        requests_per_second: Number(byId("gateway-rate").value),
        cost_per_token_micros: Number(byId("gateway-cost").value), reason: byId("gateway-reason").value
      })});
      setText("gateway-action-state", `${result.request_id}: ${result.state}`);
      byId("gateway-reason").value = "";
      await loadOverview();
    } catch (error) {
      setText("gateway-action-state", error instanceof Error ? error.message : "요청 실패");
    } finally { button.disabled = false; }
  });
  byId("disconnect").addEventListener("click", () => {
    bearerToken = "";
    sessionRole = "";
    byId("dashboard").hidden = true;
    byId("auth-panel").hidden = false;
    byId("refresh").disabled = true;
    setStatus(byId("connection-state"), "인증 필요");
    byId("token").focus();
  });
})();
