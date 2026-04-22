package io.wfs.sftpserver;

import io.wfs.sftpserver.config.SftpServerConfiguration;
import java.util.concurrent.CountDownLatch;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public final class SftpServerApplication {

    private SftpServerApplication() {
    }

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SftpServerConfiguration.class);
        application.setBannerMode(Banner.Mode.OFF);
        ConfigurableApplicationContext context = application.run(args);

        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            context.close();
            shutdownLatch.countDown();
        }, "weefs-sftp-server-shutdown"));

        try {
            shutdownLatch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            context.close();
        }
    }
}
