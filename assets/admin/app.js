(() => {
  "use strict";

  let bearerToken = "";
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
    setText("chain-head", compactDigest(metrics.chain_head_sha256));
    setText("content-storage", metrics.prompt_or_completion_content_recorded ? "ON" : "OFF");
    setText("improvement-count", `개선 후보 ${formatNumber(data.improvement_candidate_count)}`);
    renderPrograms(Array.isArray(data.programs) ? data.programs : []);
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

  async function loadOverview() {
    if (!bearerToken) return;
    byId("refresh").disabled = true;
    byId("auth-error").textContent = "";
    try {
      const response = await fetch("/v1/management-overview", {
        method: "GET",
        headers: { "Authorization": `Bearer ${bearerToken}`, "Accept": "application/json" },
        cache: "no-store",
        credentials: "omit"
      });
      if (!response.ok) throw new Error(response.status === 401 ? "토큰이 올바르지 않습니다." : `조회 실패 (${response.status})`);
      render(await response.json());
    } catch (error) {
      setStatus(byId("connection-state"), "UNAVAILABLE");
      byId("auth-error").textContent = error instanceof Error ? error.message : "조회에 실패했습니다.";
      if (byId("dashboard").hidden) byId("auth-panel").hidden = false;
    } finally {
      byId("refresh").disabled = !bearerToken;
    }
  }

  byId("auth-form").addEventListener("submit", (event) => {
    event.preventDefault();
    const input = byId("token");
    bearerToken = input.value;
    input.value = "";
    loadOverview();
  });
  byId("refresh").addEventListener("click", loadOverview);
  byId("disconnect").addEventListener("click", () => {
    bearerToken = "";
    byId("dashboard").hidden = true;
    byId("auth-panel").hidden = false;
    byId("refresh").disabled = true;
    setStatus(byId("connection-state"), "인증 필요");
    byId("token").focus();
  });
})();
