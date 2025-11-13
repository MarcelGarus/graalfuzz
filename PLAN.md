# Phase 1: Prototype

**Goal:** Fuzz selected user functions.

### Features

VS Code Commands
- Fuzz selected input
- Fuzz selected file

Results Visualization
- Show results in VS Code Output panel

### Architecture

- VS-Code Extension as Frontend
- java CLI as Backend:
    - distribute a `.jar` for easy calling/building as part of VS Code Extensions
- Communication via STDIN/STDOUT
    - language
    - function name
    - code snippet
    - outputs fuzzing results as text

### Languages
- python

# Phase 2: MVP

**Goal:** Fuzz entire loaded code base with nice visualization of input/output pairs.

### Features

Results Visualization
- Per function show input/output pairs
- On hover show code coverage info

### Architecture

- possibly setup a simple JSON-RPC server-client architecture between VS Code Extension & Fuzzer. May reduce startup overhead.
- distribute a native binary via GraalVM Native Image for efficient execution & easy calling/building
    - optionally allow `.jar` distribution for easier local development

### Languages
- python
- javascript
- smalltalk
- lox

# Phase 3: Additional Features

## Package Handling
Automatically handle imports and package dependencies for the code being fuzzed. This can help ensure that the fuzzing process has access to all necessary components and can accurately simulate real-world usage scenarios.

## Fuzzing Configurations
Allow users to specify fuzzing configurations such as:
- Number of inputs to generate
- Input size limits
- Specific edge cases to target

## Internal Variable Settings / Print Statement Catching
Per fuzzed input/output example, visualize the values of the internal variables and/or print statements executed during the run. This can help users understand the internal state changes and behaviors of their functions during fuzzing.

## Example Mining
Scan existing code bases to mine how lower-level functions are being called with what parameters. Use this information to guide the fuzzing process and generate more realistic inputs.

## Hard-Coding Parameters
Allows user to hard-code certain parameters to specific values during fuzzing. This can help focus the fuzzing process on specific scenarios or edge cases. One simple implementation idea would be to go via writing a wrapper function around the original function that sets the hard-coded parameters. Tooling could automatically generate such wrappers.

## Documentation Generation
Automatically generate documentation for the fuzzed functions based on the observed input/output pairs and behaviors. This can help users understand the purpose and functionality of their code.

## Class Fuzzing
Extend the fuzzing capabilities to support entire classes, including methods and attributes. This can help identify issues related to class interactions and state management. Difficulty here are that classes are often stateful.

## Type Inference
Automatically infer types for function parameters and return values based on the observed input/output pairs. This can help improve code quality and maintainability in dynamically typed languages. Difficulty here are determining the class types of objects.

## Defining Custom Samplers
Allow users to define custom input samplers for specific parameter types. This can help generate more relevant and targeted inputs for fuzzing. 

## End-user Development for Output Visualization?
Allow users to write their own html which visualizes the input/output pairs in a custom way.