package it.ness.alexander.first.service.rs;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
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

import static it.ness.alexander.first.management.AppConstants.ATTACHMENTS_PATH;

@Path(ATTACHMENTS_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Singleton
public class AttachementServiceRs extends RsRepositoryServiceV3<Attachment, String> {

    @Inject
    S3Client s3Client;


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
        final String logMessage = "@UPLOAD CSV FILE: [{0}]";
        logger.info("UPLOAD CSV FILE [" + formData.fileName + "]");

        Attachment attachment = new Attachment();
        //
        attachment.external_type = formData.external_type;
        attachment.external_uuid = formData.external_uuid;
        try {
            attachment.persist();
            s3Client.uploadObject(attachment.uuid, formData.data, formData.mimeType);
        } catch (Exception e) {
            logger.errorv(e, logMessage);
            return jsonErrorMessageResponse(attachment);
        }
        return Response.status(Response.Status.OK).entity(attachment).build();
    }

    @GET
    @Path("/{uuid}/resize")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response resize(@PathParam(value = "uuid") String uuid, @QueryParam(value = "format") String format) {
        Attachment attachment = Attachment.findById(uuid);
// RESIZE:
        https:
//eikhart.com/blog/aspect-ratio-calculator#:~:text=There%20is%20a%20simple%20formula,%3D%20(%20newHeight%20*%20aspectRatio%20)%20.

        //if the format is not present:
//        you will use generate image dinamically
//        https://www.baeldung.com/java-resize-image
//        the image must preserve the ratio:
//        if width > height && width > image_width (you will generate the new image scaling proportionally on width)
//        if height > width  && height > image_height (you will generate the new image scaling proportionally on height)
//
//        - you will update to s3
        try {
            int targetWidth = 0;
            int targetHeight = 0;
            InputStream inputStream = new ByteArrayInputStream();
            StreamingOutput output = s3Client.downloadObject(attachment.uuid);
            Thumbnails.of(inputStream)
                    .size(targetWidth, targetHeight)
                    .outputFormat("JPEG")
                    .outputQuality(1)
                    .toOutputStream(outputStream);
            byte[] data = outputStream.toByteArray();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);

            s3Client.uploadObject(attachment.uuid + "_" + format, output, attachment.mime_type);
//        - you will add to formats
//        - you will update attachemnt
            return Response.ok().build();
        } catch (Exception e) {
            return Response.serverError().build();
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


}
