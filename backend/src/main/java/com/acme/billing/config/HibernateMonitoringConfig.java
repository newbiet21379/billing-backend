package com.acme.billing.config;

import com.acme.billing.monitoring.database.HibernateQueryEventListener;
import com.acme.billing.monitoring.database.QueryPerformanceInterceptor;
import org.hibernate.SessionFactory;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.internal.SessionFactoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Configuration for Hibernate monitoring components.
 *
 * Registers Hibernate event listeners and statement inspectors
 * for comprehensive database performance monitoring.
 */
@Configuration
@EnableScheduling
public class HibernateMonitoringConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final QueryPerformanceInterceptor queryPerformanceInterceptor;
    private final HibernateQueryEventListener eventListener;

    @Autowired
    public HibernateMonitoringConfig(
            EntityManagerFactory entityManagerFactory,
            QueryPerformanceInterceptor queryPerformanceInterceptor,
            HibernateQueryEventListener eventListener) {
        this.entityManagerFactory = entityManagerFactory;
        this.queryPerformanceInterceptor = queryPerformanceInterceptor;
        this.eventListener = eventListener;
    }

    /**
     * Registers Hibernate event listeners after the application context is initialized.
     */
    @PostConstruct
    public void registerHibernateListeners() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        SessionFactoryImpl sessionFactoryImpl = (SessionFactoryImpl) sessionFactory;

        EventListenerRegistry registry = sessionFactoryImpl.getServiceRegistry()
            .getService(EventListenerRegistry.class);

        // Register entity event listeners
        registry.getEventListenerGroup(EventType.PRE_LOAD).appendListener(eventListener.preLoadEventListener());
        registry.getEventListenerGroup(EventType.POST_LOAD).appendListener(eventListener.postLoadEventListener());

        registry.getEventListenerGroup(EventType.PRE_INSERT).appendListener(eventListener.preInsertEventListener());
        registry.getEventListenerGroup(EventType.POST_INSERT).appendListener(eventListener.postInsertEventListener());

        registry.getEventListenerGroup(EventType.PRE_UPDATE).appendListener(eventListener.preUpdateEventListener());
        registry.getEventListenerGroup(EventType.POST_UPDATE).appendListener(eventListener.postUpdateEventListener());

        registry.getEventListenerGroup(EventType.PRE_DELETE).appendListener(eventListener.preDeleteEventListener());
        registry.getEventListenerGroup(EventType.POST_DELETE).appendListener(eventListener.postDeleteEventListener());

        registry.getEventListenerGroup(EventType.SAVE_UPDATE).appendListener(eventListener.transactionCompletionEventListener());

        // Set the statement inspector for query performance monitoring
        sessionFactoryImpl.getStatementInspectorRegistry().registerInspector(queryPerformanceInterceptor);
    }
}