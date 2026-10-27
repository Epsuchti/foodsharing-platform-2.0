package archfixture.service;

import org.springframework.transaction.annotation.Transactional;

/** Fixture: read-only methods are not checked. */
public class ReadOnlyService {

    @Transactional(readOnly = true)
    public int read() {
        return 1;
    }
}
