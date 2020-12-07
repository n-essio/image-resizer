package it.ness.alexander.first.service.jms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import javax.jms.JMSRuntimeException;
import javax.jms.Session;

@ApplicationScoped
public class ImageServiceJMSProducer {

    @Inject
    ConnectionFactory connectionFactory;

    protected Logger logger = Logger.getLogger(getClass());

    public void sendMessage(String uuid, String format) {
        try (JMSContext context = connectionFactory.createContext(Session.AUTO_ACKNOWLEDGE)){
            ObjectMapper mapper = new ObjectMapper();

            // create a JSON object
            ObjectNode message = mapper.createObjectNode();
            message.put("uuid", uuid);
            message.put("format", format);

            // convert `ObjectNode` to pretty-print JSON
            String jsonMessage = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(message);

            context.createProducer().send(context.createQueue("imageResizeQueue"), jsonMessage);
        } catch (JMSRuntimeException ex) {
            logger.error("Failed to send jms message to ImageService: " + ex);
        } catch (JsonProcessingException e) {
            logger.error("Failed to process json resource: " + e);
        }
    }
}
