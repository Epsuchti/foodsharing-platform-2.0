package network.foodsharing.store.service;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import network.foodsharing.api.model.ApiErrorCode;
import network.foodsharing.api.model.Role;
import network.foodsharing.common.error.ApiException;
import network.foodsharing.history.service.HistoryService;
import network.foodsharing.security.CurrentActorService;
import network.foodsharing.security.PermissionService;
import network.foodsharing.security.SessionPrincipal;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreChainAction;
import network.foodsharing.store.domain.StoreChainKam;
import network.foodsharing.store.domain.StoreChainStatus;
import network.foodsharing.store.repository.StoreChainKamRepository;
import network.foodsharing.store.repository.StoreChainRepository;
import network.foodsharing.store.repository.StoreRepository;
import network.foodsharing.user.domain.User;
import network.foodsharing.user.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Store chains and key account managers (plan §13.3 S3). Everybody FOODSAVER+ reads chains; chain administrators
 * (ORGA or admins of a STORE_CHAINS working group, {@link StoreChainAccess}) create and edit them; a KAM of the chain
 * may edit it but not its KAM list (403 KAM_CANNOT_EDIT_KAMS). Notes are visible to administrators and the chain's KAMs.
 */
@Service
public class StoreChainService {

    /** Create / update input; on update null means unchanged. */
    public record ChainInput(String name, String hqStreet, String hqPostcode, String hqCity, String hqCountry,
                             StoreChainStatus status, Boolean pressAllowed, UUID forumThreadId, String notes,
                             String commonStoreInformation, Integer estimatedStoreCount, List<UUID> kamIds) {
    }

    public record ChainView(StoreChain chain, List<User> kams, int storeCount, boolean showNotes) {
    }

    private final StoreChainRepository chains;
    private final StoreChainKamRepository kams;
    private final StoreRepository stores;
    private final StoreChainAccess access;
    private final UserService users;
    private final CurrentActorService currentActor;
    private final HistoryService history;

    public StoreChainService(StoreChainRepository chains, StoreChainKamRepository kams, StoreRepository stores,
                             StoreChainAccess access, UserService users, CurrentActorService currentActor,
                             HistoryService history) {
        this.chains = chains;
        this.kams = kams;
        this.stores = stores;
        this.access = access;
        this.users = users;
        this.currentActor = currentActor;
        this.history = history;
    }

    /** @throws ApiException 403 below FOODSAVER */
    @Transactional(readOnly = true)
    public Page<ChainView> list(Collection<StoreChainStatus> statuses, String search, Integer page, Integer size) {
        SessionPrincipal actor = currentActor.requireRole(Role.FOODSAVER);
        String needle = search == null || search.isBlank() ? null : search.strip().toLowerCase(Locale.ROOT);
        List<StoreChain> all = chains.findAllByOrderByNameAsc().stream()
                .filter(c -> statuses == null || statuses.isEmpty() || statuses.contains(c.getStatus()))
                .filter(c -> needle == null || c.getName().toLowerCase(Locale.ROOT).contains(needle)
                        || (c.getHqCity() != null && c.getHqCity().toLowerCase(Locale.ROOT).contains(needle)))
                .toList();
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 20 : Math.max(1, Math.min(size, 100));
        int from = Math.min(all.size(), p * s);
        List<ChainView> content = all.subList(from, Math.min(all.size(), from + s)).stream()
                .map(c -> view(c, actor)).toList();
        return new PageImpl<>(content, PageRequest.of(p, s), all.size());
    }

    /** @throws ApiException 403 below FOODSAVER, 404 CHAIN_NOT_FOUND */
    @Transactional(readOnly = true)
    public ChainView get(UUID chainId) {
        SessionPrincipal actor = currentActor.requireRole(Role.FOODSAVER);
        return view(require(chainId), actor);
    }

    /** @throws ApiException 403 unless chain administrator, 400 VALIDATION_FAILED / INVALID_KAM */
    @Transactional
    public ChainView create(ChainInput input) {
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(access.mayAdministrateChains(actor));
        if (input.name() == null || input.name().isBlank()) {
            throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
        }
        StoreChain chain = new StoreChain();
        chain.setStatus(StoreChainStatus.WAITING);
        apply(chain, input);
        chains.save(chain);
        history.record(history.event(StoreChainAction.CHAIN_CREATED, chain.getId())
                .metadata("name", chain.getName()).build());
        if (input.kamIds() != null) {
            setKams(chain, input.kamIds());
        }
        return view(chain, actor);
    }

    /**
     * @throws ApiException 404 CHAIN_NOT_FOUND, 403 unless administrator or KAM of the chain, 403 KAM_CANNOT_EDIT_KAMS
     *                      when a KAM changes the KAM list, 400 VALIDATION_FAILED / INVALID_KAM
     */
    @Transactional
    public ChainView update(UUID chainId, ChainInput input) {
        StoreChain chain = require(chainId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        boolean admin = access.mayAdministrateChains(actor);
        boolean kam = isKam(actor, chainId);
        StorePermissionService.require(admin || kam);
        if (input.kamIds() != null && !admin && !Set.copyOf(input.kamIds()).equals(kamIds(chainId))) {
            throw ApiException.forbidden(ApiErrorCode.KAM_CANNOT_EDIT_KAMS);
        }
        apply(chain, input);
        history.record(history.event(StoreChainAction.CHAIN_UPDATED, chain.getId()).build());
        if (input.kamIds() != null && admin) {
            setKams(chain, input.kamIds());
        }
        return view(chain, actor);
    }

    /** @throws ApiException 404 CHAIN_NOT_FOUND, 403 unless administrator or KAM of the chain */
    @Transactional(readOnly = true)
    public void requireChainStoresAccess(UUID chainId) {
        require(chainId);
        SessionPrincipal actor = currentActor.requirePrincipal();
        StorePermissionService.require(access.mayAdministrateChains(actor) || isKam(actor, chainId));
    }

    private StoreChain require(UUID chainId) {
        return chains.findById(chainId).orElseThrow(() -> ApiException.notFound(ApiErrorCode.CHAIN_NOT_FOUND));
    }

    private boolean isKam(SessionPrincipal actor, UUID chainId) {
        return PermissionService.isAtLeast(actor.role(), Role.STORE_MANAGER)
                && kams.existsByChainIdAndUserId(chainId, actor.userId());
    }

    private Set<UUID> kamIds(UUID chainId) {
        return kams.findByChainId(chainId).stream().map(StoreChainKam::getUserId).collect(Collectors.toSet());
    }

    /** KAMs must be active, verified and at least STORE_MANAGER (400 INVALID_KAM). */
    private void setKams(StoreChain chain, List<UUID> wantedIds) {
        Set<UUID> wanted = new LinkedHashSet<>(wantedIds);
        Map<UUID, User> found = users.findActive(wanted).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        for (UUID id : wanted) {
            User user = found.get(id);
            if (user == null || !user.isVerified() || !PermissionService.isAtLeast(
                    user.getRole(), Role.STORE_MANAGER)) {
                throw ApiException.badRequest(ApiErrorCode.INVALID_KAM);
            }
        }
        List<StoreChainKam> current = kams.findByChainId(chain.getId());
        for (StoreChainKam k : current) {
            if (!wanted.contains(k.getUserId())) {
                kams.delete(k);
                history.record(history.event(StoreChainAction.KAM_REMOVED, chain.getId())
                        .secondarySubject(k.getUserId()).build());
            }
        }
        Set<UUID> have = current.stream().map(StoreChainKam::getUserId).collect(Collectors.toSet());
        for (UUID id : wanted) {
            if (!have.contains(id)) {
                StoreChainKam k = new StoreChainKam();
                k.setChainId(chain.getId());
                k.setUserId(id);
                kams.save(k);
                history.record(history.event(StoreChainAction.KAM_ADDED, chain.getId()).secondarySubject(id).build());
            }
        }
    }

    private void apply(StoreChain chain, ChainInput in) {
        if (in.name() != null) {
            if (in.name().isBlank()) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            chain.setName(in.name().strip());
        }
        if (in.hqStreet() != null) {
            chain.setHqStreet(blank(in.hqStreet()));
        }
        if (in.hqPostcode() != null) {
            chain.setHqPostcode(blank(in.hqPostcode()));
        }
        if (in.hqCity() != null) {
            chain.setHqCity(blank(in.hqCity()));
        }
        if (in.hqCountry() != null) {
            chain.setHqCountry(blank(in.hqCountry()));
        }
        if (in.status() != null) {
            chain.setStatus(in.status());
        }
        if (in.pressAllowed() != null) {
            chain.setPressAllowed(in.pressAllowed());
        }
        if (in.forumThreadId() != null) {
            chain.setForumThreadId(in.forumThreadId());
        }
        if (in.notes() != null) {
            chain.setNotes(blank(in.notes()));
        }
        if (in.commonStoreInformation() != null) {
            chain.setCommonStoreInformation(blank(in.commonStoreInformation()));
        }
        if (in.estimatedStoreCount() != null) {
            if (in.estimatedStoreCount() < 0) {
                throw ApiException.badRequest(ApiErrorCode.VALIDATION_FAILED);
            }
            chain.setEstimatedStoreCount(in.estimatedStoreCount());
        }
    }

    private ChainView view(StoreChain chain, SessionPrincipal actor) {
        List<User> kamUsers = users.findAll(kamIds(chain.getId())).stream()
                .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
        boolean showNotes = access.mayAdministrateChains(actor)
                || kamUsers.stream().anyMatch(u -> u.getId().equals(actor.userId()));
        return new ChainView(chain, kamUsers, (int) stores.countLiveByChain(chain.getId()), showNotes);
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
