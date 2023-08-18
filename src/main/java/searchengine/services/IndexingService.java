package searchengine.services;

import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.Status;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

public interface IndexingService {
    IndexingResponse startIndexing();

    IndexingResponse stopIndexing();

    IndexingResponse indexPage(String url);

    Status getSiteStatus(String url, String name);

    Status getSiteStatusByUrl(String url);

    int getSiteStatusCount(Status status);

    int getSiteId(String url, String name);

    String getSiteUrl(int sId);

    Status getSiteStatus(int sId);

    String getSiteLastErrorBySiteId(int sId);

    LocalDateTime getSiteStatusTimeBySiteId(int sId);

    HashMap<String, String> getSitesList();

    int getPageId(String path, int siteId);

    String getPagePath(int pageId, int siteId);

    String getPageContent(int pageId, int siteId);

    int getPageCountBySiteId(int siteId);

    List<Integer> getPageBySiteId(int siteId);

    int getLemmaId(String lemma, int siteId);

    int getLemmaCountBySiteId(int siteId);

    List<Integer> getLemmaIdBySiteId(int siteId);

    int getLemmaFrequency(int lemmaId);

    int getLemmaFreqByLemmaSiteId(String lemma, int siteId);

    int getLemmaCountByLemmaSiteId(String lemma, int siteId);

    List<Integer> getLemmaIdsByLemmasSiteId(List<String> lemmas, int sId);

    List<Integer> getIndexLemmaId(int pageId);

    float getIndexRank(int pageId, int lemmaId);

    List<Integer> getIndexPageIdByLemmaIdPageIds(int lemmaId, List<Integer> pageIds);

    float getIndexSumRank(int pageId, List<Integer> lemmaIds);

    void deleteSiteById(int id);

    void deleteLemmaById(int id);

    void deleteLemmaBySiteId(int siteId);

    void deletePageBySiteId(int siteId);

    void deleteIndexByPageId(int pageId);

    void deletePageById(int id);

    void insertSite(String url, String name, Status status);

    void insertPage(int siteId, String path, int code, String content);

    void insertLemma(int siteId, String lemma, int frequency);

    void insertIndex(int pageId, int lemmaId, float rank);

    void updateSiteCurrentStatusTime(int id);

    void updateSiteStatus(int id, Status status);

    void updateSiteLastError(int id, String lastError);

    void updateLemmaFrequency(int lemmaId, int frequency);

    void updateIndexRank(int pageId, int lemmaId, float rank);
}
