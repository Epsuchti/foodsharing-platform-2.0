package network.foodsharing.region.repository;

import java.util.List;
import java.util.UUID;
import network.foodsharing.region.domain.RegionPostcode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionPostcodeRepository extends JpaRepository<RegionPostcode, UUID> {

    List<RegionPostcode> findByCountryAndPostcode(String country, String postcode);

    List<RegionPostcode> findByRegionId(UUID regionId);

    void deleteByRegionId(UUID regionId);
}
