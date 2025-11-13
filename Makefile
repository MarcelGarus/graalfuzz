
# Just for convenience....

run: classpath
	./graalfuzz

test:
	mvn -e clean test

classpath: force
	mvn -q exec:exec -Dexec.executable=echo -Dexec.args="%classpath" > classpath

compile:
	mvn clean compile

native:
	mvn -P native package

force:

# Compile the TypeScript code for the VSCode extension
vscode-extension-compile:
	cd vscode-extension && npm install && npm run compile
	cd ..
