# graalfuzz VS Code Extension README

This is the README for your extension "graalfuzz". After writing up a brief description, we recommend including the following sections.

## Features

Run the GraalFuzz fuzzer on the currently opened file.
The code has to evaluate to a function definition. Therefore, the end of the file should contain a reference to the function to be fuzzed, e.g.,

```python
def square(a):
    return a**2

square  # reference to function to be fuzzed
```

The fuzzer output will be shown in the "GraalFuzz Fuzzer" output panel.

## Requirements

Currently, the extension assumes that the `graalfuzz.sh` (or `graalfuzz.cmd` on Windows) script is located in the parent directory of the extension folder. 
TODO: In the future it will be distributed as part of the extension package.

## Extension Settings

TODO

This extension contributes the following settings:

* `myExtension.enable`: Enable/disable this extension.
* `myExtension.thing`: Set to `blah` to do something.

## Known Issues

Under current development:
- The fuzzer currently only supports Python code. Support for additional languages will be added in future releases.
- The fuzzer assumes that the code being fuzzed does not have any external dependencies. Handling of imports and package dependencies will be added in future releases.
- The fuzzer currently does not support functions with multiple arguments. This will be addressed in future releases.
- The fuzzer output can not show colored codes and therefore contains some escape sequences.

## Release Notes

Users appreciate release notes as you update your extension.

### 1.0.0

Initial release of graalfuzz.
- Run GraalFuzz fuzzer on the currently opened file.
- The fuzzer output will be shown in the "GraalFuzz Fuzzer" output panel.

---

**Enjoy!**
