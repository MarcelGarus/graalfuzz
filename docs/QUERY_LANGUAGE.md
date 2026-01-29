# FUZZ Query Language (FQL)

The FUZZ Query Language (FQL) is a declarative query specification language for analyzing fuzzing
results. It is designed for end-user development: expressive enough for advanced analysis while
enforcing a fixed execution model and preventing invalid or ambiguous queries.

FQL queries describe what data is needed, not how it is computed. Execution order is fixed and
implicit.

---

## Overview

An FQL query consists of three main sections:

- `FUZZ <function>`: defines the function to fuzz
- `CONFIG`: execution configuration
- `ROW`: row-level filtering, projection, sorting, and limiting
- `GROUP`: grouping, aggregation, group-level filtering, sorting, and limiting

Example structure:

```text
FUZZ <function> {
  CONFIG { ... }
  ROW    { ... }
  GROUP  { ... }
}
```

## CONFIG

The `CONFIG` section controls how the query is executed. Example:

```text
CONFIG {
  RUNS 1000
  EXCLUDE_ERRORS AttributeError, TypeError
}
```

Fields

| Field          | Description |
|----------------|-------------:|
| RUNS           | Maximum number of fuzzing runs or samples to consider |
| EXCLUDE_ERRORS | Exception types to exclude from analysis (comma-separated) |

The `EXCLUDE_ERRORS` field filters out rows where the exception type matches any of the listed
values. Default excluded errors for Python: `AttributeError`, `TypeError`.

## ROW

The `ROW` section operates on individual rows (single fuzzing results) before grouping. Operations
are applied in the following fixed order and cannot be changed by the user:

1. `WHERE` (row-level filtering)
2. `DEDUPLICATE` (deduplication)
3. `SELECT` (projection)
4. `SORT` (ordering)
5. `LIMIT` (row count)

Example:

```text
ROW {
  SELECT inputType, outputType, didError
  WHERE  inputType = Int
  DEDUPLICATE inputValue
  SORT   score DESC
  LIMIT  3
}
```

- SELECT: chooses which columns are visible downstream. Columns may be raw or computed (virtual
  fields).
- WHERE: filters rows using boolean predicates. Supports `=`, `!=`, `<`, `<=`, `>`, `>=` and boolean
  composition with `AND`, `OR`, `NOT`. Predefined predicates are treated as virtual boolean
  columns. Example: `WHERE didError = true AND NOT isTimeout`.
- DEDUPLICATE: removes duplicate rows based on specified columns. Only the first occurrence of each
  unique value is kept. Example: `DEDUPLICATE inputValue`.
- SORT: sorts rows by one or more columns. Default direction is `ASC`. Sorting is stable and
  deterministic. Example: `SORT score DESC`.
- LIMIT: limits the number of rows passed to grouping. Example: `LIMIT 3`.

## GROUP

The `GROUP` section aggregates rows into groups and operates on grouped data. A `GROUP` block may
contain type and key declarations, aggregates, and group-level filtering/sorting/limitations.

Example:

```text
GROUP {
  TYPE FLAT | NESTED | HIERARCHICAL
  KEYS inputType, outputType

  AGGREGATE {
    count()         AS count
    count(didError) AS errors
  }

  WHERE errors != count
  SORT  count DESC
  LIMIT 10

  DRILL NONE | LEAF | ALL
}
```

### TYPE

Defines how grouping keys are interpreted.

| Type         | Description |
|--------------|-------------:|
| FLAT         | Combined key `(A, B)` - single group level       |
| NESTED       | Hierarchical grouping: first key, then subgroups |
| HIERARCHICAL | Nested groups with per-level aggregations        |

Example (nested keys): `KEYS inputType, outputType` produces a hierarchy like:

```
inputType
 └─ outputType
```

### HIERARCHICAL Grouping

The `HIERARCHICAL` type allows grouping by a previous level's aggregation, and defining different aggregations at each level of the grouping hierarchy.
This enables complex analyses where aggregations computed at one level are used in subsequent levels.

Example:

```text
GROUP {
  TYPE HIERARCHICAL
  KEYS outputType {
    AGGREGATE {
      distinctSet(inputShape) AS inputShapes
    }
  }
  SUBGROUP inputShapes {
    AGGREGATE {
      distinctSet(outputType) AS outputTypes
    }
  }
}
```

In this example:
1. First, we group by `outputType` and compute the distinct set of input shapes
2. Then, we can regroup by the `inputShapes` aggregation result
3. Each subgroup has its own aggregations computed
This can be nested to arbitrary depth.

This is particularly useful for:
- Computing type signatures (union of inputs → union of outputs)
- Analyzing error patterns across different input types
- Creating pivot-like analyses


### KEYS

Defines grouping keys. Keys may include computed columns. Order matters for `NESTED` and
`HIERARCHICAL` grouping.

### AGGREGATE

Defines aggregate functions computed per group. Aliases are mandatory; aggregates become
available as group-level columns and may be used in `WHERE`, `SORT`, and `SELECT`.

Example:

```text
AGGREGATE {
  count()         AS count
  count(didError) AS errors
}
```

### WHERE (Group-Level Filtering)

Filters groups after aggregation. Uses aggregate aliases and group keys; same boolean semantics as
row-level filtering. Example: `WHERE errors != count`.

### SORT (Group-Level Sorting)

Sorts groups. Example: `SORT count DESC`.

### LIMIT (Group-Level)

Limits the number of groups returned (applies to top-level groups). Child limits are controlled via
`DRILL`. Example: `LIMIT 10`.

### DRILL

Controls how deep grouped results are expanded.

| Mode | Description |
|------|-------------:|
| NONE | Only top-level groups |
| LEAF | Only leaf groups |
| ALL  | Fully expanded hierarchy |

## Computed Columns and Predicates

FQL provides predefined computed columns and predicates. Computed columns behave like regular
columns; predicates are boolean computed columns. Users cannot define new functions.

Examples:

```text
WHERE isError AND NOT isTimeout
GROUP KEYS inputType, errorCategory
```

## Predefined Queries

FuzzLens provides predefined queries for common analysis patterns:

### Hover Provider Queries

| Query | Description |
|-------|-------------|
| `relevantPairs` | Input/output pairs grouped by shape and coverage path |
| `inverseTreeList` | Inverse tree: output types → input shapes |
| `treeList` | Tree view: input shapes → output types |
| `inputShapeUnion` | Union of all input shapes, excluding noisy errors |
| `outputTypeUnion` | Union of all output types, excluding noisy errors |
| `observedSignature` | Union of input shapes and output types together |
| `inputShapeToOutputShapes` | Group by input shape → union of output shapes |
| `outputTypeToInputShapes` | Group by output type → union of input shapes |
| `exceptionExamples` | Examples of each exception type with counts |
| `validExamples` | One example per (input type, output type) pair |
| `allExamples` | All examples grouped by (input shape, output shape) |
| `detailed` | Detailed grouping with traces and exceptions |
| `basic` | Basic input shape grouping |

### Legacy Queries

| Query | Description |
|-------|-------------|
| `observedOutputTypes` | Output types with input type counts |
| `inputShapesToOutputTypes` | Input shapes with output type counts |
| `nestedInputShapes` | Property-level shape analysis |

## Error Filtering

Queries can exclude specific exception types from analysis. This is useful for filtering out
"noisy" errors that don't represent meaningful program behavior but instead indicate wrong input types.

Default excluded exceptions (configurable):
- `AttributeError` (Python): Usually indicates wrong type passed
- `TypeError`: Often indicates wrong argument type
- `KeyError`: (Python) Missing dictionary keys
- `Uncaught TypeError` (JavaScript): Wrong type passed to function

These are configured in `QueryCatalog.DEFAULT_EXCLUDED_EXCEPTIONS`.

## Design Principles

- Declarative, not procedural
- Fixed execution order
- No user-controlled execution plans
- Canonical, round-trippable syntax
- UI-first design

## Full Example Query

```text
FUZZ foo {

  CONFIG {
    RUNS 1000
    EXCLUDE_ERRORS AttributeError, TypeError, KeyError, "Uncaught TypeError"
  }

  ROW {
    SELECT inputType, outputType, didError
    WHERE  inputType = Int
    DEDUPLICATE inputValue
    SORT   score DESC
    LIMIT  3
  }

  GROUP {
    TYPE FLAT
    KEYS inputType, outputType

    AGGREGATE {
      count()         AS count
      count(didError) AS errors
      distinctSet(outputShape) AS outputShapes
    }

    WHERE errors != count
    SORT  count DESC
    LIMIT 10

    DRILL LEAF
  }
}
```

## Summary

FQL is a structured analysis specification language optimized for fuzzing data. It balances expressiveness with safety by enforcing a fixed execution model and a clear separation between row-level and group-level operations.