package com.huawei.opsfactory.finops;

import com.huawei.opsfactory.finops.config.FinOpsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the FinOps service.
 *
 * @since 2026-05-28
 */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(FinOpsProperties.class)
public class FinOpsApplication {

    /**
     * Application entry point.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(FinOpsApplication.class, args);
    }
}
