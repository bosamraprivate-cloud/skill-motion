import { describe, expect, it } from "vitest";
import { framesNote, parseModelVerdict } from "../src/prompt";

const answer = '{"verdict":"done","reason":"ok"}';

describe("parseModelVerdict", () => {
  it("accepts a 120-character reason and rejects 121", () => {
    expect(parseModelVerdict(JSON.stringify({ verdict: "done", reason: "x".repeat(120) }))).not.toBeNull();
    expect(parseModelVerdict(JSON.stringify({ verdict: "done", reason: "x".repeat(121) }))).toBeNull();
  });

  it("counts code points, not UTF-16 units", () => {
    expect(parseModelVerdict(JSON.stringify({ verdict: "done", reason: "\u{1F600}".repeat(120) }))).not.toBeNull();
  });

  it.each([`\`\`\`json\n${answer}\n\`\`\``, `\`\`\`\n${answer}\n\`\`\``])("accepts a fenced answer", (text) => {
    expect(parseModelVerdict(text)).toEqual({ verdict: "done", reason: "ok" });
  });

  it("frames note matches the count", () => {
    expect(framesNote(2)).toBe("2 frames in time order.");
    expect(framesNote(3)).toContain("start, middle, end");
  });
});
