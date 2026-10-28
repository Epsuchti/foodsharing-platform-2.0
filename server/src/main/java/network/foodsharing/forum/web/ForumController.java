package network.foodsharing.forum.web;

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
import network.foodsharing.common.error.ApiException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Forums (F1-F5, G3, D10.3). Operations answer 501 until their task implements them (docs/plan-m5.md). */
@RestController
public class ForumController implements ForumApi {

    @Override
    public ResponseEntity<Void> addForumReaction(UUID postId, ReactionKey key) {
        throw ApiException.notImplemented("addForumReaction");
    }

    @Override
    public ResponseEntity<ForumPost> createForumPost(UUID threadId, ForumPostWrite forumPostWrite) {
        throw ApiException.notImplemented("createForumPost");
    }

    @Override
    public ResponseEntity<ForumThread> createForumThread(UUID regionId, ForumThreadCreate forumThreadCreate, Boolean ambassadorForum) {
        throw ApiException.notImplemented("createForumThread");
    }

    @Override
    public ResponseEntity<Void> deleteForumPost(UUID postId) {
        throw ApiException.notImplemented("deleteForumPost");
    }

    @Override
    public ResponseEntity<Void> deleteForumThread(UUID threadId) {
        throw ApiException.notImplemented("deleteForumThread");
    }

    @Override
    public ResponseEntity<ForumPost> editForumPost(UUID postId, ForumPostWrite forumPostWrite) {
        throw ApiException.notImplemented("editForumPost");
    }

    @Override
    public ResponseEntity<ForumFollow> followForumThread(UUID threadId, ForumFollow forumFollow) {
        throw ApiException.notImplemented("followForumThread");
    }

    @Override
    public ResponseEntity<ForumFollowState> getForumFollow(UUID regionId, Boolean ambassadorForum) {
        throw ApiException.notImplemented("getForumFollow");
    }

    @Override
    public ResponseEntity<ForumThread> getForumThread(UUID threadId) {
        throw ApiException.notImplemented("getForumThread");
    }

    @Override
    public ResponseEntity<HistoryEntryPage> getModerationLog(UUID regionId, OffsetDateTime from, OffsetDateTime to, Integer page, Integer size) {
        throw ApiException.notImplemented("getModerationLog");
    }

    @Override
    public ResponseEntity<ForumPost> hideForumPost(UUID postId, ForumHideRequest forumHideRequest) {
        throw ApiException.notImplemented("hideForumPost");
    }

    @Override
    public ResponseEntity<ForumThreadPage> listForumThreads(UUID regionId, Boolean ambassadorForum, Integer page, Integer size) {
        throw ApiException.notImplemented("listForumThreads");
    }

    @Override
    public ResponseEntity<Void> removeForumReaction(UUID postId, ReactionKey key) {
        throw ApiException.notImplemented("removeForumReaction");
    }

    @Override
    public ResponseEntity<ForumSearchPage> searchAllForums(String q, Boolean searchBody, Integer page, Integer size) {
        throw ApiException.notImplemented("searchAllForums");
    }

    @Override
    public ResponseEntity<ForumSearchPage> searchRegionForum(UUID regionId, String q, Boolean searchBody, Boolean ambassadorForum, Integer page, Integer size) {
        throw ApiException.notImplemented("searchRegionForum");
    }

    @Override
    public ResponseEntity<ForumFollowState> setForumFollow(UUID regionId, ForumFollowState forumFollowState, Boolean ambassadorForum) {
        throw ApiException.notImplemented("setForumFollow");
    }

    @Override
    public ResponseEntity<ForumPost> unhideForumPost(UUID postId) {
        throw ApiException.notImplemented("unhideForumPost");
    }

    @Override
    public ResponseEntity<ForumThread> updateForumThread(UUID threadId, ForumThreadUpdate forumThreadUpdate) {
        throw ApiException.notImplemented("updateForumThread");
    }
}
