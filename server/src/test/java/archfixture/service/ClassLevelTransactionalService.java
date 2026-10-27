package archfixture.service;

import org.springframework.transaction.annotation.Transactional;

/** Fixture: class-level @Transactional makes every public method a write unless marked read-only. */
@Transactional
public class ClassLevelTransactionalService {

    private int value;

    public void update() {
        value++;
    }

    @Transactional(readOnly = true)
    public int read() {
        return value;
    }
}
