jtar_version := "2.3"
jtar_jar     := "lib/jtar-" + jtar_version + ".jar"
jtar_url     := "https://repo1.maven.org/maven2/org/kamranzafar/jtar/" + jtar_version + "/jtar-" + jtar_version + ".jar"

commons_compress_version := "1.27.1"
commons_compress_jar     := "lib/commons-compress-" + commons_compress_version + ".jar"
commons_compress_url     := "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/" + commons_compress_version + "/commons-compress-" + commons_compress_version + ".jar"

xz_version := "1.10"
xz_jar     := "lib/xz-" + xz_version + ".jar"
xz_url     := "https://repo1.maven.org/maven2/org/tukaani/xz/" + xz_version + "/xz-" + xz_version + ".jar"

commons_io_version := "2.18.0"
commons_io_jar     := "lib/commons-io-" + commons_io_version + ".jar"
commons_io_url     := "https://repo1.maven.org/maven2/commons-io/commons-io/" + commons_io_version + "/commons-io-" + commons_io_version + ".jar"

# Download jtar from Maven Central if it is not already present
deps:
	mkdir -p lib
	[ -f "{{jtar_jar}}" ] || curl -fsSL -o "{{jtar_jar}}" "{{jtar_url}}"
	[ -f "{{commons_compress_jar}}" ] || curl -fsSL -o "{{commons_compress_jar}}" "{{commons_compress_url}}"
	[ -f "{{xz_jar}}" ] || curl -fsSL -o "{{xz_jar}}" "{{xz_url}}"
	[ -f "{{commons_io_jar}}" ] || curl -fsSL -o "{{commons_io_jar}}" "{{commons_io_url}}"

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