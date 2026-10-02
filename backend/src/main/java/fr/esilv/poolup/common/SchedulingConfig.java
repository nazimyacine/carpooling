package fr.esilv.poolup.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on {@code @Scheduled} methods (e.g. the nightly trip completion job). */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
