jtar_version := "2.3"
jtar_jar     := "lib/jtar-" + jtar_version + ".jar"
jtar_url     := "https://repo1.maven.org/maven2/org/kamranzafar/jtar/" + jtar_version + "/jtar-" + jtar_version + ".jar"
jsch_version := "0.1.55"
jsch_jar     := "lib/jsch-" + jsch_version + ".jar"
jsch_url     := "https://repo1.maven.org/maven2/com/jcraft/jsch/" + jsch_version + "/jsch-" + jsch_version + ".jar"

ant_version := "1.10.17"
ant_jar     := "lib/ant-" + ant_version + ".jar"
ant_url     := "https://repo1.maven.org/maven2/org/apache/ant/ant/" + ant_version + "/ant-" + ant_version + ".jar"

xz_version := "1.10"
xz_jar     := "lib/xz-" + xz_version + ".jar"
xz_url     := "https://repo1.maven.org/maven2/org/tukaani/xz/" + xz_version + "/xz-" + xz_version + ".jar"

# Download jtar from Maven Central if it is not already present
deps:
	mkdir -p lib
	[ -f "{{jtar_jar}}" ] || curl -fsSL -o "{{jtar_jar}}" "{{jtar_url}}"
	[ -f "{{ant_jar}}" ] || curl -fsSL -o "{{ant_jar}}" "{{ant_url}}"
	[ -f "{{xz_jar}}" ] || curl -fsSL -o "{{xz_jar}}" "{{xz_url}}"
	[ -f "{{jsch_jar}}" ] || curl -fsSL -o "{{jsch_jar}}" "{{jsch_url}}"

build: deps
	rm -f bin/artifact.jar
	javac -cp "lib/*" -d bin $(find src -type f -name "*.java")
	cd bin && find ../lib -type f -name "*.jar" -exec jar xf {} \;
	jar cvfm bin/artifact.jar MANIFEST.MF -C bin .
run:
	java -jar bin/artifact.jar integration
run-ui: build
	java -cp "bin:lib/*" io.wfs.ui.MainLauncher
run-gui:
	java -jar bin/artifact.jar gui
test-ui: build
	java -cp "bin:lib/*" io.wfs.ui.UiIntegrationTest
test-unit: build
	java -jar bin/artifact.jar unit
test-all: build
	java -jar bin/artifact.jar all-integration
	java -cp "bin:lib/*" io.wfs.ui.UiIntegrationTest
clean:
	rm -rf bin