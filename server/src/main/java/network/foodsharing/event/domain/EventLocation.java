package network.foodsharing.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Where an OFFLINE event takes place (upstream fs_location). */
@Getter
@Setter
@Entity
@Table(name = "event_location")
public class EventLocation extends BaseEntity {

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "street", length = 200)
    private String street;

    @Column(name = "zip", length = 20)
    private String zip;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    /** One line for ICS LOCATION and bells. */
    public String singleLine() {
        StringBuilder out = new StringBuilder(name);
        if (street != null && !street.isBlank()) {
            out.append(", ").append(street);
        }
        String place = ((zip == null ? "" : zip) + " " + (city == null ? "" : city)).strip();
        if (!place.isEmpty()) {
            out.append(", ").append(place);
        }
        return out.toString();
    }
}
