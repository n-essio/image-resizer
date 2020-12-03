package it.ness.alexander.first.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.ElementCollection;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "attachments")

@FilterDef(name = "obj.uuid", parameters = @ParamDef(name = "uuid", type = "string"))
@Filter(name = "obj.uuid", condition = "uuid = :uuid")

@FilterDef(name = "like.name", parameters = @ParamDef(name = "name", type = "string"))
@Filter(name = "like.name", condition = "name LIKE :name")

@FilterDef(name = "from.creation_date", parameters = @ParamDef(name = "creation_date", type = "string"))
@Filter(name = "from.creation_date", condition = "creation_date >= :creation_date")

@FilterDef(name = "to.creation_date", parameters = @ParamDef(name = "creation_date", type = "string"))
@Filter(name = "to.creation_date", condition = "creation_date <= :creation_date")

public class Attachment extends PanacheEntityBase {

    @Id
    public String uuid;

    public String name;
    public Long size;
    public String s3name;

    public Date creation_date;
    public String mime_type;
    public String s3_url;

    // the uuid of related entity (for example a BlogPost uuid)
    public String external_uuid;
    // the name of entity (es: blogpost, developer, project, user)
    public String external_type;

    @ElementCollection
    public List<String> formats = new ArrayList<String>();

    public Attachment() {
    }

    @Override
    public String toString() {
        return "Attachment{" +
                "uuid='" + uuid + '\'' +
                ", name='" + name + '\'' +
                ", size='" + size + '\'' +
                ", s3name='" + s3name + '\'' +
                ", creation_date=" + creation_date +
                ", mime_type='" + mime_type + '\'' +
                ", external_type='" + external_type + '\'' +
                ", external_uuid='" + external_uuid + '\'' +
                ", s3_url='" + s3_url + '\'' +
                ", formats=[" + String.join(", ", formats) + ']' +
                '}';
    }
}