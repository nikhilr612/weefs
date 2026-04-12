jtar_version := "2.3"
jtar_jar     := "lib/jtar-" + jtar_version + ".jar"
jtar_url     := "https://repo1.maven.org/maven2/org/kamranzafar/jtar/" + jtar_version + "/jtar-" + jtar_version + ".jar"

# Download jtar from Maven Central if it is not already present
deps:
	mkdir -p lib
	[ -f "{{jtar_jar}}" ] || curl -fsSL -o "{{jtar_jar}}" "{{jtar_url}}"

build: deps
	rm -f bin/artifact.jar
	javac -cp "lib/*" -d bin $(find src -type f -name "*.java")
	cd bin && find ../lib -type f -name "*.jar" -exec jar xf {} \;
	jar cvfm bin/artifact.jar MANIFEST.MF -C bin .
run:
	java -jar bin/artifact.jar integration
run-gui:
	java -jar bin/artifact.jar gui
clean:
	rm -rf bin