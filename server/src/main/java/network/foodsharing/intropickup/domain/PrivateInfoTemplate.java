package network.foodsharing.intropickup.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** Saved private info of a guide for a category. */
@Getter
@Setter
@Entity
@Table(name = "private_info_template")
public class PrivateInfoTemplate extends BaseEntity {

    @Column(name = "guide_id", nullable = false, updatable = false)
    private UUID guideId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private IntroPickupCategory category;

    @Column(name = "content", nullable = false, length = 4000)
    private String content;
}
