package com.acme.billing.config;

import com.acme.billing.monitoring.interceptor.PerformanceMonitoringInterceptor;
import com.acme.billing.monitoring.logging.CorrelationIdFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for monitoring components.
 *
 * Registers:
 * - Correlation ID filter for request tracing
 * - Performance monitoring interceptor for API metrics
 * - Other monitoring-related components
 */
@Configuration
public class MonitoringConfig implements WebMvcConfigurer {

    private final PerformanceMonitoringInterceptor performanceInterceptor;

    @Autowired
    public MonitoringConfig(PerformanceMonitoringInterceptor performanceInterceptor) {
        this.performanceInterceptor = performanceInterceptor;
    }

    /**
     * Registers the correlation ID filter.
     */
    @Bean
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new CorrelationIdFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(1); // High priority to run first
        registrationBean.setName("correlationIdFilter");
        return registrationBean;
    }

    /**
     * Registers performance monitoring interceptor.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(performanceInterceptor)
            .addPathPatterns("/api/**", "/actuator/**")
            .excludePathPatterns("/actuator/health", "/actuator/health/**")
            .order(2);
    }
}