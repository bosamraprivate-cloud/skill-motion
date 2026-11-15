/** Bump on any change to the scene prompt or the /scene contract (AD-2, AD-7). */
export const SCENE_PROMPT_VERSION = "scene-v2";

/** A required kind counts as found only at or above this confidence. */
export const SCENE_MIN_CONFIDENCE = 0.6;
export const MAX_SCENE_KINDS = 12;
export const MAX_KIND_CHARS = 32;
export const KIND_RE = /^[a-z][a-z0-9_]*$/;
export const LOCALE_RE = /^[a-z]{2}(-[A-Za-z]{2})?$/;

export const ISSUE_CODES = [
  "missing_object",
  "partly_visible",
  "too_dark",
  "person_in_view",
  "camera_tilted",
  "too_far",
  "too_close",
  "unclear",
] as const;
export type IssueCode = (typeof ISSUE_CODES)[number];
const ISSUE_SET: ReadonlySet<string> = new Set(ISSUE_CODES);
/** Issues accepted but never blocking: the child may be in the scene (face blurred on device). */
const NON_BLOCKING: ReadonlySet<IssueCode> = new Set(["person_in_view"]);

export interface Box {
  x: number;
  y: number;
  w: number;
  h: number;
}
export interface SceneObject {
  kind: string;
  box: Box;
  confidence: number;
}
export interface SceneIssue {
  code: IssueCode;
  kind?: string;
}
export interface SceneResult {
  ok: boolean;
  objects: SceneObject[];
  issues: SceneIssue[];
}

export const UNCLEAR: SceneResult = { ok: false, objects: [], issues: [{ code: "unclear" }] };

export const SCENE_SYSTEM_PROMPT = [
  "You check a photo of a room scene before a child's daily living practice session.",
  "A child may be in the photo. Faces are blurred on purpose for privacy. Never describe, identify or comment on any person; only locate them when \"person\" is a required kind.",
  "The user message lists the required object kinds. Locate each one that is visible.",
  "Report each found object once, with a box in normalized image coordinates (0..1): x,y = top-left corner, w,h = width and height of the visible extent.",
  "Objects may be partly hidden by the child or by other objects (for example a table partly behind the child). Still locate them and box the visible extent.",
  "Report problems using ONLY these issue codes: " + ISSUE_CODES.join(", ") + ".",
  'Use "missing_object" with "kind" for each required kind you cannot find.',
  'Use "partly_visible" with "kind" only when the part of the object needed for the task (for example the table top where items stand) is hidden or cut off by the image edge; ordinary occlusion by the child is not an issue.',
  'Do not report "person_in_view" because a person is visible.',
  "Judge only what is visible. Do not guess. If unsure, lower the confidence or report \"unclear\".",
  'Answer only with a JSON object: {"objects": [{"kind": "<required kind>", "box": {"x": 0, "y": 0, "w": 0, "h": 0}, "confidence": 0.0}], "issues": [{"code": "<issue code>", "kind": "<optional kind>"}]}.',
].join("\n");

export function sceneUserText(kinds: string[], locale: string): string {
  return `Required object kinds: ${kinds.join(", ")}. Locale: ${locale}.`;
}

const FENCE_RE = /^```(?:json)?\s*([\s\S]*?)\s*```$/i;
const clamp01 = (n: number) => Math.min(1, Math.max(0, n));
const isNum = (n: unknown): n is number => typeof n === "number" && Number.isFinite(n);

/** Parses and validates model text against the required kinds; anything invalid becomes UNCLEAR. */
export function parseSceneResult(text: string | null | undefined, required: string[]): SceneResult {
  if (!text || !text.trim()) return UNCLEAR;
  let value: unknown;
  try {
    const trimmed = text.trim();
    value = JSON.parse(FENCE_RE.exec(trimmed)?.[1] ?? trimmed);
  } catch {
    return UNCLEAR;
  }
  if (typeof value !== "object" || value === null || Array.isArray(value)) return UNCLEAR;
  const { objects, issues } = value as Record<string, unknown>;
  if (!Array.isArray(objects) || (issues !== undefined && !Array.isArray(issues))) return UNCLEAR;
  const requiredSet = new Set(required);

  const best = new Map<string, SceneObject>();
  for (const o of objects) {
    if (typeof o !== "object" || o === null) return UNCLEAR;
    const { kind, box, confidence } = o as Record<string, unknown>;
    if (typeof kind !== "string" || !isNum(confidence)) return UNCLEAR;
    if (typeof box !== "object" || box === null) return UNCLEAR;
    const { x, y, w, h } = box as Record<string, unknown>;
    if (!isNum(x) || !isNum(y) || !isNum(w) || !isNum(h)) return UNCLEAR;
    if (!requiredSet.has(kind)) continue; // drop unknown kinds
    const bx = clamp01(x);
    const by = clamp01(y);
    const clamped: SceneObject = {
      kind,
      box: { x: bx, y: by, w: clamp01(Math.min(w, 1 - bx)), h: clamp01(Math.min(h, 1 - by)) },
      confidence: clamp01(confidence),
    };
    if (clamped.box.w <= 0 || clamped.box.h <= 0) continue;
    const prev = best.get(kind);
    if (!prev || prev.confidence < clamped.confidence) best.set(kind, clamped);
  }

  const out: SceneIssue[] = [];
  const seen = new Set<string>();
  const add = (issue: SceneIssue) => {
    const key = `${issue.code}|${issue.kind ?? ""}`;
    if (!seen.has(key)) {
      seen.add(key);
      out.push(issue);
    }
  };
  for (const i of (issues as unknown[] | undefined) ?? []) {
    if (typeof i !== "object" || i === null) continue;
    const { code, kind } = i as Record<string, unknown>;
    if (typeof code !== "string" || !ISSUE_SET.has(code)) continue; // drop unknown codes
    if (typeof kind === "string" && requiredSet.has(kind)) add({ code: code as IssueCode, kind });
    else add({ code: code as IssueCode });
  }

  for (const kind of required) {
    const found = best.get(kind);
    if (!found || found.confidence < SCENE_MIN_CONFIDENCE) add({ code: "missing_object", kind });
  }
  // A kind cannot be both found and missing: drop model "missing_object" for confidently found kinds.
  const final = out.filter(
    (i) => !(i.code === "missing_object" && i.kind && (best.get(i.kind)?.confidence ?? 0) >= SCENE_MIN_CONFIDENCE),
  );
  const objectsOut = required.flatMap((k) => (best.has(k) ? [best.get(k)!] : []));
  const ok = final.every((i) => NON_BLOCKING.has(i.code));
  return { ok, objects: objectsOut, issues: final };
}
