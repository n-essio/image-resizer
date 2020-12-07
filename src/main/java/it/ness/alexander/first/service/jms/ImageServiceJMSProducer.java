package it.ness.alexander.first.service.jms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.*;

import static it.ness.alexander.first.management.AppConstants.IMAGE_RESIZE_QUEUE;

@ApplicationScoped
public class ImageServiceJMSProducer {

    @Inject
    ConnectionFactory connectionFactory;

    protected Logger logger = Logger.getLogger(getClass());

    private Queue queue = null;

    private JMSProducer producer = null;

    public void sendMessage(String uuid, String format) {
        try {
            if (queue == null || producer == null)
                createProducerQueue();

            ObjectMapper mapper = new ObjectMapper();
            // create a JSON object
            ObjectNode message = mapper.createObjectNode();
            message.put("uuid", uuid);
            message.put("format", format);

            // convert `ObjectNode` to JSON
            String jsonMessage = mapper.writeValueAsString(message);
            producer.send(queue, jsonMessage);

        } catch (JMSException ex) {
            logger.error("Failed to send jms message to ImageService: " + ex);
        } catch (JsonProcessingException e) {
            logger.error("Failed to process json resource: " + e);
        }
    }

    protected void createProducerQueue() throws JMSException {
        JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE);
        queue = context.createQueue(IMAGE_RESIZE_QUEUE);
        producer = context.createProducer();
    }
}
