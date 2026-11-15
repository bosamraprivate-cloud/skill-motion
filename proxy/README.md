# verify-proxy

Stateless Cloudflare Worker that judges object steps (AD-7). `POST /verify` takes `multipart/form-data` (`requestId`, `stepId`, `question`, 1-3 JPEG parts named `image`, at most 3 MB) with `Authorization: Bearer <SHARED_SECRET>`, calls DeepSeek once through the `openai` SDK, and returns `{verdict, reason, modelId, promptVersion}`. It has no storage bindings, logs nothing, and has Workers observability disabled so no request metadata is stored. The DeepSeek call times out after 7.5 s (502). `stepId` must match `^[a-z0-9]+(\.[a-z0-9]+)*$` (max 64 chars) and `question` is at most 500 chars.

| Status | Meaning |
|---|---|
| 200 | Verdict. Invalid or refused model output becomes `cannot_tell` / `invalid_model_output`. |
| 400 | Malformed body or invalid `requestId` |
| 401 | Missing or wrong secret |
| 404 / 405 | Other path / non-POST |
| 413 | More than 3 images or more than 3 MB |
| 502 | The DeepSeek call failed |

## POST /scene (Story 6.4)

Scene check before a session: one photo of the scene. The child may be in it; the app blurs faces on device before sending, and the prompt tells the model faces are blurred on purpose and never to describe the person. `person` is accepted as a required kind and gets a box like any object. Objects partly hidden by the child are still located (visible extent). Same auth (`Authorization: Bearer <SHARED_SECRET>`), same 3 MB limit, same timeout and model as `/verify`.

Request `multipart/form-data`:

| Field | Value |
|---|---|
| `requestId` | UUID |
| `kinds` | Comma-separated required object kinds from the catalogue, e.g. `table,chair,cup`; each `^[a-z][a-z0-9_]*$`, max 32 chars, 1-12 kinds (duplicates ignored) |
| `locale` | e.g. `ar-EG` (`^[a-z]{2}(-[A-Za-z]{2})?$`) |
| `image` | Exactly one JPEG part |

Response 200:

```json
{"ok": true,
 "objects": [{"kind": "table", "box": {"x": 0.1, "y": 0.5, "w": 0.6, "h": 0.4}, "confidence": 0.9}],
 "issues": [{"code": "partly_visible", "kind": "chair"}],
 "modelId": "deepseek-flash", "promptVersion": "scene-v2"}
```

- Boxes are normalized to the image (x,y top-left), clamped to 0..1 and kept inside the frame; at most one object per kind (highest confidence), only required kinds.
- Issue codes (fixed): `missing_object`, `partly_visible`, `too_dark`, `person_in_view`, `camera_tilted`, `too_far`, `too_close`, `unclear`. Unknown codes are dropped; `kind` only appears when it is a required kind.
- A required kind missing or below confidence `SCENE_MIN_CONFIDENCE` (0.6) adds `missing_object` with that kind.
- `partly_visible` means the part needed for the task is hidden or cut off, so it blocks. `person_in_view` is still accepted in the code list but is non-blocking (informational only).
- `ok` is true only when every required kind is found at that confidence and no issue other than `person_in_view` remains.
- Invalid or refused model output: `{"ok": false, "objects": [], "issues": [{"code": "unclear"}], ...}`.
- Errors as for `/verify`: 400 malformed body / invalid `requestId`, 401, 413 (over 3 MB or more than one image), 502 upstream failure.

Changing the scene prompt or contract bumps `SCENE_PROMPT_VERSION` in `src/scene.ts`.

## Verify locally

```sh
npm ci
npm test
npm run build      # wrangler deploy --dry-run --outdir dist
npx tsc --noEmit
```

## Local dev

Create `proxy/.dev.vars` (git-ignored) with `DEEPSEEK_API_KEY=...` and `SHARED_SECRET=...`, then run `npm run dev`.

## Deploy (run by the project owner)

```sh
npx wrangler login
npx wrangler secret put DEEPSEEK_API_KEY
npx wrangler secret put SHARED_SECRET
npx wrangler deploy
```

Set a monthly spend limit in the DeepSeek console. `MODEL_ID` is a plain var in `wrangler.toml` (default `deepseek-flash`). Changing the prompt or contract bumps `PROMPT_VERSION` in `src/prompt.ts`.
