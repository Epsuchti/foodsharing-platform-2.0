package network.foodsharing.notification.newsletter.web;

import static network.foodsharing.common.mapper.TimeMapper.toOffset;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.NewslettersApi;
import network.foodsharing.api.model.Newsletter;
import network.foodsharing.api.model.NewsletterCreate;
import network.foodsharing.api.model.NewsletterPage;
import network.foodsharing.api.model.NewsletterPatch;
import network.foodsharing.api.model.NewsletterProgress;
import network.foodsharing.api.model.NewsletterStatus;
import network.foodsharing.api.model.RecipientCount;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.notification.newsletter.service.NewsletterService;
import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.UnitType;
import network.foodsharing.notification.service.NewsletterRegions;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Newsletters (M4 B5, N7). */
@RestController
public class NewslettersController implements NewslettersApi {

    private final NewsletterService newsletters;
    private final NewsletterRegions regions;
    private final UserService users;
    private final UserMapper userMapper;

    public NewslettersController(NewsletterService newsletters, NewsletterRegions regions, UserService users,
                                 UserMapper userMapper) {
        this.newsletters = newsletters;
        this.regions = regions;
        this.users = users;
        this.userMapper = userMapper;
    }

    @Override
    public ResponseEntity<Newsletter> cancelNewsletter(UUID newsletterId) {
        return ResponseEntity.ok(toModel(newsletters.cancel(newsletterId)));
    }

    @Override
    public ResponseEntity<RecipientCount> countNewsletterRecipients(UUID newsletterId) {
        RecipientCount count = new RecipientCount();
        count.setCount(newsletters.recipientCount(newsletterId));
        return ResponseEntity.ok(count);
    }

    @Override
    public ResponseEntity<Newsletter> createNewsletter(NewsletterCreate body) {
        return ResponseEntity.status(201).body(toModel(newsletters.create(body.getRegionId(), body.getSubject(),
                body.getBodyMarkdown())));
    }

    @Override
    public ResponseEntity<Void> deleteNewsletter(UUID newsletterId) {
        newsletters.delete(newsletterId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Newsletter> getNewsletter(UUID newsletterId) {
        return ResponseEntity.ok(toModel(newsletters.get(newsletterId)));
    }

    @Override
    public ResponseEntity<NewsletterProgress> getNewsletterProgress(UUID newsletterId) {
        var newsletter = newsletters.get(newsletterId);
        NewsletterProgress progress = new NewsletterProgress();
        progress.setTotal(newsletter.getRecipientCount());
        progress.setSent(newsletter.getSentCount());
        progress.setFailed(newsletter.getFailedCount());
        progress.setStatus(NewsletterStatus.valueOf(newsletter.getStatus().name()));
        return ResponseEntity.ok(progress);
    }

    @Override
    public ResponseEntity<NewsletterPage> listNewsletters(UUID regionId, NewsletterStatus status, Integer page,
                                                          Integer size) {
        Page<network.foodsharing.notification.newsletter.domain.Newsletter> result = newsletters.list(regionId,
                status == null ? null : network.foodsharing.notification.domain.NewsletterStatus.valueOf(status.name()),
                page, size);
        NewsletterPage body = new NewsletterPage();
        body.setItems(toModels(result.getContent()));
        body.setPage(PageMapper.toPageMeta(result));
        return ResponseEntity.ok(body);
    }

    @Override
    public ResponseEntity<Newsletter> queueNewsletter(UUID newsletterId) {
        return ResponseEntity.ok(toModel(newsletters.queue(newsletterId)));
    }

    @Override
    public ResponseEntity<Void> sendNewsletterTest(UUID newsletterId) {
        newsletters.sendTest(newsletterId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Newsletter> updateNewsletter(UUID newsletterId, NewsletterPatch patch) {
        return ResponseEntity.ok(toModel(newsletters.update(newsletterId, patch.getSubject(),
                patch.getBodyMarkdown())));
    }

    private Newsletter toModel(network.foodsharing.notification.newsletter.domain.Newsletter newsletter) {
        return toModels(List.of(newsletter)).getFirst();
    }

    private List<Newsletter> toModels(List<network.foodsharing.notification.newsletter.domain.Newsletter> list) {
        Map<UUID, User> authors = users.findAll(list.stream().map(n -> n.getCreatedBy())
                .filter(java.util.Objects::nonNull).distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return list.stream().map(n -> {
            Newsletter model = new Newsletter();
            model.setId(n.getId());
            if (n.getRegionId() != null) {
                regions.find(n.getRegionId()).ifPresent(r -> {
                    RegionRef ref = new RegionRef();
                    ref.setId(r.id());
                    ref.setSlug(r.slug());
                    ref.setName(r.name());
                    ref.setType(UnitType.fromValue(r.type()));
                    ref.setParentId(r.parentId());
                    model.setRegion(ref);
                });
            }
            model.setSubject(n.getSubject());
            model.setBodyMarkdown(n.getBodyMarkdown());
            model.setStatus(NewsletterStatus.valueOf(n.getStatus().name()));
            model.setRecipientCount(n.getRecipientCount());
            model.setSentCount(n.getSentCount());
            model.setFailedCount(n.getFailedCount());
            User author = n.getCreatedBy() == null ? null : authors.get(n.getCreatedBy());
            model.setCreatedBy(author == null ? null : userMapper.toSummary(author));
            model.setCreatedAt(toOffset(n.getCreatedAt()));
            model.setQueuedAt(toOffset(n.getQueuedAt()));
            model.setSentAt(toOffset(n.getSentAt()));
            return model;
        }).toList();
    }
}
