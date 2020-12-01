package it.ness.alexander.first.model.pojo;

import org.jboss.resteasy.annotations.providers.multipart.PartType;

import javax.ws.rs.FormParam;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;

public class FormData {
    @FormParam("file")
    @PartType(MediaType.APPLICATION_OCTET_STREAM)
    public InputStream data;

    @FormParam("filename")
    @PartType(MediaType.TEXT_PLAIN)
    public String fileName;

    @FormParam("mime_type")
    @PartType(MediaType.TEXT_PLAIN)
    public String mimeType;

    @FormParam("external_uuid")
    @PartType(MediaType.TEXT_PLAIN)
    public String external_uuid;

    @FormParam("external_type")
    @PartType(MediaType.TEXT_PLAIN)
    public String external_type;
}
