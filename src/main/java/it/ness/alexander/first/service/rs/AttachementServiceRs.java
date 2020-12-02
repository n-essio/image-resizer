package it.ness.alexander.first.service.rs;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.runtime.JpaOperations;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import it.ness.alexander.first.model.pojo.FormData;
import it.ness.alexander.first.service.S3Client;
import it.ness.api.service.RsRepositoryServiceV3;
import it.ness.alexander.first.model.Attachment;
import net.coobird.thumbnailator.Thumbnails;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.Date;

import javax.imageio.ImageIO;

import static it.ness.alexander.first.management.AppConstants.ATTACHMENTS_PATH;

@Path(ATTACHMENTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class AttachementServiceRs extends RsRepositoryServiceV3<Attachment, String> {

    @Inject
    S3Client s3Client;

    public AttachementServiceRs() {
        super(Attachment.class);
    }

    @Override
    protected String getDefaultOrderBy() {
        return "name asc";
    }

    @Override
    public PanacheQuery<Attachment> getSearch(String orderBy) throws Exception {
        PanacheQuery<Attachment> search;
        Sort sort = sort(orderBy);

        if (sort != null) {
            search = Attachment.find("select a from Attachment a", sort);
        } else {
            search = Attachment.find("select a from Attachment a");
        }
        if (nn("obj.name")) {
            search
                    .filter("obj.name", Parameters.with("name", get("obj.name")));
        }
        if (nn("obj.external_type")) {
            search
                    .filter("obj.external_type", Parameters.with("external_type", get("obj.external_type")));
        }
        if (nn("like.name")) {
            search
                    .filter("like.name", Parameters.with("name", likeParamToLowerCase("like.name")));
        }
        return search;
    }

    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Path("/upload")
    @Transactional
    public Response upload(@MultipartForm FormData formData) {

        final String logMessage = "@POST upload image: [{0}]";

        Attachment attachment = new Attachment();
        try {
            attachment.uuid = UUID.randomUUID().toString();
            attachment.external_type = formData.external_type;
            attachment.external_uuid = formData.external_uuid;

            performDocumentUploading(attachment, formData, logMessage);
            JpaOperations.persist(attachment);
            if (attachment == null || attachment.uuid == null) {
                logger.error("Failed to create resource: " + attachment);
                return jsonErrorMessageResponse(attachment);
            } else {
                return Response.status(Response.Status.OK).entity(attachment).build();
            }
        } catch (Exception e) {
            logger.errorv(e, logMessage);
            return jsonErrorMessageResponse(attachment);
        }
    }

    @GET
    @Path("/{uuid}/resize")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Transactional
    public Response resize(@PathParam(value = "uuid") String uuid, @QueryParam(value = "format") String format) {
        final String logMessage = "@GET resize: [{0}] format: [{1}]";
        Attachment attachment = Attachment.findById(uuid);
        if (attachment == null) {
            return handleObjectNotFoundRequest(uuid);
        }
        String suuid = attachment.uuid + "_" + format;
        if (Attachment.findById(suuid) != null)
        {
            String errMsg = String.format("Resource with uuid [%s] already exists.", suuid);
            logger.error(errMsg);
            return jsonErrorMessageResponse(errMsg);
        }

        logger.infov(logMessage, attachment, format);
        logger.info(MediaType.valueOf(attachment.mime_type));

        try {
            StreamingOutput output = s3Client.downloadObject(attachment.uuid);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            output.write(baos);
            InputStream is = new ByteArrayInputStream(baos.toByteArray());
            BufferedImage originalImage = ImageIO.read(is);
            int imageWidth = originalImage.getWidth();
            int imageHeight = originalImage.getHeight();
            logger.infov("image width = " + imageWidth + " image height = " + imageHeight);
            String[] tokens = format.split("x");
            int targetWidth = Integer.valueOf(tokens[0]);
            int targetHeight = Integer.valueOf(tokens[1]);
            logger.infov("new width = " + targetWidth + " new height = " + targetHeight);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(originalImage)
                    .size(targetWidth, targetHeight)
                    .outputFormat("JPEG")
                    .outputQuality(1)
                    .toOutputStream(outputStream);
            final ByteArrayInputStream scaledInputStream = new ByteArrayInputStream(outputStream.toByteArray());

            Attachment scaledAttachment = new Attachment();
            scaledAttachment.uuid = suuid;
            scaledAttachment.name = attachment.name;
            scaledAttachment.mime_type = "image/jpg";
            scaledAttachment.external_type = attachment.external_type;
            scaledAttachment.external_uuid = attachment.external_uuid;
            scaledAttachment.creation_date = new Date();
            logger.infov("scaled attachment: [{0}]", scaledAttachment);

            String result = s3Client.uploadObject(scaledAttachment.uuid, scaledInputStream, scaledAttachment.mime_type);
            scaledAttachment.s3name = scaledAttachment.uuid;
            JpaOperations.persist(scaledAttachment);

            if (scaledAttachment == null || scaledAttachment.uuid == null) {
                logger.error("Failed to create resource: " + scaledAttachment);
                return jsonErrorMessageResponse(scaledAttachment);
            } else {
                return Response.ok(s3Client.downloadObject(scaledAttachment.uuid), scaledAttachment.mime_type)
                        .header("Content-Disposition", "attachment; filename=\"" + scaledAttachment.name + "\"")
                        .build();
            }
        } catch (Exception e) {
            logger.errorv(e, logMessage);
            return jsonErrorMessageResponse(attachment);
        }
    }

    @GET
    @Path("/{uuid}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam(value = "uuid") String uuid) throws Exception {
        final String logMessage = "@GET download: [{0}]";
        Attachment attachment = Attachment.findById(uuid);
        if (attachment == null) {
            return handleObjectNotFoundRequest(uuid);
        }
        logger.infov(logMessage, attachment);
        logger.info(MediaType.valueOf(attachment.mime_type));
        return Response.ok(s3Client.downloadObject(attachment.uuid), attachment.mime_type)
                .header("Content-Disposition", "attachment; filename=\"" + attachment.name + "\"")
                .build();
    }

    private void performDocumentUploading(Attachment attachment, FormData formData, String logMessage) throws Exception {
        attachment.name = formData.fileName;
        attachment.mime_type = formData.mimeType;
        attachment.external_type = formData.external_type;
        attachment.external_uuid = formData.external_uuid;
        attachment.creation_date = new Date();
        logger.infov(logMessage, attachment);
        String result = s3Client.uploadObject(attachment.uuid, formData.data, formData.mimeType);
        attachment.s3name = attachment.uuid;
    }
}
