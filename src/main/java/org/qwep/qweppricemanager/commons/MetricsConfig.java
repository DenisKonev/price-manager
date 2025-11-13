package org.qwep.qweppricemanager.commons;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@Configuration
@Slf4j
@Getter
@EnableAspectJAutoProxy
public class MetricsConfig {

    @Value("${POD_NAME:defaultPodName}")
    private String podName;

    @Value("${NAMESPACE:defaultNamespace}")
    private String namespace;


    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry
                .config()
                .commonTags("pod_name", podName)
                .commonTags("namespace", namespace);
    }

    @Bean
    public TimedAspect timedAspect(MeterRegistry registry) {
        return new TimedAspect(registry);
    }

}
