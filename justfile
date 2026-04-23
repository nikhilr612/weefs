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

spring_boot_version := "2.7.18"
spring_boot_jar     := "lib/spring-boot-" + spring_boot_version + ".jar"
spring_boot_url     := "https://repo1.maven.org/maven2/org/springframework/boot/spring-boot/" + spring_boot_version + "/spring-boot-" + spring_boot_version + ".jar"

spring_version := "5.3.31"
spring_context_jar     := "lib/spring-context-" + spring_version + ".jar"
spring_context_url     := "https://repo1.maven.org/maven2/org/springframework/spring-context/" + spring_version + "/spring-context-" + spring_version + ".jar"

spring_core_jar     := "lib/spring-core-" + spring_version + ".jar"
spring_core_url     := "https://repo1.maven.org/maven2/org/springframework/spring-core/" + spring_version + "/spring-core-" + spring_version + ".jar"

spring_beans_jar     := "lib/spring-beans-" + spring_version + ".jar"
spring_beans_url     := "https://repo1.maven.org/maven2/org/springframework/spring-beans/" + spring_version + "/spring-beans-" + spring_version + ".jar"

spring_expression_jar     := "lib/spring-expression-" + spring_version + ".jar"
spring_expression_url     := "https://repo1.maven.org/maven2/org/springframework/spring-expression/" + spring_version + "/spring-expression-" + spring_version + ".jar"

spring_aop_jar     := "lib/spring-aop-" + spring_version + ".jar"
spring_aop_url     := "https://repo1.maven.org/maven2/org/springframework/spring-aop/" + spring_version + "/spring-aop-" + spring_version + ".jar"

spring_jcl_jar     := "lib/spring-jcl-" + spring_version + ".jar"
spring_jcl_url     := "https://repo1.maven.org/maven2/org/springframework/spring-jcl/" + spring_version + "/spring-jcl-" + spring_version + ".jar"

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
	[ -f "{{spring_boot_jar}}" ] || curl -fsSL -o "{{spring_boot_jar}}" "{{spring_boot_url}}"
	[ -f "{{spring_context_jar}}" ] || curl -fsSL -o "{{spring_context_jar}}" "{{spring_context_url}}"
	[ -f "{{spring_core_jar}}" ] || curl -fsSL -o "{{spring_core_jar}}" "{{spring_core_url}}"
	[ -f "{{spring_beans_jar}}" ] || curl -fsSL -o "{{spring_beans_jar}}" "{{spring_beans_url}}"
	[ -f "{{spring_expression_jar}}" ] || curl -fsSL -o "{{spring_expression_jar}}" "{{spring_expression_url}}"
	[ -f "{{spring_aop_jar}}" ] || curl -fsSL -o "{{spring_aop_jar}}" "{{spring_aop_url}}"
	[ -f "{{spring_jcl_jar}}" ] || curl -fsSL -o "{{spring_jcl_jar}}" "{{spring_jcl_url}}"

build: deps
	rm -rf bin
	mkdir -p bin
	javac -cp "lib/*" -d bin $(find src -type f -name "*.java")
	cd bin && find ../lib -type f -name "*.jar" -exec jar xf {} \;
	rm -f bin/module-info.class
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