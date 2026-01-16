# Heuristics for Fuzzer Example Prioritization

This document defines a language-agnostic set of heuristics for prioritizing and grouping fuzzer-generated examples. The goal is to select the top most relevant representative examples for function behavior summarization.

## Scoring Framework

All heuristics aim to **minimize scores**. Lower scores are considered better and are prioritized in the output. This framework allows for flexible weighting of different heuristics while maintaining consistent semantics.

## 3-Layer Grouping Pipeline

Organize examples into groups to reduce entropy and surface important cases. The order of layers matters.

### 1. Group by Input Type / Shape
*Most important dimension of functional behavior.*
Group examples by their input structure (e.g., `number`, `string`, `null`, `array<number>`, `object{a:number}`).
*   **Rationale:** Different types lead to different code paths and meaningful behaviors.
*   **Ordering:** Shapes leading to diverse outputs or exceptions rank higher.

### 2. Group by Coverage / Path Hash / Output
*Second most important dimension: distinct branches → distinct behaviors → distinct output.*
Inside input groups, group executions by a path hash (e.g., XOR of branch IDs).
*   **Captures:** Conditionals, nested decisions, early returns, loops.
*   **Ordering:** Rare paths (corner cases) > Minimal paths (baseline) > Very long paths (complex behavior).
*   **Output Type:** Each path (assuming deterministic behavior) specifies an exact output type.

#### 2a. Group by Exception Type
*Essential to show crash/failure modes separately.*
Inside path groups, split into:
*   No exception
*   Exception (by type)
*   **Ordering:** Exceptions always rank high. Rare exception types outrank common ones.

#### 2b. Group by Output Type / Shape
*Final grouping inside each behavioral group.*
Group by output structure (e.g., `number`, `boolean`, `void`, `undefined`).
*   **Ordering:** Rare output types, outliers, or structural changes are prioritized.

---

## Scoring Heuristics

These rules help pick the best representative example inside each group and prioritize the groups themselves.


### Group-Level Heuristics (Key Heuristics)

**H1: Coverage Rarity** (`CoverageRarity`)
- **Purpose:** Prioritize rare execution paths that indicate edge cases or corner cases.
- **Implementation:** Calculates `1 - P(path)` where P(path) is debiased over input shapes to account for fuzzer bias.
- **Formula:**
  ```
  P(path) = Σ_s P(path|input_shape=s) * (1/|S|)
  score = 1 - P(path)
  ```
- **Weight:** 2.0 (double importance)
- **Semantics:** Returns probability of NOT hitting the path (rarity). Lower values = more common paths.

**H2: Path Simplicity** (`PathSimplicity`)
- **Purpose:** Prioritize short execution paths as they indicate simpler behaviors and edge cases.
- **Implementation:** Inverts path length to score shorter paths better.
- **Formula:** `score = -path_length`
- **Weight:** 1.0
- **Semantics:** Shorter paths get more negative scores (better).

**H3: Input Shape Simplicity** (`InputShapeSimplicity`)
- **Purpose:** Prefer inputs with simpler structures for clarity and interpretability.
- **Implementation:** Counts object field depth and inverts the complexity.
- **Formula:** `score = -complexity(shape)` where complexity counts nested object fields.
- **Weight:** 10.0 (higher importance)
- **Semantics:** More negative = simpler shape.

**H4: Output Shape Diversity** (`OutputShapeDiversity`)
- **Purpose:** Identify input shapes that produce diverse (unexpected) output types.
- **Implementation:** Calculates output type distribution variance for each input shape.
- **Formula:**
  ```
  variance = Σ(p_i - mean)² / |output_types|
  score = variance * 4  (normalized assuming max variance ≈ 0.25)
  ```
- **Weight:** 1.0
- **Semantics:** Higher variance (more diverse outputs) = higher score = more interesting.

**H5: Input Validity** (`InputValidity`)
- **Purpose:** Identify input shapes that produce both successful and failing outputs (edge case indicators).
- **Implementation:** Scores based on crash distribution for each input shape.
- **Formula:** Custom logic based on crash ratio.
- **Weight:** 100.0 (highest importance)
- **Semantics:** Inputs that sometimes crash but sometimes succeed are highly prioritized.

### Example-Level Heuristics (Item Heuristics)

**E1: Minimal Input** (`MinimalInput`)
- **Purpose:** Select the smallest input as the representative for clarity and minimization.
- **Implementation:** Calculates input size using logarithmic scaling for numeric values and counts for objects/strings. Inverts to minimize.
- **Formula:**
  ```
  size = Σ field_lengths + log₁₀(|numeric_values|) + string_lengths
  score = -size  (inverted)
  ```
- **Weight:** 5.0
- **Semantics:** More negative = smaller input = preferred.
- **Normalization:** Min-Max normalization across all inputs.

**E2: Minimal Output** (`MinimalOutput`)
- **Purpose:** Select examples with concise outputs for readability.
- **Implementation:** Measures output length and inverts the score.
- **Formula:** `score = -output_length`
- **Weight:** 1.0
- **Semantics:** More negative = shorter output = preferred.
- **Normalization:** Min-Max normalization across all outputs.

---

## Scoring Process

### Group Scoring
1. For each group (composite key), apply all registered key heuristics.
2. Multiply each heuristic score by its weight.
3. Sum weighted scores and normalize by total weight sum.
4. Groups with lower scores rank higher.

### Example Scoring Within a Group
1. For each example (RunResult), apply all item heuristics.
2. Multiply each score by its weight.
3. Sum weighted scores and normalize by total weight sum.
4. Examples within a group with lower scores are selected as representatives.

### Composite Key Handling
Composite keys (Composite GroupKey) recursively apply heuristics to all sub-keys and aggregate scores by summing individual contributions.

---

## Selection Strategy

1. **Prepare Phase:** All heuristics analyze the entire result set and prepare normalization parameters (min/max for Min-Max normalization).
2. **Group Phase:** Results are deduplicated and grouped by the grouping strategy (e.g., composite groups by input shape, path, output, exception).
3. **Score Phase:** Each group and its examples are scored using the heuristics above.
4. **Sort Phase:** Groups are sorted by score (ascending), and examples within groups are sorted by score (ascending).
5. **Output Phase:** Top K groups are selected, with 1-3 representative examples per group based on scores.

