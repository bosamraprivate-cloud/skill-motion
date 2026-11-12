---
title: 'Story 1.4: Stateless verify proxy on Cloudflare Workers'
type: 'feature'
created: '2026-11-12'
status: 'done'
baseline_commit: '837b073e05f30fed890409b3eeeed4d7f827a65f'
route: 'dispatch'
review_loop_iteration: 0
context:
  - '{project-root}/_bmad-output/implementation-artifacts/epic-1-context.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** Object steps (cup, soap, zipper, tap) need a model verdict, but the DeepSeek key must never reach the tablet and no child data may be stored outside it.

**Approach:** A stateless Cloudflare Worker in TypeScript exposing `POST /verify` exactly per AD-7: shared-secret auth, size/image limits, one DeepSeek chat-completions call through the `openai` SDK (baseURL `https://api.deepseek.com`, model from `MODEL_ID`, default `deepseek-flash`, JSON output mode, 3 `image_url` data-URL parts built from the uploaded binary JPEGs), schema validation, and fixed error mapping. Fully tested with a mocked SDK. Deployment and secrets are a separate, user-run step.

## Boundaries & Constraints

**Always:**
- Contract (AD-7, updated 2026-11-12): `multipart/form-data` with text fields `requestId` (uuid), `stepId`, `question` and 1–3 binary JPEG file parts named `image`; body ≤ 3 MB; the Worker base64-encodes each image into a `data:image/jpeg;base64,` URL only for the DeepSeek call (no Files API: it stores files upstream); 200 → `{verdict: "done"|"not_done"|"cannot_tell", reason, modelId, promptVersion}`; 401 wrong/missing secret (DeepSeek not called); 413 >3 images or >3 MB; 400 malformed body or invalid requestId; 405 non-POST; 404 other paths; 502 only when the DeepSeek call itself fails (HTTP or network). Refusal, empty, unparseable or schema-invalid model output → 200 `cannot_tell`, reason `invalid_model_output`. Never `not_done` except from a valid model answer.
- `modelId` is the `model` field returned by DeepSeek; `promptVersion` is a constant exported from `prompt.ts` (AD-2), e.g. `"v1"`.
- Fixed system prompt in `prompt.ts`: judge only what is visible in the 3 frames (start, middle, end); answer only JSON `{"verdict","reason"}`; if unsure or the relevant hands/object are not visible, answer `cannot_tell`; reason ≤ 120 chars. The step question is the user text.
- Secret comparison is constant-time. Secrets only via `env.DEEPSEEK_API_KEY`, `env.SHARED_SECRET`; `MODEL_ID` is a plain var in `wrangler.toml`.
- No storage bindings (KV, D1, R2, Durable Objects, Queues) and no logging of bodies, images, questions or headers; `observability` may log status codes and latency only.
- Latest stable versions of `wrangler`, `typescript`, `openai`, `vitest`, `@cloudflare/vitest-pool-workers` (or plain vitest with mocks), pinned in `package-lock.json`.

**Never:** No deployment, no `wrangler secret put`, no real network calls in tests. No child identifiers in prompts. No retries that could double-bill (one upstream call per request).

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| No/wrong secret | Missing or wrong Bearer | 401, SDK not called | N/A |
| Too many images | 4 images | 413, SDK not called | N/A |
| Too large | Body > 3 MB (Content-Length or actual) | 413, SDK not called | N/A |
| Malformed | Not multipart, missing fields, bad uuid, zero images, non-JPEG part (magic bytes FF D8 FF) | 400 | N/A |
| Happy | Valid body, model returns `{"verdict":"done","reason":"..."}` | 200 with verdict, reason, modelId from response, promptVersion | N/A |
| Request shape | Valid body | SDK called once: baseURL DeepSeek, model `MODEL_ID` (default `deepseek-flash`), `response_format: json_object`, 3 `image_url` parts with `data:image/jpeg;base64,` | N/A |
| Invalid output | Model returns prose / bad JSON / verdict `"maybe"` / refusal / empty | 200 `cannot_tell`, `invalid_model_output` | N/A |
| Upstream fail | SDK throws APIError (any status) or network error | 502 | No body logging |
| Wrong method/path | GET /verify, POST /other | 405 / 404 | N/A |

</frozen-after-approval>

## Code Map

- Greenfield `proxy/` (Structural Seed: `src/index.ts`, `src/prompt.ts`, `test/`). Root `.gitignore` already ignores `node_modules/`, `.dev.vars`.
- DeepSeek docs confirmed 2026-11-12 (api-docs.deepseek.com, guides/vision): base URL `https://api.deepseek.com`; `deepseek-flash` (V4.1-Flash-1017) accepts images; inline `image_url` with base64 data URL; up to 600 images/request, 32 MiB per image, 48 MiB inline body, 8192 px per side; formats JPEG/PNG/GIF/WebP; `detail` param (`low` = 512×512, `auto` = original). JSON mode together with images is not documented — the Worker validates output regardless.

## Tasks & Acceptance

**Execution:**
- [x] `proxy/package.json`, `package-lock.json`, `tsconfig.json`, `wrangler.toml` -- scripts `test`, `build` (`wrangler deploy --dry-run --outdir dist`), `typecheck`; `MODEL_ID = "deepseek-flash"` var; no bindings -- setup
- [x] `proxy/src/prompt.ts` -- `PROMPT_VERSION`, system prompt, verdict schema validator -- AD-2
- [x] `proxy/src/index.ts` -- handler per contract and matrix; SDK client created per request from env -- AD-7
- [x] `proxy/test/index.test.ts` -- every matrix row with a mocked `openai` module -- verification
- [x] `proxy/README.md` -- local dev, deploy and secret commands for the user (no secret values) -- operations
- [x] `docs/designs/m1-spike-results.md` -- append confirmed DeepSeek endpoint, model ID and image limits (from Code Map) -- AC

**Acceptance Criteria:**
- Given `wrangler.toml`, when inspected, then there are no storage bindings and nothing logs bodies.
- Given `proxy/`, when `npm test`, `npm run build` and `npx tsc --noEmit` run, then all pass.

## Implementation Notes

- Node is on PATH; Windows. Do not commit (orchestrator commits). Do not run `wrangler deploy` or `wrangler secret put`.

## Spec Change Log

## Review Triage Log

| # | Finding | Verdict | Route | Evidence |
|---|---|---|---|---|
| 1 | No upstream timeout (SDK default ~10 min) | high | patch | client built without timeout |
| 2 | Missing API key → uncaught 500 | medium | patch | constructor outside try |
| 3 | Client disconnect → unhandled rejection 500 | medium | patch | readLimited has no catch |
| 4 | question/stepId uncapped | medium | patch | cost and injection surface |
| 5 | Prompt says 3 frames, 1–3 accepted | medium | patch | SYSTEM_PROMPT text |
| 6 | Reason length in UTF-16; fenced JSON rejected | low | patch | prompt.ts validator |
| 7 | Multi-chunk base64 untested | medium | patch | only 16-byte images in tests |
| 8 | Workers Logs record request metadata incl. headers | medium | patch | observability enabled, sampling 1 |
| 9 | README build comment mismatch | low | patch | script has --outdir dist |
| 10 | json_object + images undocumented | maybe-false | verify-on-deploy | live call decides; fallback: drop response_format |
| 11 | .dev.vars not ignored | false | rejected | root .gitignore `.dev.vars` matches proxy/.dev.vars |
| 12 | package-lock.json missing | false | rejected | file exists; excluded from review diff by the orchestrator |
| 13 | requestId/stepId not echoed | low | rejected | AD-7 response shape is fixed; app pairs by request |
| 14 | Spike section lacks source URL / modelId alias note | low | patch-by-orchestrator | add URLs and note at deploy |

## Verification

**Commands:**
- `npm ci && npm test && npm run build && npx tsc --noEmit` (in `proxy/`) -- expected: all succeed
