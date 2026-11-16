package network.foodsharing.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import network.foodsharing.content.domain.BlogPost;
import network.foodsharing.content.repository.BlogPostRepository;
import network.foodsharing.content.repository.ContentPageRepository;
import org.junit.jupiter.api.Test;

/**
 * A41: blog pictures and content images are public; the picture of a deleted blog post counts as ownerless, so the
 * upload cleanup removes it (was BlogFlowIT).
 */
class ContentUploadRulesTest {

    BlogPostRepository posts = mock(BlogPostRepository.class);
    ContentPageRepository pages = mock(ContentPageRepository.class);
    ContentContributors.BlogPictures pictures = new ContentContributors.BlogPictures(posts);
    ContentContributors.ContentImages images = new ContentContributors.ContentImages(pages);

    @Test
    void blogPicturesArePublicAndGoWithTheirPost() {
        assertThat(pictures.module()).isEqualTo(BlogService.PICTURE_MODULE);
        assertThat(pictures.mayRead(null, UUID.randomUUID())).isTrue();
        BlogPost live = new BlogPost();
        live.setId(UUID.randomUUID());
        BlogPost deleted = new BlogPost();
        deleted.setId(UUID.randomUUID());
        deleted.setDeletedAt(Instant.parse("2026-11-02T09:00:00Z"));
        when(posts.findById(live.getId())).thenReturn(Optional.of(live));
        when(posts.findById(deleted.getId())).thenReturn(Optional.of(deleted));
        assertThat(pictures.exists(live.getId())).isTrue();
        assertThat(pictures.exists(deleted.getId())).isFalse();
        assertThat(pictures.exists(UUID.randomUUID())).isFalse();
    }

    @Test
    void contentImagesArePublicAndGoWithTheirPage() {
        UUID page = UUID.randomUUID();
        when(pages.existsById(page)).thenReturn(true);
        assertThat(images.module()).isEqualTo(ContentService.IMAGE_MODULE);
        assertThat(images.mayRead(null, page)).isTrue();
        assertThat(images.exists(page)).isTrue();
        assertThat(images.exists(UUID.randomUUID())).isFalse();
    }
}
