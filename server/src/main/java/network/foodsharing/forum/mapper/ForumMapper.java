package network.foodsharing.forum.mapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ForumFollow;
import network.foodsharing.api.model.ForumLastPost;
import network.foodsharing.api.model.ForumPost;
import network.foodsharing.api.model.ForumPostHidden;
import network.foodsharing.api.model.ForumReaction;
import network.foodsharing.api.model.ForumSearchHit;
import network.foodsharing.api.model.ForumSearchPage;
import network.foodsharing.api.model.ForumThread;
import network.foodsharing.api.model.ForumThreadPage;
import network.foodsharing.api.model.ForumThreadPermissions;
import network.foodsharing.api.model.ForumThreadSummary;
import network.foodsharing.api.model.ReactionKey;
import network.foodsharing.api.model.ThreadStatus;
import network.foodsharing.api.model.UserSummary;
import network.foodsharing.common.mapper.PageMapper;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.forum.domain.ForumPostReaction;
import network.foodsharing.forum.domain.ForumThreadFollow;
import network.foodsharing.forum.service.ForumRules;
import network.foodsharing.forum.service.ForumViews;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.mapper.UserMapper;
import network.foodsharing.user.service.UserService;
import org.springframework.stereotype.Component;

/** Forum entities → API models. Hidden post bodies are visible to moderators and the author only. */
@Component
public class ForumMapper {

    private final UserService users;
    private final UserMapper userMapper;

    public ForumMapper(UserService users, UserMapper userMapper) {
        this.users = users;
        this.userMapper = userMapper;
    }

    public static network.foodsharing.forum.domain.ReactionKey toDomain(ReactionKey key) {
        if (key == null) {
            return null;
        }
        return "100".equals(key.getValue()) ? network.foodsharing.forum.domain.ReactionKey.HUNDRED
                : network.foodsharing.forum.domain.ReactionKey.valueOf(key.getValue().toUpperCase());
    }

    public static ReactionKey toModel(network.foodsharing.forum.domain.ReactionKey key) {
        return ReactionKey.fromValue(key == network.foodsharing.forum.domain.ReactionKey.HUNDRED ? "100"
                : key.name().toLowerCase());
    }

    public static network.foodsharing.forum.domain.ThreadStatus toDomain(ThreadStatus status) {
        return status == null ? null : network.foodsharing.forum.domain.ThreadStatus.valueOf(status.getValue());
    }

    public static ForumFollow toFollow(ForumThreadFollow follow) {
        return new ForumFollow().bell(follow != null && follow.isBell()).mail(follow != null && follow.isMail());
    }

    public ForumThreadPage toPage(ForumViews.ThreadListing listing) {
        List<network.foodsharing.forum.domain.ForumThread> threads = listing.page().getContent();
        Map<UUID, UserSummary> summaries = summaries(threadUserIds(threads));
        return new ForumThreadPage().page(PageMapper.toPageMeta(listing.page())).items(threads.stream()
                .map(t -> toSummary(t, listing.follows().get(t.getId()), summaries)).toList());
    }

    public ForumSearchPage toSearchPage(ForumViews.SearchResult result) {
        List<network.foodsharing.forum.domain.ForumThread> threads = result.hits().stream()
                .map(ForumViews.SearchHit::thread).toList();
        Map<UUID, UserSummary> summaries = summaries(threadUserIds(threads));
        return new ForumSearchPage().page(PageMapper.toPageMeta(result.page())).items(result.hits().stream()
                .map(hit -> new ForumSearchHit().thread(toSummary(hit.thread(), hit.follow(), summaries))
                        .snippet(hit.snippet())).toList());
    }

    public ForumThread toThread(ForumViews.ThreadDetails details) {
        network.foodsharing.forum.domain.ForumThread thread = details.thread();
        Set<UUID> ids = threadUserIds(List.of(thread));
        details.posts().forEach(p -> {
            ids.add(p.getAuthorId());
            if (p.getHiddenBy() != null) {
                ids.add(p.getHiddenBy());
            }
        });
        details.reactions().forEach(r -> ids.add(r.getUserId()));
        Map<UUID, UserSummary> summaries = summaries(ids);
        ForumThreadSummary summary = toSummary(thread, details.follow(), summaries);
        ForumRules.Actor actor = details.actor();
        ForumRules.Forum forum = details.forum();
        Map<UUID, List<ForumPostReaction>> byPost = details.reactions().stream()
                .collect(Collectors.groupingBy(ForumPostReaction::getPostId));
        ForumThread result = new ForumThread().id(summary.getId()).regionId(summary.getRegionId())
                .ambassadorForum(summary.getAmbassadorForum()).title(summary.getTitle()).author(summary.getAuthor())
                .createdAt(summary.getCreatedAt()).status(summary.getStatus()).sticky(summary.getSticky())
                .active(summary.getActive()).postCount(summary.getPostCount()).lastPost(summary.getLastPost())
                .following(summary.getFollowing());
        result.setPosts(details.posts().stream()
                .map(p -> toPost(p, byPost.getOrDefault(p.getId(), List.of()), actor, forum, summaries)).toList());
        result.setPermissions(new ForumThreadPermissions()
                .mayPost(ForumRules.mayPostToThread(actor, forum, thread.getStatus(), thread.isActive()))
                .mayModerate(ForumRules.mayModerate(actor, forum))
                .mayHide(ForumRules.mayHidePosts(actor, forum))
                .mayRename(ForumRules.mayRename(actor, forum, thread.getAuthorId()))
                .mayDelete(ForumRules.mayDeleteThread(actor, forum, thread.isActive()))
                .mayChangeSticky(ForumRules.mayChangeSticky(actor, forum)));
        return result;
    }

    public ForumPost toPost(ForumViews.PostDetails details) {
        Set<UUID> ids = new HashSet<>();
        ids.add(details.post().getAuthorId());
        if (details.post().getHiddenBy() != null) {
            ids.add(details.post().getHiddenBy());
        }
        details.reactions().forEach(r -> ids.add(r.getUserId()));
        return toPost(details.post(), details.reactions(), details.actor(), details.forum(), summaries(ids));
    }

    private ForumPost toPost(network.foodsharing.forum.domain.ForumPost post, List<ForumPostReaction> reactions,
                             ForumRules.Actor actor, ForumRules.Forum forum, Map<UUID, UserSummary> summaries) {
        boolean author = actor.userId().equals(post.getAuthorId());
        boolean moderator = ForumRules.mayModerate(actor, forum);
        ForumPost result = new ForumPost().id(post.getId()).author(summaries.get(post.getAuthorId()))
                .createdAt(TimeMapper.toOffset(post.getCreatedAt())).editedAt(TimeMapper.toOffset(post.getEditedAt()))
                .mayEdit(ForumRules.mayEditPost(actor, forum, post.getAuthorId()))
                .mayDelete(ForumRules.mayDeletePost(actor, post.getAuthorId()))
                .mayHide(post.isHidden() ? ForumRules.mayUnhidePosts(actor, forum) : ForumRules.mayHidePosts(actor, forum));
        if (!post.isHidden() || author || moderator) {
            result.setBody(post.getBody());
        }
        if (post.isHidden()) {
            result.setHidden(new ForumPostHidden().at(TimeMapper.toOffset(post.getHiddenAt()))
                    .by(post.getHiddenBy() == null ? null : summaries.get(post.getHiddenBy()))
                    .reason(post.getHiddenReason()));
        }
        Map<network.foodsharing.forum.domain.ReactionKey, List<UserSummary>> grouped = new LinkedHashMap<>();
        for (ForumPostReaction reaction : reactions) {
            UserSummary user = summaries.get(reaction.getUserId());
            if (user != null) {
                grouped.computeIfAbsent(reaction.getReactionKey(), k -> new ArrayList<>()).add(user);
            }
        }
        result.setReactions(grouped.entrySet().stream()
                .map(e -> new ForumReaction().key(toModel(e.getKey())).users(e.getValue())).toList());
        return result;
    }

    private ForumThreadSummary toSummary(network.foodsharing.forum.domain.ForumThread thread, ForumThreadFollow follow,
                                         Map<UUID, UserSummary> summaries) {
        ForumThreadSummary summary = new ForumThreadSummary().id(thread.getId()).regionId(thread.getRegionId())
                .ambassadorForum(thread.isAmbassadorForum()).title(thread.getTitle())
                .author(summaries.get(thread.getAuthorId())).createdAt(TimeMapper.toOffset(thread.getCreatedAt()))
                .status(ThreadStatus.fromValue(thread.getStatus().name())).sticky(thread.isSticky())
                .active(thread.isActive()).postCount(thread.getPostCount()).following(toFollow(follow));
        if (thread.getLastPostAt() != null) {
            summary.setLastPost(new ForumLastPost().at(TimeMapper.toOffset(thread.getLastPostAt()))
                    .author(thread.getLastPostAuthorId() == null ? null : summaries.get(thread.getLastPostAuthorId())));
        }
        return summary;
    }

    private static Set<UUID> threadUserIds(Collection<network.foodsharing.forum.domain.ForumThread> threads) {
        Set<UUID> ids = new HashSet<>();
        for (var thread : threads) {
            ids.add(thread.getAuthorId());
            if (thread.getLastPostAuthorId() != null) {
                ids.add(thread.getLastPostAuthorId());
            }
        }
        return ids;
    }

    private Map<UUID, UserSummary> summaries(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<User> found = users.findAll(ids);
        userMapper.prefetchHomeRegions(found);
        return found.stream().collect(Collectors.toMap(User::getId, userMapper::toSummary,
                (a, b) -> a, LinkedHashMap::new));
    }
}
