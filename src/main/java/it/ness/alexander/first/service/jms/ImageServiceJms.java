package it.ness.alexander.first.service.jms;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import it.ness.alexander.first.model.pojo.ImageEvent;
import org.jboss.logging.Logger;

import javax.transaction.Transactional;

@ApplicationScoped
public class ImageServiceJms implements Runnable {

    @Inject
    ConnectionFactory connectionFactory;

    @Inject
    Event imageEvent;

    protected Logger logger = Logger.getLogger(getClass());

    private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

    void onStart(@Observes StartupEvent ev) {
        scheduler.submit(this);
    }

    void onStop(@Observes ShutdownEvent ev) {
        scheduler.shutdown();
    }

    @Override
    @Transactional
    public void run() {
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)) {
            JMSConsumer consumer = context.createConsumer(context.createQueue("imageResizeQueue"));
            while (true) {
                final Message message = consumer.receive();
                if (message == null) {
                    return;
                }
                final String jsonMessage = message.getBody(String.class);
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Object> map
                        = objectMapper.readValue(jsonMessage, new TypeReference<Map<String,Object>>(){});

                final String uuid = (String)map.get("uuid");
                final String format = (String)map.get("format");
                logger.infov("JSM message received to resize image [{0}] to format [{1}]", uuid, format);
                imageEvent.fireAsync(new ImageEvent(uuid, format));
            }
        } catch (JMSException e) {
            throw new RuntimeException(e);
        } catch (JsonMappingException e) {
            logger.error("Failed to map json resource: " + e);
        } catch (JsonProcessingException e) {
            logger.error("Failed to process json resource: " + e);
        }
    }
}
