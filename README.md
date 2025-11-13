# GraalFuzz 

Fuzzing dynamic languages implemented with GraalVM's Truffle framework.

It also contains a Lox implementation for testing purposes.
The Lox implementation is developed as part of the Build Your Own Programming Language course at Software Architecture Group, Hasso Plattner Institute, Potsdam.

## Java Maven Project

### Getting Started

For development, you can use Oracle JDK 21, OpenJDK 21, or any of its derivatives.
Use GraalVM version 25.0.1 (https://www.graalvm.org/downloads/).

### Maven

To directly use the command line, this might be helpful:

#### Compile ...

```bash
mvn compile
```

Or for native image:
```bash
mvn -P native package
```

#### Run Tests

```bash
mvn test
```

#### Running the main class

After compiling
Run `graalfuzz.cmd` (Windows) or `./graalfuzz` (Unix).

#### Cleanup

```bash
mvn clean
```

## VS Code Extension
See [vscode-extension/README.md](vscode-extension/README.md) for details.