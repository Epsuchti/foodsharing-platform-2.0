package network.foodsharing.history.internal;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.event.service.spi.EventListenerRegistry;
import org.hibernate.event.spi.EventType;
import org.hibernate.integrator.spi.Integrator;

/** Registers the {@link TrackedFieldListener} with Hibernate's event system. */
public class HistoryIntegrator implements Integrator {

    private final TrackedFieldListener listener;

    public HistoryIntegrator(TrackedFieldListener listener) {
        this.listener = listener;
    }

    @Override
    public void integrate(Metadata metadata, BootstrapContext bootstrapContext, SessionFactoryImplementor sessionFactory) {
        sessionFactory.getServiceRegistry()
                .requireService(EventListenerRegistry.class)
                .appendListeners(EventType.POST_UPDATE, listener);
    }
}
