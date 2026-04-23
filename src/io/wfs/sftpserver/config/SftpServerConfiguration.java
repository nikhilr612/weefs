package io.wfs.sftpserver.config;

import io.wfs.sftpserver.SftpServerController;
import io.wfs.sftpserver.adapter.VfsSftpFileSystemView;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class SftpServerConfiguration {

    @Bean
    SftpServerProperties sftpServerProperties(Environment environment) {
        return SftpServerProperties.from(environment);
    }

    @Bean(destroyMethod = "close")
    ArchiveVfsMount archiveVfsMount(SftpServerProperties properties) throws IOException {
        return ArchiveVfsMount.open(properties);
    }

    @Bean
    VfsSftpFileSystemView vfsSftpFileSystemView(ArchiveVfsMount archiveVfsMount) {
        return new VfsSftpFileSystemView(archiveVfsMount);
    }

    @Bean
    SftpServerController sftpServerController(
            SftpServerProperties properties,
            VfsSftpFileSystemView fileSystemView,
            ArchiveVfsMount archiveVfsMount) {
        return new SftpServerController(properties, fileSystemView, archiveVfsMount);
    }
}
