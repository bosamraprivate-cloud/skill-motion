# Scoring Rules

> These two rules connect parts that already exist in the project's documents. They were agreed with the author on 2026-11-11. No new clinical content is added: the levels, the three attempts, the domain tags and the three plans all come from `project-details.md`, the assessment scales and `treatment-plans.md`.

## Rule 1: From camera result to step level

`project-details.md` Stage 5 gives the child three attempts per step, recorded by the camera, and three performance levels. The app links them as follows:

| Result | Level | Score |
|---|---|:---:|
| The step is done on attempt 1, after the standard instruction (picture, text, audio). | Independent | 3 |
| The step is done on attempt 2 or 3, after the app has helped (audio replayed, or the step picture or demonstration shown again). | Partial assistance | 2 |
| The step is not done after attempt 3. | Full assistance | 1 |

## Rule 2: From step levels to the treatment plan

`project-details.md` Stage 7 lists steps done independently as strengths and steps needing help as needs. For each functional domain, the app takes the average score of the scored steps tagged with that domain in the scale, and rounds it to the nearest assistance level:

| Average score in the domain | Result | Plan in `treatment-plans.md` |
|---|---|---|
| 3 | Strength | None |
| 2.5 to less than 3 | Mild weakness | 1. Mild weakness plan |
| 1.5 to less than 2.5 | Moderate weakness | 2. Moderate weakness plan |
| Less than 1.5 | Severe weakness | 3. Severe weakness plan |

A step tagged with several domains counts once in each of them. The domains are those written in the "Functional domain" column of each scale. Where a scale writes "Visual-motor coordination" as a domain, it counts as Motor coordination, because `project-details.md` (section "Fourth") lists visual-motor coordination as a sub-skill of Motor coordination; a step tagged with both counts once.
