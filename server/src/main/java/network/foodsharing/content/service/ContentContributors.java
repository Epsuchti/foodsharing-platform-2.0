package network.foodsharing.content.service;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import network.foodsharing.common.persistence.UserRows;
import network.foodsharing.content.repository.BlogPostRepository;
import network.foodsharing.content.repository.ContentPageRepository;
import network.foodsharing.upload.service.UploadAccessRule;
import network.foodsharing.user.service.DataExportContributor;
import org.springframework.stereotype.Component;

/**
 * SPI implementations of the content module: public upload rules and the blog part of the GDPR export. Upload rules
 * use repositories only (UploadService needs every rule, and the content services depend on UploadService).
 */
final class ContentContributors {

    private ContentContributors() {
    }

    /** Blog pictures are public (A41); the picture of a deleted post goes with the upload cleanup. */
    @Component
    static class BlogPictures implements UploadAccessRule {

        private final BlogPostRepository posts;

        BlogPictures(BlogPostRepository posts) {
            this.posts = posts;
        }

        @Override
        public String module() {
            return BlogService.PICTURE_MODULE;
        }

        @Override
        public boolean mayRead(UUID userId, UUID usageOwnerId) {
            return true;
        }

        @Override
        public boolean exists(UUID usageOwnerId) {
            return posts.findById(usageOwnerId).filter(post -> post.getDeletedAt() == null).isPresent();
        }
    }

    /** Images of content pages are public (A41). */
    @Component
    static class ContentImages implements UploadAccessRule {

        private final ContentPageRepository pages;

        ContentImages(ContentPageRepository pages) {
            this.pages = pages;
        }

        @Override
        public String module() {
            return ContentService.IMAGE_MODULE;
        }

        @Override
        public boolean mayRead(UUID userId, UUID usageOwnerId) {
            return true;
        }

        @Override
        public boolean exists(UUID usageOwnerId) {
            return pages.existsById(usageOwnerId);
        }
    }

    /**
     * Blog posts a user wrote (A40a: authored content stays and points at the anonymised user row, so there is no
     * deletion contributor).
     */
    @Component
    static class BlogExport implements DataExportContributor {

        private final EntityManager em;

        BlogExport(EntityManager em) {
            this.em = em;
        }

        @Override
        public String section() {
            return "blogPosts";
        }

        @Override
        public Object export(UUID userId) {
            return UserRows.read(em, "BlogPost", "authorId", userId, "id", "title", "teaser", "published",
                    "publishedAt", "createdAt", "deletedAt");
        }
    }

    /** Consents to the legal documents (D17.3): kept on account deletion as proof, part of the export. */
    @Component
    static class LegalExport implements DataExportContributor {

        private final EntityManager em;

        LegalExport(EntityManager em) {
            this.em = em;
        }

        @Override
        public String section() {
            return "legalConsents";
        }

        @Override
        public Object export(UUID userId) {
            return UserRows.read(em, "LegalAcceptance", "userId", userId, "kind", "documentVersion", "acceptedAt");
        }
    }
}
