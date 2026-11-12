import { beforeEach, describe, expect, it, vi } from "vitest";

const { create, ctor } = vi.hoisted(() => ({ create: vi.fn(), ctor: vi.fn() }));

vi.mock("openai", () => {
  class APIError extends Error {
    status: number;
    constructor(status: number) {
      super("api error");
      this.status = status;
    }
  }
  class OpenAI {
    static APIError = APIError;
    chat = { completions: { create } };
    constructor(opts: unknown) {
      ctor(opts);
    }
  }
  return { default: OpenAI, APIError };
});

import worker, { type Env } from "../src/index";
import { PROMPT_VERSION } from "../src/prompt";
import { APIError } from "openai";

const env: Env = { DEEPSEEK_API_KEY: "test-key", SHARED_SECRET: "s3cret", MODEL_ID: "deepseek-flash" };
const UUID = "123e4567-e89b-42d3-a456-426614174000";
const jpeg = (size = 16) => {
  const b = new Uint8Array(size);
  b.set([0xff, 0xd8, 0xff]);
  return b;
};

function form(opts: { images?: Uint8Array[]; requestId?: string | null; stepId?: string | null; question?: string | null } = {}) {
  const f = new FormData();
  const { requestId = UUID, stepId = "drink.cup.06", question = "Is the cup lifted to the mouth?" } = opts;
  if (requestId !== null) f.append("requestId", requestId);
  if (stepId !== null) f.append("stepId", stepId);
  if (question !== null) f.append("question", question);
  for (const img of opts.images ?? [jpeg(), jpeg(), jpeg()]) {
    f.append("image", new Blob([img], { type: "image/jpeg" }), "frame.jpg");
  }
  return f;
}

function req(body: BodyInit | null, init: { secret?: string | null; method?: string; path?: string; headers?: Record<string, string> } = {}) {
  const headers = new Headers(init.headers);
  const secret = init.secret === undefined ? "s3cret" : init.secret;
  if (secret !== null) headers.set("authorization", `Bearer ${secret}`);
  return new Request(`https://proxy.example${init.path ?? "/verify"}`, {
    method: init.method ?? "POST",
    headers,
    body,
  });
}

const call = (r: Request, e: Env = env) => worker.fetch(r, e);

function modelReply(content: string | null, extra: Record<string, unknown> = {}) {
  return { model: "deepseek-flash-1017", choices: [{ message: { role: "assistant", content, refusal: null, ...extra } }] };
}

beforeEach(() => {
  create.mockReset();
  ctor.mockReset();
});

describe("auth", () => {
  it.each([[null], ["wrong"], [""]])("401 for secret %s without calling the SDK", async (secret) => {
    const res = await call(req(form(), { secret }));
    expect(res.status).toBe(401);
    expect(create).not.toHaveBeenCalled();
  });

  it("401 for a non-Bearer scheme", async () => {
    const res = await call(req(form(), { secret: null, headers: { authorization: "Basic s3cret" } }));
    expect(res.status).toBe(401);
  });
});

describe("limits", () => {
  it("413 for 4 images", async () => {
    const res = await call(req(form({ images: [jpeg(), jpeg(), jpeg(), jpeg()] })));
    expect(res.status).toBe(413);
    expect(create).not.toHaveBeenCalled();
  });

  it("413 when the actual body exceeds 3 MB", async () => {
    // Pre-serialize so the stream is a plain buffer (Node's FormData stream misbehaves on cancel).
    const encoded = new Response(form({ images: [jpeg(3 * 1024 * 1024 + 1)] }));
    const bytes = new Uint8Array(await encoded.arrayBuffer());
    const res = await call(req(bytes, { headers: { "content-type": encoded.headers.get("content-type")! } }));
    expect(res.status).toBe(413);
    expect(create).not.toHaveBeenCalled();
  });

  it("413 when Content-Length declares more than 3 MB", async () => {
    // Body never read: the declared length alone must trigger 413.
    const headers = new Headers({
      authorization: "Bearer s3cret",
      "content-type": "multipart/form-data; boundary=x",
      "content-length": String(4 * 1024 * 1024),
    });
    const proxied = { url: "https://proxy.example/verify", method: "POST", headers, body: null } as unknown as Request;
    const res = await call(proxied);
    expect(res.status).toBe(413);
    expect(create).not.toHaveBeenCalled();
  });
});

describe("malformed", () => {
  it.each([
    ["not multipart", () => req(JSON.stringify({ a: 1 }), { headers: { "content-type": "application/json" } })],
    ["missing requestId", () => req(form({ requestId: null }))],
    ["bad uuid", () => req(form({ requestId: "not-a-uuid" }))],
    ["missing stepId", () => req(form({ stepId: null }))],
    ["missing question", () => req(form({ question: null }))],
    ["zero images", () => req(form({ images: [] }))],
    ["non-JPEG part", () => req(form({ images: [new Uint8Array([0x89, 0x50, 0x4e, 0x47])] }))],
  ])("400 for %s", async (_name, make) => {
    const res = await call(make());
    expect(res.status).toBe(400);
    expect(create).not.toHaveBeenCalled();
  });

  it("400 for a text field named image", async () => {
    const f = form({ images: [] });
    f.append("image", "text");
    const res = await call(req(f));
    expect(res.status).toBe(400);
  });
});

describe("happy path", () => {
  it("returns the model verdict with modelId and promptVersion", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"done","reason":"Cup at the mouth in the end frame"}'));
    const res = await call(req(form()));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({
      verdict: "done",
      reason: "Cup at the mouth in the end frame",
      modelId: "deepseek-flash-1017",
      promptVersion: PROMPT_VERSION,
    });
  });

  it("passes not_done through from a valid model answer", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"not_done","reason":"Cup stays on the table"}'));
    const body = (await (await call(req(form()))).json()) as { verdict: string };
    expect(body.verdict).toBe("not_done");
  });

  it("calls the SDK once with the DeepSeek request shape", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"done","reason":"ok"}'));
    await call(req(form()));
    expect(ctor).toHaveBeenCalledWith(expect.objectContaining({ apiKey: "test-key", baseURL: "https://api.deepseek.com", maxRetries: 0 }));
    expect(create).toHaveBeenCalledTimes(1);
    const args = create.mock.calls[0][0];
    expect(args.model).toBe("deepseek-flash");
    expect(args.response_format).toEqual({ type: "json_object" });
    expect(args.messages[0].role).toBe("system");
    const user = args.messages[1];
    expect(user.content[0]).toEqual({ type: "text", text: "Is the cup lifted to the mouth?" });
    const images = user.content.filter((p: { type: string }) => p.type === "image_url");
    expect(images).toHaveLength(3);
    for (const p of images) expect(p.image_url.url).toMatch(/^data:image\/jpeg;base64,\/9j\//);
  });

  it("uses deepseek-flash when MODEL_ID is unset", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"done","reason":"ok"}'));
    await call(req(form()), { DEEPSEEK_API_KEY: "k", SHARED_SECRET: "s3cret" });
    expect(create.mock.calls[0][0].model).toBe("deepseek-flash");
  });
});

describe("invalid model output", () => {
  it.each([
    ["prose", modelReply("The cup was lifted.")],
    ["bad JSON", modelReply('{"verdict":"done"')],
    ["unknown verdict", modelReply('{"verdict":"maybe","reason":"x"}')],
    ["missing reason", modelReply('{"verdict":"done"}')],
    ["reason too long", modelReply(JSON.stringify({ verdict: "done", reason: "x".repeat(121) }))],
    ["refusal", modelReply(null, { refusal: "I cannot help" })],
    ["empty", modelReply("")],
    ["no choices", { model: "deepseek-flash-1017", choices: [] }],
  ])("200 cannot_tell for %s", async (_name, reply) => {
    create.mockResolvedValue(reply);
    const res = await call(req(form()));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({
      verdict: "cannot_tell",
      reason: "invalid_model_output",
      modelId: "deepseek-flash-1017",
      promptVersion: PROMPT_VERSION,
    });
  });
});

describe("upstream failure", () => {
  it.each([[400], [429], [500]])("502 for APIError %s", async (status) => {
    create.mockRejectedValue(new APIError(status as never, undefined as never, undefined as never, undefined as never));
    const res = await call(req(form()));
    expect(res.status).toBe(502);
    expect(create).toHaveBeenCalledTimes(1);
  });

  it("502 for a network error", async () => {
    create.mockRejectedValue(new TypeError("fetch failed"));
    expect((await call(req(form()))).status).toBe(502);
  });
});

describe("review fixes", () => {
  it("sets a 7.5 s timeout and maps a timeout error to 502", async () => {
    create.mockRejectedValue(Object.assign(new Error("Request timed out."), { name: "APIConnectionTimeoutError" }));
    const res = await call(req(form()));
    expect(res.status).toBe(502);
    expect(ctor).toHaveBeenCalledWith(expect.objectContaining({ timeout: 7_500 }));
  });

  it("502 upstream_failed when the client constructor throws (missing key)", async () => {
    ctor.mockImplementationOnce(() => {
      throw new Error("missing api key");
    });
    const res = await call(req(form()), { DEEPSEEK_API_KEY: "", SHARED_SECRET: "s3cret" });
    expect(res.status).toBe(502);
    expect(await res.json()).toEqual({ error: "upstream_failed" });
  });

  it("400 when the upload stream errors mid-read", async () => {
    const body = new ReadableStream<Uint8Array>({
      start(c) {
        c.error(new Error("disconnect"));
      },
    });
    const headers = new Headers({ authorization: "Bearer s3cret", "content-type": "multipart/form-data; boundary=x" });
    const r = { url: "https://proxy.example/verify", method: "POST", headers, body } as unknown as Request;
    const res = await call(r);
    expect(res.status).toBe(400);
    expect(await res.json()).toEqual({ error: "malformed_body" });
  });

  it.each([
    ["question > 500 chars", { question: "q".repeat(501) }],
    ["stepId > 64 chars", { stepId: "a".repeat(65) }],
    ["stepId bad format", { stepId: "Drink cup!" }],
    ["stepId trailing dot", { stepId: "drink.cup." }],
  ])("400 for %s", async (_n, o) => {
    const res = await call(req(form(o)));
    expect(res.status).toBe(400);
    expect(create).not.toHaveBeenCalled();
  });

  it("accepts a 500-char question", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"done","reason":"ok"}'));
    expect((await call(req(form({ question: "q".repeat(500) })))).status).toBe(200);
  });

  it("sends a count-aware frames note", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"done","reason":"ok"}'));
    await call(req(form({ images: [jpeg()] })));
    expect(create.mock.calls[0][0].messages[1].content[1]).toEqual({ type: "text", text: "1 frame." });
  });

  it("round-trips a ~100 KB image through multi-chunk base64", async () => {
    create.mockResolvedValue(modelReply('{"verdict":"done","reason":"ok"}'));
    const img = new Uint8Array(100_000);
    for (let i = 0; i < img.length; i++) img[i] = (i * 31 + 7) & 0xff;
    img.set([0xff, 0xd8, 0xff]);
    await call(req(form({ images: [img] })));
    const url: string = create.mock.calls[0][0].messages[1].content[2].image_url.url;
    const decoded = Uint8Array.from(atob(url.slice("data:image/jpeg;base64,".length)), (c) => c.charCodeAt(0));
    expect(decoded).toEqual(img);
  });
});

describe("routing", () => {
  it("405 for GET /verify", async () => {
    const res = await call(req(null, { method: "GET" }));
    expect(res.status).toBe(405);
  });

  it("404 for POST /other", async () => {
    const res = await call(req(form(), { path: "/other" }));
    expect(res.status).toBe(404);
  });
});
