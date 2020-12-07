package it.ness.alexander.first.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.ElementCollection;

import java.util.List;
import java.util.ArrayList;

@Entity
@Table(name = "imageformat")

@FilterDef(name = "obj.uuid", parameters = @ParamDef(name = "uuid", type = "string"))
@Filter(name = "obj.uuid", condition = "uuid = :uuid")

@FilterDef(name = "obj.hour_of_day_to_start", parameters = @ParamDef(name = "hour_of_day_to_start", type = "int"))
@Filter(name = "obj.hour_of_day_to_start", condition = "hour_of_day_to_start = :hour_of_day_to_start")

public class ImageFormat extends PanacheEntityBase {

    @Id
    public String uuid;

    public String mime_type;

    public Integer hour_of_day_to_start;

    public String executor;

    @ElementCollection
    public List<String> formats = new ArrayList<String>();

    public ImageFormat() {
    }

    @Override
    public String toString() {
        return "ImageFormat{" +
                "uuid='" + uuid + '\'' +
                ", mime_type='" + mime_type + '\'' +
                ", hour_of_day_to_start='" + hour_of_day_to_start + '\'' +
                ", executor='" + executor + '\'' +
                ", formats=[" + String.join(", ", formats) + ']' +
                '}';
    }
}