# ONSure OpenAI Responses provider

This isolated candidate implements `onsure-provider-spi` against the OpenAI Responses API.
It sends no request until network egress, customer-data transfer and a request cost ceiling are
explicitly approved. It performs one attempt against the exact configured model; retry and model
fallback decisions remain with the caller.

Runtime configuration is read by `OpenAiResponsesProvider.fromEnvironment()`:

- `OPENAI_API_KEY` (required, secret; never commit it)
- `ONSURE_OPENAI_MODEL` (default `gpt-5.6-sol`)
- `ONSURE_OPENAI_INPUT_MICROS_PER_MILLION_TOKENS` (required)
- `ONSURE_OPENAI_OUTPUT_MICROS_PER_MILLION_TOKENS` (required)

Pricing is deliberately operator-supplied because it changes independently of source releases.
Health inspection is configuration-only and never probes the remote API.

The JAR main class accepts one bounded JSON request file. Approval booleans and the maximum cost
must be present under `policy`; the API key is accepted only through the environment. Live invocation
is an operator action and is not performed by repository tests.

`config/provider/openai-request.example.json` is deliberately denied by default. Copy it outside the
repository, review/redact the content, and only then set approvals and a cost ceiling. The CLI command
uses the module JAR plus its runtime dependencies; the RHEL package places both under `/opt/onsure`.
