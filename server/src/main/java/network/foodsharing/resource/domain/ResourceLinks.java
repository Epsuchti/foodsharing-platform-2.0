package network.foodsharing.resource.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import network.foodsharing.common.persistence.BaseEntity;

/** The small join entities of the resource mosaic. */
public final class ResourceLinks {

    private ResourceLinks() {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Entity(name = "ResourceCategory")
    @Table(name = "resource_category")
    public static class Category extends BaseEntity {
        @Column(name = "icon", length = 40)
        private String icon;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Entity(name = "ResourceCategoryName")
    @Table(name = "resource_category_i18n")
    public static class CategoryName extends BaseEntity {
        @Column(name = "category_id", nullable = false)
        private UUID categoryId;

        @Column(name = "language", nullable = false, length = 5)
        private String language;

        @Column(name = "name", nullable = false, length = 100)
        private String name;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Entity(name = "ResourceCategoryLink")
    @Table(name = "resource_category_link")
    public static class CategoryLink extends BaseEntity {
        @Column(name = "resource_id", nullable = false)
        private UUID resourceId;

        @Column(name = "category_id", nullable = false)
        private UUID categoryId;

        public CategoryLink(UUID resourceId, UUID categoryId) {
            this.resourceId = resourceId;
            this.categoryId = categoryId;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Entity(name = "ResourceImage")
    @Table(name = "resource_image")
    public static class Image extends BaseEntity {
        @Column(name = "resource_id", nullable = false)
        private UUID resourceId;

        @Column(name = "upload_id", nullable = false)
        private UUID uploadId;

        @Column(name = "sort_order", nullable = false)
        private int sortOrder;

        public Image(UUID resourceId, UUID uploadId, int sortOrder) {
            this.resourceId = resourceId;
            this.uploadId = uploadId;
            this.sortOrder = sortOrder;
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @Entity(name = "ResourceFavourite")
    @Table(name = "resource_favorite")
    public static class Favourite extends BaseEntity {
        @Column(name = "user_id", nullable = false)
        private UUID userId;

        @Column(name = "resource_id", nullable = false)
        private UUID resourceId;

        public Favourite(UUID userId, UUID resourceId) {
            this.userId = userId;
            this.resourceId = resourceId;
        }
    }
}
