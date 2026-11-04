package archfixture.domain;

import jakarta.persistence.Entity;
import network.foodsharing.common.persistence.BaseEntity;

/** Rule 7b fixture: an entity mapping bytes (outside the scanned packages, never a real table). */
@Entity
public class BytesEntity extends BaseEntity {

    private String name;

    private byte[] content;

    private Byte[] boxed;
}
