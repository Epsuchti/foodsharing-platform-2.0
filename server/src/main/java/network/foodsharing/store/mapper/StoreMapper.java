package network.foodsharing.store.mapper;

import static network.foodsharing.common.mapper.TimeMapper.toOffset;

import java.util.List;
import network.foodsharing.api.model.ChainRef;
import network.foodsharing.api.model.StoreAddress;
import network.foodsharing.api.model.StoreContact;
import network.foodsharing.api.model.StoreDetail;
import network.foodsharing.api.model.StoreListItem;
import network.foodsharing.api.model.StoreMetadata;
import network.foodsharing.api.model.StorePermissions;
import network.foodsharing.api.model.StoreTeamCounts;
import network.foodsharing.api.model.WeightCategory;
import network.foodsharing.region.mapper.RegionMapper;
import network.foodsharing.store.domain.PickupWeightCategory;
import network.foodsharing.store.domain.Store;
import network.foodsharing.store.domain.StoreMembershipStatus;
import network.foodsharing.store.service.StorePermissionService;
import network.foodsharing.store.service.StoreQueryService;
import network.foodsharing.store.service.StoreService.StoreInput;
import network.foodsharing.user.mapper.UserMapper;
import org.springframework.stereotype.Component;

/** Store models ↔ service views; domain and API enums share constant names. */
@Component
public class StoreMapper {

    private final UserMapper users;
    private final RegionMapper regions;

    public StoreMapper(UserMapper users, RegionMapper regions) {
        this.users = users;
        this.regions = regions;
    }

    static <E extends Enum<E>> E convert(Enum<?> value, Class<E> type) {
        return value == null ? null : Enum.valueOf(type, value.name());
    }

    public static StoreAddress address(Store store) {
        StoreAddress address = new StoreAddress();
        address.setStreet(store.getStreet());
        address.setPostcode(store.getPostcode());
        address.setCity(store.getCity());
        address.setCountry(store.getCountry());
        return address;
    }

    public static ChainRef chainRef(network.foodsharing.store.domain.StoreChain chain) {
        if (chain == null) {
            return null;
        }
        ChainRef ref = new ChainRef();
        ref.setId(chain.getId());
        ref.setName(chain.getName());
        return ref;
    }

    public static network.foodsharing.api.model.StoreCategory category(
            network.foodsharing.store.domain.StoreCategory category, Long count) {
        if (category == null) {
            return null;
        }
        network.foodsharing.api.model.StoreCategory model = new network.foodsharing.api.model.StoreCategory();
        model.setId(category.getId());
        model.setName(category.getName());
        model.setType(category.getType());
        model.setStoreCount(count == null ? null : count.intValue());
        return model;
    }

    public static network.foodsharing.api.model.Grocery grocery(network.foodsharing.store.domain.Grocery grocery,
                                                               Long count) {
        network.foodsharing.api.model.Grocery model = new network.foodsharing.api.model.Grocery();
        model.setId(grocery.getId());
        model.setName(grocery.getName());
        model.setStoreCount(count == null ? null : count.intValue());
        return model;
    }

    public static WeightCategory weight(PickupWeightCategory weight) {
        if (weight == null) {
            return null;
        }
        WeightCategory model = new WeightCategory();
        model.setCode(weight.getCode());
        model.setLabel(weight.getCode());
        model.setAverageKg(weight.getAverageKg().doubleValue());
        return model;
    }

    public StoreMetadata toMetadata(StoreQueryService.Metadata metadata) {
        StoreMetadata model = new StoreMetadata();
        model.setCategories(metadata.categories().stream().map(c -> category(c, null)).toList());
        model.setGroceries(metadata.groceries().stream().map(g -> grocery(g, null)).toList());
        model.setWeightCategories(metadata.weights().stream().map(StoreMapper::weight).toList());
        model.setChains(metadata.chains().stream().map(StoreMapper::chainRef).toList());
        return model;
    }

    public StoreListItem toListItem(StoreQueryService.ListItem item) {
        Store store = item.store();
        StoreListItem model = new StoreListItem();
        model.setId(store.getId());
        model.setName(store.getName());
        model.setAddress(address(store));
        model.setLatitude(store.getLatitude());
        model.setLongitude(store.getLongitude());
        model.setCategory(category(item.category(), null));
        model.setChain(chainRef(item.chain()));
        model.setCooperationStatus(convert(store.getCooperationStatus(),
                network.foodsharing.api.model.CooperationStatus.class));
        model.setTeamStatus(convert(store.getTeamStatus(), network.foodsharing.api.model.TeamSearchStatus.class));
        model.setRegion(regions.toRef(item.region()));
        model.setMembership(convert(item.membership(), network.foodsharing.api.model.UserTeamStatus.class));
        model.setIsManager(item.membership() == network.foodsharing.store.domain.UserTeamStatus.COORDINATOR);
        model.setNextPickupAt(item.slots() == null ? null : toOffset(item.slots().nextPickupAt()));
        model.setFreeSlotStatus(item.slots() == null || item.slots().freeSlotStatus() == null
                ? network.foodsharing.api.model.FreeSlotStatus.GREEN
                : convert(item.slots().freeSlotStatus(), network.foodsharing.api.model.FreeSlotStatus.class));
        model.setFreeSlotCount(item.slots() == null ? 0 : item.slots().freeSlotCount());
        return model;
    }

    public StorePermissions toPermissions(StorePermissionService.Rights r) {
        StorePermissions model = new StorePermissions();
        model.setMayAccess(r.mayAccess());
        model.setMayReadWall(r.mayReadWall());
        model.setMayPostWall(r.mayPostWall());
        model.setMayDeleteAnyWallPost(r.mayDeleteAnyWallPost());
        model.setMayEditStore(r.mayEditStore());
        model.setMayEditTeam(r.mayEditTeam());
        model.setMaySeePhoneNumbers(r.maySeePhoneNumbers());
        model.setMaySeeMemberDistance(r.maySeeMemberDistance());
        model.setMaySeePickups(r.maySeePickups());
        model.setMayEditPickups(r.mayEditPickups());
        model.setMayConfirmPickups(r.mayConfirmPickups());
        model.setMaySeePickupHistory(r.maySeePickupHistory());
        model.setMaySeeStoreLog(r.maySeeStoreLog());
        model.setMayDelete(r.mayDelete());
        model.setMayApply(r.mayApply());
        model.setMayLeave(r.mayLeave());
        model.setMayChatTeam(r.mayChatTeam());
        model.setMayChatJumper(r.mayChatJumper());
        model.setIsKam(r.isKam());
        model.setIsManager(r.isManager());
        model.setTeamStatus(convert(r.teamStatus(), network.foodsharing.api.model.UserTeamStatus.class));
        return model;
    }

    public StoreDetail toDetail(StoreQueryService.Detail d) {
        Store s = d.store();
        boolean sensitive = d.rights().mayReadWall();
        StoreDetail model = new StoreDetail();
        model.setId(s.getId());
        model.setName(s.getName());
        model.setAddress(address(s));
        model.setLatitude(s.getLatitude());
        model.setLongitude(s.getLongitude());
        model.setCategory(category(d.category(), null));
        model.setChain(chainRef(d.chain()));
        model.setGroceries(d.groceries().stream().map(g -> grocery(g, null)).toList());
        model.setCooperationStatus(convert(s.getCooperationStatus(), network.foodsharing.api.model.CooperationStatus.class));
        model.setStatusDate(s.getStatusDate());
        model.setCooperationStart(s.getCooperationStart());
        model.setPublicity(convert(s.getPublicity(), network.foodsharing.api.model.PublicityStatus.class));
        model.setSticker(convert(s.getSticker(), network.foodsharing.api.model.StickerStatus.class));
        model.setPublicInfo(s.getPublicInfo());
        model.setPublicPickupTime(convert(s.getPublicPickupTime(), network.foodsharing.api.model.PublicPickupTime.class));
        model.setWeightCategory(s.getWeightCategory());
        model.setPrefetchDays(s.getPrefetchDays());
        model.setUseRegionPickupRule(s.isUseRegionPickupRule());
        model.setTeamStatus(convert(s.getTeamStatus(), network.foodsharing.api.model.TeamSearchStatus.class));
        model.setDescription(s.getDescription());
        if (sensitive) {
            model.setConvinceStatus(convert(s.getConvinceStatus(), network.foodsharing.api.model.ConvinceStatus.class));
            StoreContact contact = new StoreContact();
            contact.setPerson(s.getContactPerson());
            contact.setPhone(s.getContactPhone());
            contact.setFax(s.getContactFax());
            contact.setEmail(s.getContactEmail());
            model.setContact(contact);
            model.setSpecialNotes(s.getSpecialNotes());
            model.setRequiresHygieneCertificate(s.isRequiresHygiene());
            model.setRequiresVerified(s.isRequiresVerified());
            model.setRequiresPhone(s.isRequiresPhone());
            model.setRequiresApplicationText(s.isRequiresApplicationText());
        }
        model.setRegion(regions.toRef(d.region()));
        model.setTimeZone(d.timeZone());
        model.setManagers(d.managers().stream().map(users::toSummary).toList());
        StoreTeamCounts counts = new StoreTeamCounts();
        counts.setMembers(count(d, StoreMembershipStatus.MEMBER));
        counts.setJumpers(count(d, StoreMembershipStatus.JUMPER));
        counts.setApplied(count(d, StoreMembershipStatus.APPLIED));
        counts.setInvited(count(d, StoreMembershipStatus.INVITED));
        model.setTeamCounts(counts);
        model.setMembership(convert(d.rights().teamStatus(), network.foodsharing.api.model.UserTeamStatus.class));
        model.setPermissions(toPermissions(d.rights()));
        model.setCreatedAt(toOffset(s.getCreatedAt()));
        model.setUpdatedAt(toOffset(s.getUpdatedAt()));
        return model;
    }

    private static int count(StoreQueryService.Detail d, StoreMembershipStatus status) {
        return d.teamCounts().getOrDefault(status, 0L).intValue();
    }

    public network.foodsharing.api.model.StoreTeam toTeam(network.foodsharing.store.service.StoreTeamService.TeamView v) {
        network.foodsharing.api.model.StoreTeam model = new network.foodsharing.api.model.StoreTeam();
        model.setMembers(v.members().stream().map(this::toMember).toList());
        model.setJumpers(v.jumpers().stream().map(this::toMember).toList());
        if (v.applications() != null) {
            model.setApplications(v.applications().stream().map(a -> {
                network.foodsharing.api.model.StoreApplication app = new network.foodsharing.api.model.StoreApplication();
                app.setUser(users.toSummary(a.user()));
                app.setText(a.membership().getApplicationText());
                app.setAppliedAt(toOffset(a.membership().getCreatedAt()));
                return app;
            }).toList());
        }
        if (v.invitations() != null) {
            model.setInvitations(v.invitations().stream().map(i -> {
                network.foodsharing.api.model.StoreInvitation inv = new network.foodsharing.api.model.StoreInvitation();
                inv.setUser(users.toSummary(i.user()));
                var by = i.membership().getInvitedBy() == null ? null : v.inviters().get(i.membership().getInvitedBy());
                inv.setInvitedBy(by == null ? null : users.toSummary(by));
                inv.setInvitedAt(toOffset(i.membership().getCreatedAt()));
                return inv;
            }).toList());
        }
        return model;
    }

    private network.foodsharing.api.model.TeamMember toMember(
            network.foodsharing.store.service.StoreTeamService.TeamMemberView m) {
        network.foodsharing.api.model.TeamMember model = new network.foodsharing.api.model.TeamMember();
        model.setUser(users.toSummary(m.user(), m.withPhone()));
        model.setIsManager(m.membership().isManager());
        model.setStatus(convert(m.membership().getStatus(), network.foodsharing.api.model.StoreMembershipStatus.class));
        model.setVerified(m.user().isVerified());
        model.setHygieneCertificateUntil(toOffset(m.hygieneUntil()));
        model.setPickupCount(m.stats().pickupCount());
        model.setFirstPickupAt(toOffset(m.stats().firstPickupAt()));
        model.setLastPickupAt(toOffset(m.stats().lastPickupAt()));
        model.setAddedAt(toOffset(m.membership().getAddedAt()));
        model.setDistanceKm(m.distanceKm());
        model.setSleeping(users.isSleeping(m.user()));
        model.setPhone(m.withPhone() ? m.user().getMobilePhone() : null);
        return model;
    }

    public StoreInput toInput(network.foodsharing.api.model.StoreCreate c) {
        StoreAddress a = c.getAddress() == null ? new StoreAddress() : c.getAddress();
        StoreContact k = c.getContact() == null ? new StoreContact() : c.getContact();
        return new StoreInput(c.getName(), a.getStreet(), a.getPostcode(), a.getCity(), a.getCountry(),
                c.getLatitude(), c.getLongitude(), c.getCategoryId(), c.getChainId(),
                c.getGroceryIds() == null ? List.of() : c.getGroceryIds(),
                convert(c.getCooperationStatus(), network.foodsharing.store.domain.CooperationStatus.class),
                c.getCooperationStart(), convert(c.getConvinceStatus(), network.foodsharing.store.domain.ConvinceStatus.class),
                k.getPerson(), k.getPhone(), k.getFax(), k.getEmail(),
                convert(c.getPublicity(), network.foodsharing.store.domain.PublicityStatus.class),
                convert(c.getSticker(), network.foodsharing.store.domain.StickerStatus.class), c.getPublicInfo(),
                convert(c.getPublicPickupTime(), network.foodsharing.store.domain.PublicPickupTime.class),
                c.getWeightCategory(), c.getPrefetchDays(), c.getUseRegionPickupRule(),
                c.getRequiresHygieneCertificate(), c.getRequiresVerified(), c.getRequiresPhone(),
                c.getRequiresApplicationText(),
                convert(c.getTeamStatus(), network.foodsharing.store.domain.TeamSearchStatus.class),
                c.getSpecialNotes(), c.getDescription(), null, c.getFirstWallPost(), null);
    }

    public StoreInput toInput(network.foodsharing.api.model.StorePatch p) {
        StoreAddress a = p.getAddress() == null ? new StoreAddress() : p.getAddress();
        StoreContact k = p.getContact() == null ? new StoreContact() : p.getContact();
        return new StoreInput(p.getName(), a.getStreet(), a.getPostcode(), a.getCity(), a.getCountry(),
                p.getLatitude(), p.getLongitude(), p.getCategoryId(), p.getChainId(), p.getGroceryIds(),
                convert(p.getCooperationStatus(), network.foodsharing.store.domain.CooperationStatus.class),
                p.getCooperationStart(), convert(p.getConvinceStatus(), network.foodsharing.store.domain.ConvinceStatus.class),
                k.getPerson(), k.getPhone(), k.getFax(), k.getEmail(),
                convert(p.getPublicity(), network.foodsharing.store.domain.PublicityStatus.class),
                convert(p.getSticker(), network.foodsharing.store.domain.StickerStatus.class), p.getPublicInfo(),
                convert(p.getPublicPickupTime(), network.foodsharing.store.domain.PublicPickupTime.class),
                p.getWeightCategory(), p.getPrefetchDays(), p.getUseRegionPickupRule(),
                p.getRequiresHygieneCertificate(), p.getRequiresVerified(), p.getRequiresPhone(),
                p.getRequiresApplicationText(),
                convert(p.getTeamStatus(), network.foodsharing.store.domain.TeamSearchStatus.class),
                p.getSpecialNotes(), p.getDescription(), p.getRegionId(), null, p.getReason());
    }

    public network.foodsharing.api.model.WallPost toWallPost(network.foodsharing.store.service.StoreWallService.PostView v) {
        network.foodsharing.api.model.WallPost model = new network.foodsharing.api.model.WallPost();
        model.setId(v.post().getId());
        model.setAuthor(v.author() == null ? null : users.toSummary(v.author()));
        model.setText(v.post().getText());
        model.setMilestone(network.foodsharing.api.model.WallMilestone.valueOf(v.post().getMilestone().name()));
        model.setCreatedAt(network.foodsharing.common.mapper.TimeMapper.toOffset(v.post().getCreatedAt()));
        model.setMayDelete(v.mayDelete());
        return model;
    }

    public network.foodsharing.api.model.StoreStatistics toStatistics(
            network.foodsharing.store.service.StoreStatisticsService.Statistics s) {
        network.foodsharing.api.model.StoreStatistics model = new network.foodsharing.api.model.StoreStatistics();
        model.setTotalPickups(s.totalPickups());
        model.setTotalKg(s.totalKg());
        model.setWeightCategory(weight(s.weightCategory()));
        model.setMonths(s.months().stream().map(m -> {
            network.foodsharing.api.model.StoreStatisticsMonth month = new network.foodsharing.api.model.StoreStatisticsMonth();
            month.setMonth(m.month().toString());
            month.setPickups(m.pickups());
            month.setKg(m.kg());
            return month;
        }).toList());
        if (s.perMember() != null) {
            model.setPerMember(s.perMember().stream().map(m -> {
                network.foodsharing.api.model.StoreMemberPickups row = new network.foodsharing.api.model.StoreMemberPickups();
                row.setUser(users.toSummary(m.user()));
                row.setPickups(m.pickups());
                return row;
            }).toList());
        }
        return model;
    }

    public network.foodsharing.api.model.StoreChain toChain(network.foodsharing.store.service.StoreChainService.ChainView v) {
        var c = v.chain();
        network.foodsharing.api.model.StoreChain model = new network.foodsharing.api.model.StoreChain();
        model.setId(c.getId());
        model.setName(c.getName());
        network.foodsharing.api.model.ChainHeadquarters hq = new network.foodsharing.api.model.ChainHeadquarters();
        hq.setStreet(c.getHqStreet());
        hq.setPostcode(c.getHqPostcode());
        hq.setCity(c.getHqCity());
        hq.setCountry(c.getHqCountry());
        model.setHeadquarters(hq);
        model.setStatus(network.foodsharing.api.model.StoreChainStatus.valueOf(c.getStatus().name()));
        model.setPressAllowed(c.isPressAllowed());
        model.setForumThreadId(c.getForumThreadId());
        model.setNotes(v.showNotes() ? c.getNotes() : null);
        model.setCommonStoreInformation(c.getCommonStoreInformation());
        model.setEstimatedStoreCount(c.getEstimatedStoreCount());
        model.setStoreCount(v.storeCount());
        model.setKams(v.kams().stream().map(users::toSummary).toList());
        model.setUpdatedAt(network.foodsharing.common.mapper.TimeMapper.toOffset(c.getUpdatedAt()));
        return model;
    }

    public network.foodsharing.store.service.StoreChainService.ChainInput toChainInput(
            network.foodsharing.api.model.StoreChainUpdate u) {
        var hq = u.getHeadquarters();
        return new network.foodsharing.store.service.StoreChainService.ChainInput(u.getName(),
                hq == null ? null : hq.getStreet(), hq == null ? null : hq.getPostcode(),
                hq == null ? null : hq.getCity(), hq == null ? null : hq.getCountry(),
                u.getStatus() == null ? null : network.foodsharing.store.domain.StoreChainStatus.valueOf(u.getStatus().name()),
                u.getPressAllowed(), u.getForumThreadId(), u.getNotes(), u.getCommonStoreInformation(),
                u.getEstimatedStoreCount(), u.getKamIds());
    }
}
