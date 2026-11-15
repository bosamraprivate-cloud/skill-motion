import { beforeEach, describe, expect, it, vi } from "vitest";

const { create } = vi.hoisted(() => ({ create: vi.fn() }));

vi.mock("openai", () => {
  class OpenAI {
    chat = { completions: { create } };
  }
  return { default: OpenAI };
});

import worker, { type Env } from "../src/index";
import { SCENE_MIN_CONFIDENCE, SCENE_PROMPT_VERSION, parseSceneResult } from "../src/scene";

const env: Env = { DEEPSEEK_API_KEY: "k", SHARED_SECRET: "s3cret", MODEL_ID: "deepseek-flash" };
const UUID = "123e4567-e89b-42d3-a456-426614174000";
const jpeg = () => {
  const b = new Uint8Array(16);
  b.set([0xff, 0xd8, 0xff]);
  return b;
};

function form(o: { kinds?: string | null; locale?: string | null; images?: Uint8Array[]; requestId?: string } = {}) {
  const f = new FormData();
  f.append("requestId", o.requestId ?? UUID);
  const kinds = o.kinds === undefined ? "table,chair" : o.kinds;
  const locale = o.locale === undefined ? "ar-EG" : o.locale;
  if (kinds !== null) f.append("kinds", kinds);
  if (locale !== null) f.append("locale", locale);
  for (const img of o.images ?? [jpeg()]) f.append("image", new Blob([img], { type: "image/jpeg" }), "scene.jpg");
  return f;
}

const req = (body: BodyInit, secret: string | null = "s3cret") => {
  const headers = new Headers();
  if (secret !== null) headers.set("authorization", `Bearer ${secret}`);
  return new Request("https://proxy.example/scene", { method: "POST", headers, body });
};
const reply = (content: string | null) => ({ model: "deepseek-flash-1017", choices: [{ message: { content, refusal: null } }] });
const good = {
  objects: [
    { kind: "table", box: { x: 0.1, y: 0.5, w: 0.6, h: 0.4 }, confidence: 0.9 },
    { kind: "chair", box: { x: 0.7, y: 0.4, w: 0.2, h: 0.5 }, confidence: 0.8 },
  ],
  issues: [],
};

beforeEach(() => {
  create.mockReset();
});

describe("/scene route", () => {
  it.each([[null], ["wrong"]])("401 for secret %s", async (secret) => {
    const res = await worker.fetch(req(form(), secret), env);
    expect(res.status).toBe(401);
    expect(create).not.toHaveBeenCalled();
  });

  it.each([
    ["no kinds", { kinds: null }],
    ["bad kind", { kinds: "Table!" }],
    ["no locale", { locale: null }],
    ["no image", { images: [] }],
    ["not jpeg", { images: [new Uint8Array([1, 2, 3, 4])] }],
    ["bad request id", { requestId: "x" }],
  ])("400 for %s", async (_n, o) => {
    const res = await worker.fetch(req(form(o as never)), env);
    expect(res.status).toBe(400);
    expect(create).not.toHaveBeenCalled();
  });

  it("413 for two images", async () => {
    const res = await worker.fetch(req(form({ images: [jpeg(), jpeg()] })), env);
    expect(res.status).toBe(413);
  });

  it("returns a valid parse with modelId and promptVersion", async () => {
    create.mockResolvedValue(reply(JSON.stringify(good)));
    const res = await worker.fetch(req(form()), env);
    expect(res.status).toBe(200);
    const body = (await res.json()) as Record<string, unknown>;
    expect(body).toEqual({ ok: true, objects: good.objects, issues: [], modelId: "deepseek-flash-1017", promptVersion: SCENE_PROMPT_VERSION });
  });

  it("invalid model JSON becomes unclear", async () => {
    create.mockResolvedValue(reply("not json"));
    const body = (await (await worker.fetch(req(form()), env)).json()) as Record<string, unknown>;
    expect(body.ok).toBe(false);
    expect(body.issues).toEqual([{ code: "unclear" }]);
  });

  it("502 when upstream fails", async () => {
    create.mockRejectedValue(new TypeError("fetch failed"));
    expect((await worker.fetch(req(form()), env)).status).toBe(502);
  });
});

describe("parseSceneResult", () => {
  it("clamps boxes to 0..1", () => {
    const r = parseSceneResult(
      JSON.stringify({ objects: [{ kind: "table", box: { x: -0.2, y: 0.5, w: 2, h: 0.9 }, confidence: 1.5 }], issues: [] }),
      ["table"],
    );
    expect(r.objects[0]).toEqual({ kind: "table", box: { x: 0, y: 0.5, w: 1, h: 0.5 }, confidence: 1 });
    expect(r.ok).toBe(true);
  });

  it("missing kind gives ok false and missing_object", () => {
    const r = parseSceneResult(JSON.stringify({ objects: [good.objects[0]], issues: [] }), ["table", "chair"]);
    expect(r.ok).toBe(false);
    expect(r.issues).toContainEqual({ code: "missing_object", kind: "chair" });
  });

  it("low confidence counts as missing", () => {
    const o = { ...good.objects[0], confidence: SCENE_MIN_CONFIDENCE - 0.01 };
    const r = parseSceneResult(JSON.stringify({ objects: [o], issues: [] }), ["table"]);
    expect(r.ok).toBe(false);
    expect(r.issues).toEqual([{ code: "missing_object", kind: "table" }]);
  });

  it("drops unknown kinds and codes; blocking issue makes ok false", () => {
    const r = parseSceneResult(
      JSON.stringify({
        objects: [...good.objects, { kind: "sofa", box: { x: 0, y: 0, w: 0.1, h: 0.1 }, confidence: 0.9 }],
        issues: [{ code: "lava" }, { code: "too_dark" }],
      }),
      ["table", "chair"],
    );
    expect(r.objects.map((o) => o.kind)).toEqual(["table", "chair"]);
    expect(r.issues).toEqual([{ code: "too_dark" }]);
    expect(r.ok).toBe(false);
  });

  it("partly_visible blocks; person_in_view does not", () => {
    const pv = parseSceneResult(JSON.stringify({ ...good, issues: [{ code: "partly_visible", kind: "chair" }] }), ["table", "chair"]);
    expect(pv.ok).toBe(false);
    const piv = parseSceneResult(JSON.stringify({ ...good, issues: [{ code: "person_in_view" }] }), ["table", "chair"]);
    expect(piv.ok).toBe(true);
    expect(piv.issues).toEqual([{ code: "person_in_view" }]);
  });

  it("accepts person as a required kind", () => {
    const person = { kind: "person", box: { x: 0.3, y: 0.1, w: 0.3, h: 0.8 }, confidence: 0.95 };
    const r = parseSceneResult(JSON.stringify({ objects: [...good.objects, person], issues: [] }), ["table", "person"]);
    expect(r.ok).toBe(true);
    expect(r.objects.map((o) => o.kind)).toEqual(["table", "person"]);
  });

  it("prompt says faces are blurred and never to describe the person", async () => {
    const { SCENE_SYSTEM_PROMPT } = await import("../src/scene");
    expect(SCENE_SYSTEM_PROMPT).toMatch(/blurred on purpose/);
    expect(SCENE_SYSTEM_PROMPT).toMatch(/Never describe/);
  });

  it.each([["[]"], ['{"objects": "x"}'], ['{"objects":[{"kind":"table","box":{"x":"a"},"confidence":1}]}'], [""]])(
    "invalid shape %s becomes unclear",
    (t) => {
      expect(parseSceneResult(t, ["table"])).toEqual({ ok: false, objects: [], issues: [{ code: "unclear" }] });
    },
  );
});
