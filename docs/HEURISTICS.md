# Heuristics for Fuzzer Example Prioritization

This document defines a language-agnostic set of heuristics for prioritizing and grouping fuzzer-generated examples. The goal is to select the top most relevant representative examples for function behavior summarization.

## 3-Layer Grouping Pipeline

Organize examples into groups to reduce entropy and surface important cases. The order of layers matters.

### 1. Group by Input Type / Shape
*Most important dimension of functional behavior.*
Group examples by their input structure (e.g., `number`, `string`, `null`, `array<number>`, `object{a:number}`).
*   **Rationale:** Different types lead to different code paths and meaningful behaviors.
*   **Ordering:** Shapes leading to diverse outputs or exceptions rank higher.

### 2. Group by Coverage / Path Hash
*Second most important dimension: distinct branches → distinct behaviors.*
Inside input groups, group executions by a path hash (e.g., XOR of branch IDs).
*   **Captures:** Conditionals, nested decisions, early returns, loops.
*   **Ordering:** Rare paths (corner cases) > Very long paths (complex behavior) > Minimal paths (baseline).

### 3. Group by Output

#### 3a. Group by Exception Type
*Essential to show crash/failure modes separately.*
Inside path groups, split into:
*   No exception
*   Exception (by type)
*   **Ordering:** Exceptions always rank high. Rare exception types outrank common ones.

#### 3b. Group by Output Type / Shape
*Final grouping inside each behavioral group.*
Group by output structure (e.g., `number`, `boolean`, `void`, `undefined`).
*   **Ordering:** Rare output types, outliers, or structural changes are prioritized.

---

## Scoring Heuristics

These rules help pick the best representative example inside each group and prioritize the groups themselves.

Local means the heuristic can be computed without knowledge of other groups/examples. Global means it requires access to the other groups/examples of the same group.

### Group-Level Prioritization (Selecting interesting behaviors)

*   **H1 Coverage Rarity Boost:** (Global across all groups/examples)
    `group_score += rarity(path_hash)`
    Uncommon paths often represent edge conditions.

*   **H2 Output Variability Boost:** (Global within same input shape)
    `group_score += output_variability_score(output_value)`
    Outputs that differ significantly from the norm indicate interesting behavior.
    This includes sometimes exceptions, sometimes normal outputs.

*   **H3 Coverage Path Complexity Modifier:** (Local within same coverage group)
    `group_score += abs(path_length - mean_path_length)`
    U-shaped curve: prioritize extremely long paths and very short paths (edge cases).

*   **H4 Input Shape Simplicity:** (Local within same input shape)
    `example_score += 1 / structural_complexity(input)`
    Prefer shapes with fewer fields and lower nesting depth.

### Example-Level Prioritization (Selecting the best representative)

*   **E1 Minimal Input (Shrinking):** (Local only on this example)
    `example_score += 1 / input_size`
    Prefer the smallest input for human interpretability.

*   **E2 Minimal Output:** (Local only on this example)
    `example_score += 1 / output_size`
    Prefer the smallest output for human interpretability.

**Selection Strategy:**
1.  Select Top K groups by `GroupScore`.
2.  Pick Best 1–2 representatives per group by `ExampleScore`.
