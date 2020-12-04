package it.ness.alexander.first.service.timers;
/*
import dev.it.services.model.PerformedAction;
import dev.it.services.model.PerformedActionBlogPost;
import dev.it.services.model.pojo.ActionValue;
 */
import io.quarkus.scheduler.Scheduled;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;


@Singleton
public class ResizeImageTimerService {

    @Inject
    EntityManager entityManager;

    protected Logger logger = Logger.getLogger(getClass());

    @Scheduled(every="10m")
    @Transactional
    void proccessResizingActions() {

    }

}
