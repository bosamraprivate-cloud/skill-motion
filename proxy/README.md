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
