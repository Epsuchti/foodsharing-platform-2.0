package network.foodsharing.forum.web;

import java.time.OffsetDateTime;
import java.util.UUID;
import network.foodsharing.api.ForumApi;
import network.foodsharing.api.model.ForumFollow;
import network.foodsharing.api.model.ForumFollowState;
import network.foodsharing.api.model.ForumHideRequest;
import network.foodsharing.api.model.ForumPost;
import network.foodsharing.api.model.ForumPostWrite;
import network.foodsharing.api.model.ForumSearchPage;
import network.foodsharing.api.model.ForumThread;
import network.foodsharing.api.model.ForumThreadCreate;
import network.foodsharing.api.model.ForumThreadPage;
import network.foodsharing.api.model.ForumThreadUpdate;
import network.foodsharing.api.model.HistoryEntryPage;
import network.foodsharing.api.model.ReactionKey;
import network.foodsharing.common.mapper.TimeMapper;
import network.foodsharing.forum.mapper.ForumMapper;
import network.foodsharing.forum.service.FollowService;
import network.foodsharing.forum.service.ForumSearchService;
import network.foodsharing.forum.service.ModerationLogService;
import network.foodsharing.forum.service.PostService;
import network.foodsharing.forum.service.ReactionService;
import network.foodsharing.forum.service.ThreadService;
import network.foodsharing.history.mapper.HistoryMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Region and ambassador forums, moderation, follows, search and moderation log (F1-F5, G3, D10.3; A36, A37). */
@RestController
public class ForumController implements ForumApi {

    private final ThreadService threads;
    private final PostService posts;
    private final ReactionService reactions;
    private final FollowService follows;
    private final ForumSearchService search;
    private final ModerationLogService moderationLog;
    private final ForumMapper mapper;
    private final HistoryMapper historyMapper;

    public ForumController(ThreadService threads, PostService posts, ReactionService reactions, FollowService follows,
                           ForumSearchService search, ModerationLogService moderationLog, ForumMapper mapper,
                           HistoryMapper historyMapper) {
        this.threads = threads;
        this.posts = posts;
        this.reactions = reactions;
        this.follows = follows;
        this.search = search;
        this.moderationLog = moderationLog;
        this.mapper = mapper;
        this.historyMapper = historyMapper;
    }

    @Override
    public ResponseEntity<ForumThreadPage> listForumThreads(UUID regionId, Boolean ambassadorForum, Integer page,
                                                            Integer size) {
        return ResponseEntity.ok(mapper.toPage(threads.list(regionId, Boolean.TRUE.equals(ambassadorForum),
                page == null ? 0 : page, size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<ForumThread> createForumThread(UUID regionId, ForumThreadCreate body,
                                                         Boolean ambassadorForum) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toThread(threads.create(regionId,
                Boolean.TRUE.equals(ambassadorForum), body.getTitle(), body.getBody(),
                Boolean.TRUE.equals(body.getFollowBell()), Boolean.TRUE.equals(body.getFollowMail()))));
    }

    @Override
    public ResponseEntity<ForumThread> getForumThread(UUID threadId) {
        return ResponseEntity.ok(mapper.toThread(threads.get(threadId)));
    }

    @Override
    public ResponseEntity<ForumThread> updateForumThread(UUID threadId, ForumThreadUpdate body) {
        return ResponseEntity.ok(mapper.toThread(threads.update(threadId, new ThreadService.ThreadChange(
                body.getTitle(), ForumMapper.toDomain(body.getStatus()), body.getSticky(), body.getActive()))));
    }

    @Override
    public ResponseEntity<Void> deleteForumThread(UUID threadId) {
        threads.delete(threadId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ForumFollow> followForumThread(UUID threadId, ForumFollow body) {
        FollowService.ThreadFollowState state = follows.followThread(threadId, Boolean.TRUE.equals(body.getBell()),
                Boolean.TRUE.equals(body.getMail()));
        return ResponseEntity.ok(new ForumFollow().bell(state.bell()).mail(state.mail()));
    }

    @Override
    public ResponseEntity<ForumFollowState> getForumFollow(UUID regionId, Boolean ambassadorForum) {
        return ResponseEntity.ok(new ForumFollowState()
                .following(follows.isFollowingForum(regionId, Boolean.TRUE.equals(ambassadorForum))));
    }

    @Override
    public ResponseEntity<ForumFollowState> setForumFollow(UUID regionId, ForumFollowState body,
                                                           Boolean ambassadorForum) {
        return ResponseEntity.ok(new ForumFollowState().following(follows.setForumFollow(regionId,
                Boolean.TRUE.equals(ambassadorForum), Boolean.TRUE.equals(body.getFollowing()))));
    }

    @Override
    public ResponseEntity<ForumPost> createForumPost(UUID threadId, ForumPostWrite body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toPost(posts.create(threadId, body.getBody())));
    }

    @Override
    public ResponseEntity<ForumPost> editForumPost(UUID postId, ForumPostWrite body) {
        return ResponseEntity.ok(mapper.toPost(posts.edit(postId, body.getBody())));
    }

    @Override
    public ResponseEntity<Void> deleteForumPost(UUID postId) {
        posts.delete(postId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ForumPost> hideForumPost(UUID postId, ForumHideRequest body) {
        return ResponseEntity.ok(mapper.toPost(posts.hide(postId, body.getReason())));
    }

    @Override
    public ResponseEntity<ForumPost> unhideForumPost(UUID postId) {
        return ResponseEntity.ok(mapper.toPost(posts.unhide(postId)));
    }

    @Override
    public ResponseEntity<Void> addForumReaction(UUID postId, ReactionKey key) {
        reactions.add(postId, ForumMapper.toDomain(key));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> removeForumReaction(UUID postId, ReactionKey key) {
        reactions.remove(postId, ForumMapper.toDomain(key));
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ForumSearchPage> searchRegionForum(UUID regionId, String q, Boolean searchBody,
                                                             Boolean ambassadorForum, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toSearchPage(search.searchRegion(regionId, Boolean.TRUE.equals(ambassadorForum),
                q, Boolean.TRUE.equals(searchBody), page == null ? 0 : page, size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<ForumSearchPage> searchAllForums(String q, Boolean searchBody, Integer page, Integer size) {
        return ResponseEntity.ok(mapper.toSearchPage(search.searchAll(q, Boolean.TRUE.equals(searchBody),
                page == null ? 0 : page, size == null ? 20 : size)));
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getModerationLog(UUID regionId, OffsetDateTime from, OffsetDateTime to,
                                                             Integer page, Integer size) {
        return ResponseEntity.ok(historyMapper.toPage(moderationLog.log(regionId, TimeMapper.toInstant(from),
                TimeMapper.toInstant(to), page == null ? 0 : page, size == null ? 20 : size)));
    }
}
