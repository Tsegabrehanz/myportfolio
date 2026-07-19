package com.tsegabrehan.einvoicing.config;

import com.tsegabrehan.einvoicing.generation.XRechnungGenerator;
import com.tsegabrehan.einvoicing.validation.En16931Validator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link XRechnungGenerator} and {@link En16931Validator} are deliberately
 * kept free of any Spring (or other framework) dependency — they were
 * written and verified as plain Java, compilable and testable with just
 * {@code javac}/{@code java}, independent of the Maven/Spring build this
 * sandbox couldn't fully exercise (see README). Registering them as beans
 * here, rather than annotating the classes themselves with
 * {@code @Component}, keeps that independence intact.
 */
@Configuration
public class CoreLogicConfig {

    @Bean
    public XRechnungGenerator xRechnungGenerator() {
        return new XRechnungGenerator();
    }

    @Bean
    public En16931Validator en16931Validator() {
        return new En16931Validator();
    }
}
