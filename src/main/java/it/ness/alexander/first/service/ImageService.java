package it.ness.alexander.first.service;

import org.jboss.logging.Logger;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.ObservesAsync;
import javax.transaction.Transactional;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.imageio.ImageIO;
import javax.ws.rs.core.StreamingOutput;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import net.coobird.thumbnailator.Thumbnails;
import it.ness.alexander.first.model.Attachment;
import it.ness.alexander.first.model.pojo.ImageEvent;

@ApplicationScoped
public class ImageService {

    protected Logger logger = Logger.getLogger(getClass());

    @Inject
    S3Client s3Client;

    @Inject
    EntityManager entityManager;

    public void onEvent(@ObservesAsync ImageEvent event) {
        final String uuid = event.getUuid();
        final String format = event.getFormat();
        resize(uuid, format);
    }

    @Transactional
    public void resize(final String uuid, final String format) {
        //resize and upload to s3
        try {
            Attachment attachment = Attachment.findById(uuid);
            if (attachment != null) {
                boolean itemExists = attachment.formats.stream().anyMatch(c -> c.equals(format));
                if (!itemExists) {
                    String mime_type = attachment.mime_type;
                    String ruuid = uuid + "_" + format;
                    BufferedImage originalImg = downloadImageFromS3(uuid);
                    ByteArrayOutputStream resizedBaos = resize(originalImg, format, mime_type);
                    uploadImageToS3(ruuid, resizedBaos, mime_type);
                    attachment.formats.add(format);
                    entityManager.merge(attachment);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to create resource in ImageService: " + e);
        }
    }

    private ByteArrayOutputStream resize(BufferedImage originalImage, String format, String mime_type) throws Exception {
        String[] tokens = format.split("x");
        int targetWidth = Integer.valueOf(tokens[0]);
        int targetHeight = Integer.valueOf(tokens[1]);
        String targetImgFormat = getFormatForMimeType(mime_type);
        logger.infov("new width = " + targetWidth + ", new height = " + targetHeight + ", target image format = " + targetImgFormat);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Thumbnails.of(originalImage)
                .size(targetWidth, targetHeight)
                .outputFormat(targetImgFormat)
                .outputQuality(1)
                .toOutputStream(outputStream);
        return outputStream;
    }

    private BufferedImage downloadImageFromS3(String uuid) throws Exception {
        StreamingOutput output = s3Client.downloadObject(uuid);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        output.write(baos);
        InputStream is = new ByteArrayInputStream(baos.toByteArray());
        BufferedImage originalImage = ImageIO.read(is);
        int imageWidth = originalImage.getWidth();
        int imageHeight = originalImage.getHeight();
        logger.infov("image width = " + imageWidth + " image height = " + imageHeight);
        return originalImage;
    }

    private void uploadImageToS3(String uuid, ByteArrayOutputStream resizedBaos, String mime_type) throws Exception {
        final ByteArrayInputStream resizedBais = new ByteArrayInputStream(resizedBaos.toByteArray());
        s3Client.uploadObject(uuid, resizedBais, mime_type);
    }

    private String getFormatForMimeType(String mime_type) throws Exception {
        String[] formats = ImageIO.getWriterFormatNames();
        String lmime_type = mime_type.toLowerCase();
        for (String f : formats) {
            String lf = f.toLowerCase();
            if (lmime_type.indexOf(lf) > 0 ||
                    lf.indexOf(lmime_type) > 0 ||
                    lf.equals(lmime_type))
                return f;
        }
        throw new Exception(String.format("Failed to find Image type for mime type [%s]", mime_type));
    }
}
