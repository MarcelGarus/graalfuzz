# Bugs and TODOs in GraalFuzz Fuzzer

## Python Dictionary/List subscripting error

When running the fuzzer on the following code, the following error occurs:

```python
    def get_point_id(point):
        return f"lat<{point['lat']}>lon<{point['lon']}>"
```

```
    TypeError: 'polyglot.ForeignObject' object is not subscriptable
```

The problem is that python dictionaries are not accessed via "." but via "[]". The fuzzer currently does not seem to handle this case correctly.

Same problem occurs here for list subscripting:

```python
    def sum(some_list):
        return some_list[0] + some_list[1]
```

## Multi Argument Functions
When fuzzing functions with multiple arguments, the fuzzer currently only generates a single argument.
