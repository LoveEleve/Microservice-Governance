package io.microsphere.labs.spring.framework;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(FeatureSelector.class)
public class ImportConfig {
}
