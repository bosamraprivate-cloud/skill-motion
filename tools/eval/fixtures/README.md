# Metrics fixture

`metrics_fixture.csv` (results.csv columns) + `metrics_clips.csv` (eval/clips.csv columns) give the hand-computed
numbers in `metrics_fixture.expected.json`. The Python script (`test_compute_metrics.py`) and the Kotlin `Metrics`
(Story 5.8 part B) must both reproduce them. IDs are synthetic; no clinical content.

## Steps (one session `s1`)

| Step | App verdicts (prompt) | `level` | Blind verdicts | Blind level (Rule 1) | Kappa |
|---|---|---|---|---|---|
| a | done(1) | 3 | done | 3 | (3,3) |
| b | not_done(1), done(2) | 2 | done, done | 3 | (2,3) |
| c | not_done(1,2,3) | 1 | not_done, not_done, done | 2 | (1,2) |
| d | cannot_tell(1), done(1) | 3 | done, done | 3 | (3,3) |
| e | not_done(1), done(2) | 2 | not_done, (unrated) | - | partial, excluded |
| f | cannot_tell(1), cannot_tell(1) | not_scored | done, done | 3 | not scored, excluded |
| g | done(1) | 3 | cannot_tell | open | blind open, excluded |
| h | not_done(1), done(2); correctedLevel 3 | 2 | not_done, done | 2 | (2,2) |

## Derivation

- Attempts 15; app cannot tell d1, f1, f2 = 3 -> rate 3/15 = 0.2 (above 15 %, flagged).
- Rated 14 (e2 unrated). Cannot tell on either side: d1, f1, f2, g1 = 4. Decided 10: a1 =, b1 x, b2 =, c1 =,
  c2 =, c3 x, d2 =, e1 =, h1 =, h2 = -> agreement 8/10 = 0.8 (meets).
- Kappa pairs (app, blind): (3,3) x2, (2,3), (1,2), (2,2); n = 5. Weight w = (i-j)^2/4.
  Observed weighted sum = 0.25 + 0.25 = 0.5. Marginals app {1:1, 2:2, 3:2}, blind {2:2, 3:3}.
  Expected = sum w * r_i * c_j / n = (1*2/5)*0.25 + (1*3/5)*1 + (2*3/5)*0.25 + (2*2/5)*0.25
  = 0.1 + 0.6 + 0.3 + 0.2 = 1.2. Kappa = 1 - 0.5/1.2 = 7/12 = 0.58333 (below 0.6, flagged).
- Corrections: step h only (3 vs automatic 2): 1 step, 1 differs; not used for accuracy.
- Clips: a1, b1, b2 match; c1 `missing.mp4` unmatched. Scripted done vs app done, not_done, done -> 2/3.

## Kotlin export sample

`results_export_sample.csv` is written by the Android unit test `CsvExporterTest` (Story 5.6) from synthetic rows;
`test_kotlin_export_round_trip` parses it with `compute_metrics.read_results`. Regenerate with `./gradlew :app:testDebugUnitTest --tests "*CsvExporterTest" -DupdateFixtures=true`; never
hand-edit.
