
# Just for convenience....

run: classpath
	./lox

test:
	mvn -e clean test

classpath: force
	mvn -q exec:exec -Dexec.executable=echo -Dexec.args="%classpath" > classpath

native:
	mvn -P native package

force:
