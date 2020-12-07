package it.ness.alexander.first.service.timers;

import io.quarkus.scheduler.Scheduled;
import it.ness.alexander.first.model.pojo.ImageEvent;
import it.ness.alexander.first.service.jms.ImageServiceJMSProducer;
import org.hibernate.Session;
import org.jboss.logging.Logger;

import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.List;

import it.ness.alexander.first.model.ImageFormat;


@Singleton
public class ResizeImageTimerService {

    @Inject
    EntityManager entityManager;

    @Inject
    Event imageEvent;

    @Inject
    ImageServiceJMSProducer producer;

    protected Logger logger = Logger.getLogger(getClass());

    @Scheduled(every="30m")
    @Transactional
    void processResizingActions() {
        Calendar rightNow = Calendar.getInstance();
        int hour = rightNow.get(Calendar.HOUR_OF_DAY);
        enableFilter("obj.hour_of_day_to_start", "hour_of_day_to_start", hour);

        List<ImageFormat> imageFormats = ImageFormat.list("select a from ImageFormat a");

        if (imageFormats != null) {
            for (ImageFormat imageFormat : imageFormats)
            {
                if (imageFormat.executor != null) {
                    if ("jms".equals(imageFormat.executor.toLowerCase()))
                        elaborateImageFormatJMS(imageFormat);
                    else
                    if ("cdi".equals(imageFormat.executor.toLowerCase()))
                        elaborateImageFormatCDI(imageFormat);
                }
            }
            logger.info("ImageFormat processed at : " + LocalDateTime.now());
        }
    }

    private void elaborateImageFormatCDI(ImageFormat imageFormat) {
        logger.info("Elaborating with CDI ImageFormat: " + imageFormat.toString());
        final String mime_type = imageFormat.mime_type;
        for (String format : imageFormat.formats) {
            List<String> attachments_uuids = getAttachmentUuidToResize(mime_type, format);
            for (String uuid : attachments_uuids) {
                logger.infov("Start CDI job to resize image [{0}] to new format [{1}]", uuid, format);
                imageEvent.fireAsync(new ImageEvent(uuid, format));
            }
        }
    }

    private void elaborateImageFormatJMS(ImageFormat imageFormat) {
        logger.info("Elaborating with JMS ImageFormat: " + imageFormat.toString());
        final String mime_type = imageFormat.mime_type;
        for (String format : imageFormat.formats) {
            List<String> attachments_uuids = getAttachmentUuidToResize(mime_type, format);
            for (String uuid : attachments_uuids) {
                logger.infov("Start JMS job to resize image [{0}] to new format [{1}]", uuid, format);
                producer.sendMessage(uuid, format);
            }
        }
    }

    private List<String> getAttachmentUuidToResize(final String mime_type, final String format) {
        Query q = entityManager.createNativeQuery("SELECT attachments.uuid FROM attachments WHERE uuid NOT IN (" +
                "SELECT DISTINCT attachments.uuid FROM attachments LEFT JOIN attachments_formats " +
                "ON attachments.uuid = attachments_formats.attachments_uuid " +
                "WHERE attachments_formats.formats = ?1 AND attachments.mime_type = ?2) ORDER BY RANDOM()");
        q.setParameter(1, format);
        q.setParameter(2, mime_type);
        return q.getResultList();
    }

    public void enableFilter(String filterName, String parameterName, Object value) {
        entityManager.unwrap(Session.class)
                .enableFilter(filterName)
                .setParameter(parameterName, value);
    }
}
