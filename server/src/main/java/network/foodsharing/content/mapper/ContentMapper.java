package network.foodsharing.content.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.BlogPost;
import network.foodsharing.api.model.BlogPostPage;
import network.foodsharing.api.model.BlogPostSummary;
import network.foodsharing.api.model.ContentPage;
import network.foodsharing.api.model.ContentPageKind;
import network.foodsharing.api.model.DonationPage;
import network.foodsharing.api.model.FaqEntry;
import network.foodsharing.api.model.Language;
import network.foodsharing.api.model.Petition;
import network.foodsharing.api.model.RegionRef;
import network.foodsharing.api.model.ReleaseNote;
import network.foodsharing.api.model.TeamGroup;
import network.foodsharing.api.model.TeamPageMember;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.content.service.BlogService;
import network.foodsharing.content.service.CampaignService;
import network.foodsharing.content.service.ContentService;
import network.foodsharing.content.service.ReleaseNoteService;
import network.foodsharing.content.service.TeamPageService;
import network.foodsharing.region.domain.Region;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.region.service.RegionService;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Content domain (blog, release notes, team, campaigns, FAQ) → API models (B11). */
@Component
public class ContentMapper {

    private final UserService users;
    private final UserMapper userMapper;
    private final RegionService regions;
    private final RegionMapper regionMapper;

    public ContentMapper(UserService users, UserMapper userMapper, RegionService regions, RegionMapper regionMapper) {
        this.users = users;
        this.userMapper = userMapper;
        this.regions = regions;
        this.regionMapper = regionMapper;
    }

    public static String pictureUrl(UUID uploadId) {
        return uploadId == null ? null : "/api/uploads/" + uploadId + "?size=lg";
    }

    public static ContentPage page(ContentService.LocalizedPage view) {
        ContentPage page = new ContentPage();
        page.setSlug(view.page().getSlug());
        page.setKind(ContentPageKind.fromValue(view.page().getKind().name()));
        page.setTitle(view.title());
        page.setBody(view.body());
        page.setLanguage(Language.fromValue(view.language()));
        page.setFallback(view.fallback());
        page.setUpdatedAt(TimeMapper.toOffset(view.updatedAt()));
        page.setEstimatedMinutes(view.page().getEstimatedMinutes());
        page.setNextChapterKey(view.nextChapterKey());
        return page;
    }

    public BlogPostPage toPage(Page<network.foodsharing.content.domain.BlogPost> rows) {
        Lookups lookups = lookups(rows.getContent());
        BlogPostPage page = new BlogPostPage();
        page.setItems(rows.getContent().stream().map(post -> summary(post, lookups)).toList());
        page.setPage(PageMapper.toPageMeta(rows));
        return page;
    }

    public BlogPostPage toEditPage(Page<BlogService.PostView> rows) {
        Lookups lookups = lookups(rows.getContent().stream().map(BlogService.PostView::post).toList());
        BlogPostPage page = new BlogPostPage();
        page.setItems(rows.getContent().stream().map(view -> summary(view.post(), lookups)).toList());
        page.setPage(PageMapper.toPageMeta(rows));
        return page;
    }

    public BlogPost toPost(network.foodsharing.content.domain.BlogPost post) {
        return post(post, lookups(List.of(post)));
    }

    public BlogPost toPost(BlogService.PostView view) {
        BlogPost model = post(view.post(), lookups(List.of(view.post())));
        model.setMayEdit(view.mayEdit());
        model.setMayPublish(view.mayPublish());
        model.setPictureUploadId(view.post().getPictureUploadId());
        return model;
    }

    private BlogPost post(network.foodsharing.content.domain.BlogPost post, Lookups lookups) {
        BlogPost model = new BlogPost();
        model.setId(post.getId());
        model.setTitle(post.getTitle());
        model.setTeaser(post.getTeaser());
        model.setBody(post.getBody());
        model.setPictureUrl(pictureUrl(post.getPictureUploadId()));
        model.setPublishedAt(TimeMapper.toOffset(post.getPublishedAt()));
        model.setAuthor(lookups.users().get(post.getAuthorId()));
        model.setRegion(post.getRegionId() == null ? null : lookups.regions().get(post.getRegionId()));
        model.setPublished(post.isPublished());
        model.setAwaitingCheck(post.isAwaitingCheck());
        model.setMayEdit(false);
        return model;
    }

    private static BlogPostSummary summary(network.foodsharing.content.domain.BlogPost post, Lookups lookups) {
        BlogPostSummary model = new BlogPostSummary();
        model.setId(post.getId());
        model.setTitle(post.getTitle());
        model.setTeaser(post.getTeaser());
        model.setPictureUrl(pictureUrl(post.getPictureUploadId()));
        model.setPublishedAt(TimeMapper.toOffset(post.getPublishedAt()));
        model.setAuthor(lookups.users().get(post.getAuthorId()));
        model.setRegion(post.getRegionId() == null ? null : lookups.regions().get(post.getRegionId()));
        model.setPublished(post.isPublished());
        model.setAwaitingCheck(post.isAwaitingCheck());
        return model;
    }

    private record Lookups(Map<UUID, UserSummary> users, Map<UUID, RegionRef> regions) {
    }

    private Lookups lookups(Collection<network.foodsharing.content.domain.BlogPost> posts) {
        Set<UUID> userIds = posts.stream().map(network.foodsharing.content.domain.BlogPost::getAuthorId)
                .collect(Collectors.toSet());
        Set<UUID> regionIds = posts.stream().map(network.foodsharing.content.domain.BlogPost::getRegionId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, UserSummary> people = userIds.isEmpty() ? Map.of() : users.findAll(userIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toSummary, (a, b) -> a));
        Map<UUID, RegionRef> refs = regionIds.isEmpty() ? Map.of() : regions.findAll(regionIds).stream()
                .collect(Collectors.toMap(Region::getId, regionMapper::toRef, (a, b) -> a));
        return new Lookups(people, refs);
    }

    public static ReleaseNote toReleaseNote(ReleaseNoteService.LocalizedNote note) {
        ReleaseNote model = new ReleaseNote();
        model.setId(note.note().getId());
        model.setVersion(note.note().getReleaseVersion());
        model.setReleasedAt(note.note().getReleasedAt());
        model.setTitle(note.title());
        model.setBody(note.body());
        return model;
    }

    public static FaqEntry toFaq(ContentService.LocalizedPage page) {
        FaqEntry entry = new FaqEntry();
        entry.setSlug(page.page().getSlug());
        entry.setQuestion(page.title());
        entry.setAnswer(page.body());
        return entry;
    }

    public static TeamGroup toTeamGroup(TeamPageService.Group group) {
        TeamGroup model = new TeamGroup();
        model.setId(group.group().getId());
        model.setName(group.group().getName());
        model.setTeaser(group.group().getTeaser());
        model.setPhotoUrl(group.group().getPhotoUrl());
        model.setMembers(group.members().stream().map(member -> {
            TeamPageMember m = new TeamPageMember();
            m.setDisplayName(member.displayName());
            m.setAvatarUrl(member.avatarUrl());
            m.setRoleDescription(member.positionDescription());
            return m;
        }).toList());
        return model;
    }

    public static Petition toPetition(CampaignService.Petition petition) {
        Petition model = new Petition();
        model.setHref(petition.href());
        model.setSignatures(petition.signatures());
        model.setDaysLeft(petition.daysLeft());
        return model;
    }

    public static DonationPage toDonationPage(CampaignService.Donation donation) {
        DonationPage model = new DonationPage();
        model.setShowCampaignCard(donation.showCampaignCard());
        model.setShowModal(donation.showModal());
        model.setModalHoursLoggedIn(donation.modalHoursLoggedIn());
        model.setModalHoursLoggedOut(donation.modalHoursLoggedOut());
        model.setInfoUrl(donation.infoUrl());
        model.setIframeOneTimeUrl(donation.iframeOneTimeUrl());
        model.setIframeCampaignUrl(donation.iframeCampaignUrl());
        model.setIframeFriendshipCircleUrl(donation.iframeFriendshipCircleUrl());
        model.setIframeSelfServiceUrl(donation.iframeSelfServiceUrl());
        model.setCampaignPages(donation.campaignPages().stream().map(ContentMapper::page).toList());
        return model;
    }
}
