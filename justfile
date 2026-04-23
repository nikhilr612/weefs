jtar_version := "2.3"
jtar_jar     := "lib/jtar-" + jtar_version + ".jar"
jtar_url     := "https://repo1.maven.org/maven2/org/kamranzafar/jtar/" + jtar_version + "/jtar-" + jtar_version + ".jar"
jsch_version := "0.1.55"
jsch_jar     := "lib/jsch-" + jsch_version + ".jar"
jsch_url     := "https://repo1.maven.org/maven2/com/jcraft/jsch/" + jsch_version + "/jsch-" + jsch_version + ".jar"

commons_compress_version := "1.27.1"
commons_compress_jar     := "lib/commons-compress-" + commons_compress_version + ".jar"
commons_compress_url     := "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/" + commons_compress_version + "/commons-compress-" + commons_compress_version + ".jar"

xz_version := "1.10"
xz_jar     := "lib/xz-" + xz_version + ".jar"
xz_url     := "https://repo1.maven.org/maven2/org/tukaani/xz/" + xz_version + "/xz-" + xz_version + ".jar"

commons_io_version := "2.18.0"
commons_io_jar     := "lib/commons-io-" + commons_io_version + ".jar"
commons_io_url     := "https://repo1.maven.org/maven2/commons-io/commons-io/" + commons_io_version + "/commons-io-" + commons_io_version + ".jar"

sshd_core_version := "2.11.0"
sshd_core_jar     := "lib/sshd-core-" + sshd_core_version + ".jar"
sshd_core_url     := "https://repo1.maven.org/maven2/org/apache/sshd/sshd-core/" + sshd_core_version + "/sshd-core-" + sshd_core_version + ".jar"

sshd_sftp_version := "2.11.0"
sshd_sftp_jar     := "lib/sshd-sftp-" + sshd_sftp_version + ".jar"
sshd_sftp_url     := "https://repo1.maven.org/maven2/org/apache/sshd/sshd-sftp/" + sshd_sftp_version + "/sshd-sftp-" + sshd_sftp_version + ".jar"

sshd_common_version := "2.17.1"
sshd_common_jar     := "lib/sshd-common-" + sshd_common_version + ".jar"
sshd_common_url     := "https://repo1.maven.org/maven2/org/apache/sshd/sshd-common/" + sshd_common_version + "/sshd-common-" + sshd_common_version + ".jar"

slf4j_version := "2.0.16"
slf4j_api_jar := "lib/slf4j-api-" + slf4j_version + ".jar"
slf4j_api_url := "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/" + slf4j_version + "/slf4j-api-" + slf4j_version + ".jar"
slf4j_simple_jar := "lib/slf4j-simple-" + slf4j_version + ".jar"
slf4j_simple_url := "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/" + slf4j_version + "/slf4j-simple-" + slf4j_version + ".jar"

# Download dependencies from Maven Central if not already present
flatlaf_version := "3.5.4"
flatlaf_jar     := "lib/flatlaf-" + flatlaf_version + ".jar"
flatlaf_url     := "https://repo1.maven.org/maven2/com/formdev/flatlaf/" + flatlaf_version + "/flatlaf-" + flatlaf_version + ".jar"

# Download jtar from Maven Central if it is not already present
deps:
	mkdir -p lib
	[ -f "{{jtar_jar}}" ] || curl -fsSL -o "{{jtar_jar}}" "{{jtar_url}}"
	[ -f "{{commons_compress_jar}}" ] || curl -fsSL -o "{{commons_compress_jar}}" "{{commons_compress_url}}"
	[ -f "{{xz_jar}}" ] || curl -fsSL -o "{{xz_jar}}" "{{xz_url}}"
	[ -f "{{commons_io_jar}}" ] || curl -fsSL -o "{{commons_io_jar}}" "{{commons_io_url}}"
	[ -f "{{jsch_jar}}" ] || curl -fsSL -o "{{jsch_jar}}" "{{jsch_url}}"
	[ -f "{{sshd_core_jar}}" ] || curl -fsSL -o "{{sshd_core_jar}}" "{{sshd_core_url}}"
	[ -f "{{sshd_sftp_jar}}" ] || curl -fsSL -o "{{sshd_sftp_jar}}" "{{sshd_sftp_url}}"
	[ -f "{{sshd_common_jar}}" ]  || curl -fsSL -o "{{sshd_common_jar}}"  "{{sshd_common_url}}"
	[ -f "{{slf4j_api_jar}}" ]    || curl -fsSL -o "{{slf4j_api_jar}}"    "{{slf4j_api_url}}"
	[ -f "{{slf4j_simple_jar}}" ] || curl -fsSL -o "{{slf4j_simple_jar}}" "{{slf4j_simple_url}}"
	[ -f "{{flatlaf_jar}}" ] || curl -fsSL -o "{{flatlaf_jar}}" "{{flatlaf_url}}"

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
run-sftp-server: build
	java -jar bin/artifact.jar sftp-server archive.zip 8888 dev dev
test-ui: build
	java -cp "bin:lib/*" io.wfs.ui.UiIntegrationTest
test-unit: build
	java -jar bin/artifact.jar unit
test-all: build
	java -jar bin/artifact.jar all-integration
	java -cp "bin:lib/*" io.wfs.ui.UiIntegrationTest
clean:
	rm -rf bin