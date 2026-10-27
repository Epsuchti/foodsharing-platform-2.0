package network.foodsharing.onboarding.service;

/**
 * SPI for the impact counters of the public explainer page: each bean adds its numbers (M2: foodsavers and regions;
 * M3 stores, pickups and kilograms).
 */
public interface ExplainerCounterContributor {

    /** Mutable counters the contributors add to. */
    final class Counters {
        long foodsavers;
        long regions;
        double kgSaved;
        long pickups;
        long stores;

        public void addFoodsavers(long value) {
            foodsavers += value;
        }

        public void addRegions(long value) {
            regions += value;
        }

        public void addKgSaved(double value) {
            kgSaved += value;
        }

        public void addPickups(long value) {
            pickups += value;
        }

        public void addStores(long value) {
            stores += value;
        }

        public long foodsavers() {
            return foodsavers;
        }

        public long regions() {
            return regions;
        }

        public double kgSaved() {
            return kgSaved;
        }

        public long pickups() {
            return pickups;
        }

        public long stores() {
            return stores;
        }
    }

    void contribute(Counters counters);
}
