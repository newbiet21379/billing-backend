package com.acme.billing.monitoring.database;

import org.hibernate.HibernateException;
import org.hibernate.event.spi.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hibernate event listener for tracking query performance and database operations.
 *
 * This listener integrates with Hibernate's event system to:
 * - Track query execution times
 * - Monitor connection and transaction lifecycle
 * - Record entity operation metrics
 * - Log database operation details
 */
@Component
public class HibernateQueryEventListener {

    private static final Logger logger = LoggerFactory.getLogger(HibernateQueryEventListener.class);
    private final QueryPerformanceInterceptor queryPerformanceInterceptor;

    public HibernateQueryEventListener(QueryPerformanceInterceptor queryPerformanceInterceptor) {
        this.queryPerformanceInterceptor = queryPerformanceInterceptor;
    }

    /**
     * Pre-load event listener.
     */
    public class PreLoadEventListener implements PreLoadEventListener {
        @Override
        public void onPreLoad(PreLoadEvent event) {
            // Record pre-load metrics
            String entityName = event.getEntity().getClass().getSimpleName();
            logger.debug("Pre-load event for entity: {}", entityName);
        }
    }

    /**
     * Post-load event listener.
     */
    public class PostLoadEventListener implements PostLoadEventListener {
        @Override
        public void onPostLoad(PostLoadEvent event) {
            // Record post-load metrics
            String entityName = event.getEntity().getClass().getSimpleName();
            Object id = event.getId();

            logger.debug("Post-load event for entity: {} with ID: {}", entityName, id);

            // Could record load timing metrics here if we had start time
        }
    }

    /**
     * Pre-insert event listener.
     */
    public class PreInsertEventListener implements PreInsertEventListener {
        @Override
        public boolean onPreInsert(PreInsertEvent event) {
            String entityName = event.getEntity().getClass().getSimpleName();
            logger.debug("Pre-insert event for entity: {}", entityName);
            return false; // Don't veto the operation
        }
    }

    /**
     * Post-insert event listener.
     */
    public class PostInsertEventListener implements PostInsertEventListener {
        @Override
        public void onPostInsert(PostInsertEvent event) {
            String entityName = event.getEntity().getClass().getSimpleName();
            Object id = event.getId();

            logger.debug("Post-insert event for entity: {} with ID: {}", entityName, id);

            // Record insert operation metrics
            recordEntityOperation("insert", entityName, event.getState().length);
        }
    }

    /**
     * Pre-update event listener.
     */
    public class PreUpdateEventListener implements PreUpdateEventListener {
        @Override
        public boolean onPreUpdate(PreUpdateEvent event) {
            String entityName = event.getEntity().getClass().getSimpleName();
            Object id = event.getId();

            logger.debug("Pre-update event for entity: {} with ID: {}", entityName, id);
            return false; // Don't veto the operation
        }
    }

    /**
     * Post-update event listener.
     */
    public class PostUpdateEventListener implements PostUpdateEventListener {
        @Override
        public void onPostUpdate(PostUpdateEvent event) {
            String entityName = event.getEntity().getClass().getSimpleName();
            Object id = event.getId();

            logger.debug("Post-update event for entity: {} with ID: {}", entityName, id);

            // Record update operation metrics
            int changedProperties = event.getDirtyProperties().length;
            recordEntityOperation("update", entityName, changedProperties);
        }
    }

    /**
     * Pre-delete event listener.
     */
    public class PreDeleteEventListener implements PreDeleteEventListener {
        @Override
        public boolean onPreDelete(PreDeleteEvent event) {
            String entityName = event.getEntity().getClass().getSimpleName();
            Object id = event.getId();

            logger.debug("Pre-delete event for entity: {} with ID: {}", entityName, id);
            return false; // Don't veto the operation
        }
    }

    /**
     * Post-delete event listener.
     */
    public class PostDeleteEventListener implements PostDeleteEventListener {
        @Override
        public void onPostDelete(PostDeleteEvent event) {
            String entityName = event.getEntity().getClass().getSimpleName();
            Object id = event.getId();

            logger.debug("Post-delete event for entity: {} with ID: {}", entityName, id);

            // Record delete operation metrics
            recordEntityOperation("delete", entityName, 0);
        }
    }

    /**
     * Transaction completion event listener.
     */
    public class TransactionCompletionEventListener implements org.hibernate.event.spi.SaveOrUpdateEventListener {

        @Override
        public void onSaveOrUpdate(SaveOrUpdateEvent event) throws HibernateException {
            // Track transaction metrics
            logger.debug("Save or update event for entity: {}",
                event.getEntity() != null ? event.getEntity().getClass().getSimpleName() : "null");
        }
    }

    /**
     * Records metrics for entity operations.
     */
    private void recordEntityOperation(String operation, String entityName, int affectedProperties) {
        logger.debug("Entity operation recorded - type: {}, entity: {}, properties: {}",
            operation, entityName, affectedProperties);

        // Here you could integrate with your metrics system
        // metrics.recordEntityOperation(operation, entityName, affectedProperties);
    }

    // Getter methods for the event listeners
    public PreLoadEventListener preLoadEventListener() {
        return new PreLoadEventListener();
    }

    public PostLoadEventListener postLoadEventListener() {
        return new PostLoadEventListener();
    }

    public PreInsertEventListener preInsertEventListener() {
        return new PreInsertEventListener();
    }

    public PostInsertEventListener postInsertEventListener() {
        return new PostInsertEventListener();
    }

    public PreUpdateEventListener preUpdateEventListener() {
        return new PreUpdateEventListener();
    }

    public PostUpdateEventListener postUpdateEventListener() {
        return new PostUpdateEventListener();
    }

    public PreDeleteEventListener preDeleteEventListener() {
        return new PreDeleteEventListener();
    }

    public PostDeleteEventListener postDeleteEventListener() {
        return new PostDeleteEventListener();
    }

    public TransactionCompletionEventListener transactionCompletionEventListener() {
        return new TransactionCompletionEventListener();
    }
}