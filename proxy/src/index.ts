import OpenAI from "openai";
import { PROMPT_VERSION, SYSTEM_PROMPT, framesNote, parseModelVerdict } from "./prompt";
import {
  KIND_RE,
  LOCALE_RE,
  MAX_KIND_CHARS,
  MAX_SCENE_KINDS,
  SCENE_PROMPT_VERSION,
  SCENE_SYSTEM_PROMPT,
  UNCLEAR,
  parseSceneResult,
  sceneUserText,
} from "./scene";

export interface Env {
  DEEPSEEK_API_KEY: string;
  SHARED_SECRET: string;
  MODEL_ID?: string;
}

export const DEEPSEEK_BASE_URL = "https://api.deepseek.com";
export const DEFAULT_MODEL_ID = "deepseek-flash";
export const MAX_BODY_BYTES = 3 * 1024 * 1024;
export const MAX_IMAGES = 3;

export const MAX_QUESTION_CHARS = 500;
export const MAX_STEP_ID_CHARS = 64;
export const UPSTREAM_TIMEOUT_MS = 7_500;
const STEP_ID_RE = /^[a-z0-9]+(\.[a-z0-9]+)*$/;
const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function json(status: number, body: unknown, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", ...extraHeaders },
  });
}

const error = (status: number, code: string) => json(status, { error: code });

/** Constant-time comparison over UTF-8 bytes; a length mismatch still walks the longer input. */
export function safeEqual(a: string, b: string): boolean {
  const enc = new TextEncoder();
  const x = enc.encode(a);
  const y = enc.encode(b);
  const len = Math.max(x.length, y.length);
  let diff = x.length ^ y.length;
  for (let i = 0; i < len; i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

function authorized(request: Request, env: Env): boolean {
  const header = request.headers.get("authorization") ?? "";
  if (!env.SHARED_SECRET || !header.startsWith("Bearer ")) return false;
  return safeEqual(header.slice("Bearer ".length), env.SHARED_SECRET);
}

/** Reads the body, stopping once it exceeds the limit. Returns null when too large. */
async function readLimited(request: Request): Promise<Uint8Array | null | "aborted"> {
  if (!request.body) return new Uint8Array(0);
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    let chunk: ReadableStreamReadResult<Uint8Array>;
    try {
      chunk = await reader.read();
    } catch {
      return "aborted";
    }
    const { done, value } = chunk;
    if (done) break;
    total += value.byteLength;
    if (total > MAX_BODY_BYTES) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const out = new Uint8Array(total);
  let off = 0;
  for (const c of chunks) {
    out.set(c, off);
    off += c.byteLength;
  }
  return out;
}

function isJpeg(bytes: Uint8Array): boolean {
  return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
}

function toBase64(bytes: Uint8Array): string {
  let bin = "";
  const step = 0x8000;
  for (let i = 0; i < bytes.length; i += step) {
    bin += String.fromCharCode(...bytes.subarray(i, i + step));
  }
  return btoa(bin);
}

interface VerifyInput {
  stepId: string;
  question: string;
  images: Uint8Array[];
}

type Parsed = { ok: true; input: VerifyInput } | { ok: false; response: Response };

const fail = (status: number, code: string): Parsed => ({ ok: false, response: error(status, code) });

/** Shared size-limited multipart read for /verify and /scene. */
async function readForm(request: Request): Promise<{ ok: true; form: FormData } | { ok: false; response: Response }> {
  const bad = (status: number, code: string) => ({ ok: false as const, response: error(status, code) });
  const contentType = request.headers.get("content-type") ?? "";
  if (!contentType.toLowerCase().startsWith("multipart/form-data")) return bad(400, "malformed_body");
  const declared = Number(request.headers.get("content-length") ?? "0");
  if (Number.isFinite(declared) && declared > MAX_BODY_BYTES) return bad(413, "too_large");
  const raw = await readLimited(request);
  if (raw === null) return bad(413, "too_large");
  if (raw === "aborted") return bad(400, "malformed_body");
  try {
    return { ok: true, form: await new Response(raw, { headers: { "content-type": contentType } }).formData() };
  } catch {
    return bad(400, "malformed_body");
  }
}

async function parseBody(request: Request): Promise<Parsed> {
  const read = await readForm(request);
  if (!read.ok) return read;
  const form = read.form;

  const parts = form.getAll("image");
  if (parts.length > MAX_IMAGES) return fail(413, "too_many_images");

  const requestId = form.get("requestId");
  const stepId = form.get("stepId");
  const question = form.get("question");
  if (typeof requestId !== "string" || !UUID_RE.test(requestId)) return fail(400, "invalid_request_id");
  if (typeof stepId !== "string" || !stepId.trim() || typeof question !== "string" || !question.trim()) {
    return fail(400, "malformed_body");
  }
  if (question.length > MAX_QUESTION_CHARS || stepId.length > MAX_STEP_ID_CHARS || !STEP_ID_RE.test(stepId)) {
    return fail(400, "malformed_body");
  }
  if (parts.length === 0) return fail(400, "malformed_body");

  const images: Uint8Array[] = [];
  for (const part of parts) {
    if (typeof part === "string") return fail(400, "malformed_body");
    const bytes = new Uint8Array(await part.arrayBuffer());
    if (!isJpeg(bytes)) return fail(400, "malformed_body");
    images.push(bytes);
  }
  return { ok: true, input: { stepId, question, images } };
}

async function verify(request: Request, env: Env): Promise<Response> {
  if (!authorized(request, env)) return error(401, "unauthorized");
  const parsed = await parseBody(request);
  if (!parsed.ok) return parsed.response;
  const { question, images } = parsed.input;

  const model = env.MODEL_ID || DEFAULT_MODEL_ID;

  let completion: OpenAI.Chat.Completions.ChatCompletion;
  try {
    // One client per request and no SDK retries: a retry could double-bill. The timeout sits just
    // under the app's 8 s limit. Construction is inside the try: a missing key throws here.
    const client = new OpenAI({
      apiKey: env.DEEPSEEK_API_KEY,
      baseURL: DEEPSEEK_BASE_URL,
      maxRetries: 0,
      timeout: UPSTREAM_TIMEOUT_MS,
    });
    completion = await client.chat.completions.create({
      model,
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: SYSTEM_PROMPT },
        {
          role: "user",
          content: [
            { type: "text", text: question },
            { type: "text", text: framesNote(images.length) },
            ...images.map((img) => ({
              type: "image_url" as const,
              image_url: { url: `data:image/jpeg;base64,${toBase64(img)}` },
            })),
          ],
        },
      ],
    });
  } catch {
    return error(502, "upstream_failed");
  }

  const modelId = typeof completion?.model === "string" && completion.model ? completion.model : model;
  const message = completion?.choices?.[0]?.message;
  const result = message && !message.refusal ? parseModelVerdict(message.content) : null;
  if (!result) {
    return json(200, { verdict: "cannot_tell", reason: "invalid_model_output", modelId, promptVersion: PROMPT_VERSION });
  }
  return json(200, { verdict: result.verdict, reason: result.reason, modelId, promptVersion: PROMPT_VERSION });
}

interface SceneInput {
  kinds: string[];
  locale: string;
  image: Uint8Array;
}

async function parseSceneBody(
  request: Request,
): Promise<{ ok: true; input: SceneInput } | { ok: false; response: Response }> {
  const read = await readForm(request);
  if (!read.ok) return read;
  const form = read.form;
  const bad = (status: number, code: string) => ({ ok: false as const, response: error(status, code) });
  const requestId = form.get("requestId");
  if (typeof requestId !== "string" || !UUID_RE.test(requestId)) return bad(400, "invalid_request_id");
  const kindsRaw = form.get("kinds");
  const locale = form.get("locale");
  if (typeof kindsRaw !== "string" || typeof locale !== "string" || !LOCALE_RE.test(locale)) {
    return bad(400, "malformed_body");
  }
  const kinds = [...new Set(kindsRaw.split(",").map((k) => k.trim()).filter((k) => k))];
  if (kinds.length === 0 || kinds.length > MAX_SCENE_KINDS) return bad(400, "malformed_body");
  if (kinds.some((k) => k.length > MAX_KIND_CHARS || !KIND_RE.test(k))) return bad(400, "malformed_body");
  const parts = form.getAll("image");
  if (parts.length > 1) return bad(413, "too_many_images");
  const part = parts[0];
  if (part === undefined || typeof part === "string") return bad(400, "malformed_body");
  const image = new Uint8Array(await part.arrayBuffer());
  if (!isJpeg(image)) return bad(400, "malformed_body");
  return { ok: true, input: { kinds, locale, image } };
}

async function scene(request: Request, env: Env): Promise<Response> {
  if (!authorized(request, env)) return error(401, "unauthorized");
  const parsed = await parseSceneBody(request);
  if (!parsed.ok) return parsed.response;
  const { kinds, locale, image } = parsed.input;
  const model = env.MODEL_ID || DEFAULT_MODEL_ID;

  let completion: OpenAI.Chat.Completions.ChatCompletion;
  try {
    const client = new OpenAI({
      apiKey: env.DEEPSEEK_API_KEY,
      baseURL: DEEPSEEK_BASE_URL,
      maxRetries: 0,
      timeout: UPSTREAM_TIMEOUT_MS,
    });
    completion = await client.chat.completions.create({
      model,
      response_format: { type: "json_object" },
      messages: [
        { role: "system", content: SCENE_SYSTEM_PROMPT },
        {
          role: "user",
          content: [
            { type: "text", text: sceneUserText(kinds, locale) },
            { type: "image_url", image_url: { url: `data:image/jpeg;base64,${toBase64(image)}` } },
          ],
        },
      ],
    });
  } catch {
    return error(502, "upstream_failed");
  }

  const modelId = typeof completion?.model === "string" && completion.model ? completion.model : model;
  const message = completion?.choices?.[0]?.message;
  const result = message && !message.refusal ? parseSceneResult(message.content, kinds) : UNCLEAR;
  return json(200, { ...result, modelId, promptVersion: SCENE_PROMPT_VERSION });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const { pathname } = new URL(request.url);
    if (pathname !== "/verify" && pathname !== "/scene") return error(404, "not_found");
    if (request.method !== "POST") return json(405, { error: "method_not_allowed" }, { allow: "POST" });
    return pathname === "/scene" ? scene(request, env) : verify(request, env);
  },
} satisfies ExportedHandler<Env>;
