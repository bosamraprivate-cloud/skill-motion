/** Bump on any change to the prompt or the verify contract (AD-2, AD-7). */
export const PROMPT_VERSION = "v1";

export const SYSTEM_PROMPT = [
  "You check whether a daily living step was performed.",
  "You receive 1 to 3 frames from one attempt, in time order.",
  "Judge only what is visible in these frames. Do not guess.",
  'Answer only with a JSON object: {"verdict": "done" | "not_done" | "cannot_tell", "reason": "<at most 120 characters>"}.',
  'If you are unsure, or the relevant hands or object are not visible, answer "cannot_tell".',
  "The user message contains the step question and the number of frames.",
].join("\n");

/** Count-aware note sent with the images, e.g. "3 frames in time order (start, middle, end)." */
export function framesNote(count: number): string {
  if (count === 1) return "1 frame.";
  if (count === 3) return "3 frames in time order (start, middle, end).";
  return `${count} frames in time order.`;
}

const FENCE_RE = /^```(?:json)?\s*([\s\S]*?)\s*```$/i;

export type Verdict = "done" | "not_done" | "cannot_tell";

export interface ModelVerdict {
  verdict: Verdict;
  reason: string;
}

const VERDICTS: ReadonlySet<string> = new Set(["done", "not_done", "cannot_tell"]);
export const MAX_REASON_LENGTH = 120;

/** Parses model text into a verdict; returns null for anything not matching the schema. */
export function parseModelVerdict(text: string | null | undefined): ModelVerdict | null {
  if (!text || !text.trim()) return null;
  let value: unknown;
  try {
    const trimmed = text.trim();
    value = JSON.parse(FENCE_RE.exec(trimmed)?.[1] ?? trimmed);
  } catch {
    return null;
  }
  if (typeof value !== "object" || value === null || Array.isArray(value)) return null;
  const { verdict, reason } = value as Record<string, unknown>;
  if (typeof verdict !== "string" || !VERDICTS.has(verdict)) return null;
  if (typeof reason !== "string" || [...reason].length > MAX_REASON_LENGTH) return null;
  return { verdict: verdict as Verdict, reason };
}
