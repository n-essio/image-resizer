package it.ness.alexander.first.service.rs;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.runtime.JpaOperations;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import it.ness.alexander.first.model.pojo.FormData;
import it.ness.alexander.first.service.S3Client;
import it.ness.api.service.RsRepositoryServiceV3;
import it.ness.alexander.first.model.Attachment;
import it.ness.alexander.first.model.pojo.ImageEvent;
import javax.enterprise.event.Event;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import javax.ejb.Singleton;
import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import java.util.UUID;
import java.util.Date;

import static it.ness.alexander.first.management.AppConstants.ATTACHMENTS_PATH;
import static it.ness.alexander.first.management.AppConstants.SUPPORTED_IMAGE_FORMATS;

@Path(ATTACHMENTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class AttachementServiceRs extends RsRepositoryServiceV3<Attachment, String> {

    @Inject
    S3Client s3Client;

    @Inject
    Event imageEvent;

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
            performDocumentUploading(attachment, formData, logMessage);
            attachment.persist();
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
    @Transactional
    public Response resize(@PathParam(value = "uuid") String uuid, @QueryParam(value = "format") String format) {
        if (!validateFormat(format)) {
            String errMsg = String.format("Resizing image format [%s] not supported.", format);
            logger.error(errMsg);
            return jsonErrorMessageResponse(errMsg);
        }

        Attachment attachment = Attachment.findById(uuid);
        if (attachment == null) {
            return handleObjectNotFoundRequest(uuid);
        }
        boolean itemExists = attachment.formats.stream().anyMatch(c -> c.equals(format));
        if (itemExists) {
            String errMsg = String.format("Image [%s] with format [%s] already exists.", uuid, format);
            logger.error(errMsg);
            return jsonErrorMessageResponse(errMsg);
        }

        final String logMessage = "@GET resize: [{0}] format: [{1}]";
        logger.infov(logMessage, attachment, format);
        logger.info(MediaType.valueOf(attachment.mime_type));

        imageEvent.fireAsync(new ImageEvent(uuid, format));

        return Response.ok().build();
    }

    @GET
    @Path("/{uuid}/download")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response download(@PathParam(value = "uuid") String uuid, @QueryParam(value = "format") String format) throws Exception {
        String duuid = uuid;
        if (format != null)
        {
            if (!validateFormat(format)) {
                String errMsg = String.format("Image format [%s] not supported.", format);
                logger.error(errMsg);
                return jsonErrorMessageResponse(errMsg);
            }
            duuid = duuid + "_" + format;
        }
        final String logMessage = "@GET download: [{0}]";
        Attachment attachment = Attachment.findById(uuid);
        if (attachment == null) {
            return handleObjectNotFoundRequest(uuid);
        }
        logger.infov(logMessage, attachment);
        logger.info(MediaType.valueOf(attachment.mime_type));

        boolean itemExists = attachment.formats.stream().anyMatch(c -> c.equals(format));
        if (itemExists) {
            return Response.ok(s3Client.downloadObject(duuid), attachment.mime_type)
                    .header("Content-Disposition", "attachment; filename=\"" + attachment.name + "\"")
                    .build();
        }
        else {
            return Response.ok(s3Client.downloadObject(attachment.uuid), attachment.mime_type)
                    .header("Content-Disposition", "attachment; filename=\"" + attachment.name + "\"")
                    .build();
        }
    }

    private void performDocumentUploading(Attachment attachment, FormData formData, String logMessage) throws Exception {
        attachment.uuid = UUID.randomUUID().toString();
        attachment.name = formData.fileName;
        attachment.mime_type = formData.mimeType;
        attachment.external_type = formData.external_type;
        attachment.external_uuid = formData.external_uuid;
        attachment.creation_date = new Date();
        logger.infov(logMessage, attachment);
        String result = s3Client.uploadObject(attachment.uuid, formData.data, formData.mimeType);
        attachment.s3name = attachment.uuid;
    }

    private boolean validateFormat(String format) {
        for (String f : SUPPORTED_IMAGE_FORMATS)
        {
            if (f.equals(format))
                return true;
        }
        return false;
    }
}
