package network.foodsharing.testsupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import network.foodsharing.region.service.RegionsCsvImportService;
import network.foodsharing.store.domain.StoreCategory;
import network.foodsharing.store.domain.StoreChain;
import network.foodsharing.store.domain.StoreChainStatus;
import network.foodsharing.store.repository.StoreCategoryRepository;
import network.foodsharing.store.repository.StoreChainRepository;

/** The Zürich initial-data files ({@code docs/data/zuerich}) and their production prerequisites (D16.x ITs). */
public final class ZuerichData {

    public static final Path DIR = Path.of("..", "docs", "data", "zuerich");

    private ZuerichData() {
    }

    public static String read(String file) throws IOException {
        return Files.readString(DIR.resolve(file), StandardCharsets.UTF_8);
    }

    /** Imports the regions file and creates the store categories and chains the stores file names (as ORGA). */
    public static void prepare(RegionsCsvImportService regionsImport, StoreCategoryRepository categories,
                               StoreChainRepository chains) throws IOException {
        regionsImport.importCsv(read("regions.csv"), false);
        for (String name : List.of("Supermarkt", "Bäckerei", "Restaurant", "Bio-Laden", "Markt", "Take-away", "Hotel")) {
            StoreCategory category = new StoreCategory();
            category.setName(name);
            categories.save(category);
        }
        for (String name : List.of("Coop", "Migros", "Denner", "Aldi Suisse", "Lidl Schweiz", "Volg")) {
            StoreChain chain = new StoreChain();
            chain.setName(name);
            chain.setStatus(StoreChainStatus.COOPERATING);
            chains.save(chain);
        }
    }
}
