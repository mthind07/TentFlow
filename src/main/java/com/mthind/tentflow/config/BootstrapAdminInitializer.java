package com.mthind.tentflow.config;

import com.mthind.tentflow.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            BootstrapAdminInitializer.class
    );

    private final TentFlowSecurityProperties properties;
    private final AccountService accountService;

    public BootstrapAdminInitializer(
            TentFlowSecurityProperties properties,
            AccountService accountService
    ) {
        this.properties = properties;
        this.accountService = accountService;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        boolean hasEmail = hasText(properties.bootstrapAdminEmail());
        boolean hasPassword = hasText(properties.bootstrapAdminPassword());

        if (!hasEmail && !hasPassword) {
            LOGGER.info(
                    "Bootstrap administrator is not configured; "
                            + "existing accounts are unchanged."
            );
            return;
        }

        if (hasEmail != hasPassword) {
            throw new IllegalStateException(
                    "Set both TENTFLOW_BOOTSTRAP_ADMIN_EMAIL and "
                            + "TENTFLOW_BOOTSTRAP_ADMIN_PASSWORD, or neither."
            );
        }

        accountService.ensureBootstrapAdministrator(
                properties.bootstrapAdminEmail(),
                properties.bootstrapAdminPassword()
        );
        LOGGER.info("Bootstrap administrator check completed.");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}