
package be.cookies.studio.core.service;

import be.cookies.studio.core.model.NewsItem;
import java.util.List;

public record NewsLoadResult(
        List<NewsItem> news,
        boolean fromCache
) {
    public NewsLoadResult {
        news = List.copyOf(news);
    }
}
