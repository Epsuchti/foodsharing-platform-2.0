package archfixture.service;

import org.springframework.transaction.annotation.Transactional;

/** Fixture: violates rule 4 (mutates, records nothing). Not a Spring bean. */
public class MissingHistoryService {

    private int counter;

    @Transactional
    public void change() {
        counter++;
    }
}
