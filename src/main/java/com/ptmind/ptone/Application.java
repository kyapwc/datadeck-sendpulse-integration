package com.ptmind.ptone;

import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class Application {
    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(Application.class, args);

        String[] activeProfiles = context.getEnvironment().getActiveProfiles();

        String env = "default";

        if (!ArrayUtils.isEmpty(activeProfiles)) {
            env = activeProfiles[0];
        }

        logger.info("Current Environment: " + env);
    }
}
