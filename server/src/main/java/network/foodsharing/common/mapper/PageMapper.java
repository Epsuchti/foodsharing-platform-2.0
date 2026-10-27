package network.foodsharing.common.mapper;

import network.foodsharing.api.model.PageMeta;
import org.springframework.data.domain.Page;

/** Spring Data page metadata → generated {@link PageMeta}. */
public final class PageMapper {

    private PageMapper() {
    }

    public static PageMeta toPageMeta(Page<?> page) {
        PageMeta meta = new PageMeta();
        meta.setPage(page.getNumber());
        meta.setSize(page.getSize());
        meta.setTotalItems(page.getTotalElements());
        meta.setTotalPages(page.getTotalPages());
        return meta;
    }
}
