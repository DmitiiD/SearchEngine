package searchengine.services;

import lombok.Getter;
import org.springframework.stereotype.Service;
import searchengine.config.Options;
import searchengine.config.SitesList;
import searchengine.dto.statistics.IndexingResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.utils.ForkJoinParser;
import searchengine.utils.LemmaFinder;

@Service
@Getter

public class IndexingServiceImpl implements IndexingService {
    private final int NOTFOUND = -1;
    private final float EPS = 0.00001f;
    private final SitesList sites;
    private final Options options;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private volatile HashMap<Integer, ForkJoinPool> indexingPools = new HashMap<>();
    private final LemmaFinder lemmaFinderRus, lemmaFinderEng;


    public IndexingServiceImpl(SitesList sites, Options options, SiteRepository siteRepository,
                               PageRepository pageRepository, LemmaRepository lemmaRepository,
                               IndexRepository indexRepository) throws IOException {
        this.sites = sites;
        this.options = options;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        lemmaFinderRus = LemmaFinder.getInstanceRus();
        lemmaFinderEng = LemmaFinder.getInstanceEng();
    }

    @Override
    public void updateIndexRank(int pageId, int lemmaId, float rank) {
        indexRepository.updateRank(pageId, lemmaId, rank);
    }

    @Override
    public void updateLemmaFrequency(int lemmaId, int frequency) {
        lemmaRepository.updateFrequency(lemmaId, frequency);
    }

    @Override
    public void updateSiteLastError(int id, String lastError) {
        siteRepository.updateLastError(id, lastError.toLowerCase());
    }

    @Override
    public void updateSiteStatus(int id, Status status) {
        siteRepository.updateStatus(id, status.toString());
    }

    @Override
    public void updateSiteCurrentStatusTime(int id) {
        siteRepository.updateStatusTime(id, LocalDateTime.now());
    }

    @Override
    public void insertLemma(int siteId, String lemma, int frequency) {
        lemmaRepository.insert(siteId, lemma.toLowerCase(), frequency);
    }

    @Override
    public void insertIndex(int pageId, int lemmaId, float rank) {
        indexRepository.insert(pageId, lemmaId, rank);
    }

    @Override
    public void insertPage(int siteId, String path, int code, String content) {
        pageRepository.insert(siteId, path.toLowerCase(), code, content);
    }

    @Override
    public void insertSite(String url, String name, Status status) {
        siteRepository.insert(url.toLowerCase(), name.toLowerCase(), status.toString(), LocalDateTime.now());
    }

    @Override
    public void deleteSiteById(int id) {
        siteRepository.deleteById(id);
    }

    @Override
    public void deleteLemmaById(int id) {
        lemmaRepository.deleteById(id);
    }

    @Override
    public void deleteLemmaBySiteId(int siteId) {
        lemmaRepository.deleteBySiteId(siteId);
    }

    @Override
    public void deletePageBySiteId(int siteId) {
        pageRepository.deleteBySiteId(siteId);
    }

    @Override
    public void deletePageById(int id) {
        pageRepository.deleteById(id);
    }

    @Override
    public void deleteIndexByPageId(int pageId) {
        indexRepository.deleteByPageId(pageId);
    }

    @Override
    public String getSiteUrl(int sId) {
        List<Site> siteList = siteRepository.findAllContains(sId);
        for (Site site : siteList) {
            return site.getUrl();
        }
        return "url not found";
    }

    @Override
    public Status getSiteStatus(int sId) {
        List<Site> siteList = siteRepository.findAllContains(sId);
        for (Site site : siteList) {
            return site.getStatus();
        }
        return Status.NOTFOUND;
    }

    @Override
    public int getSiteId(String url, String name) {
        List<Site> siteList = siteRepository.findAllContains(url.toLowerCase(), name.toLowerCase());
        for (Site site : siteList) {
            return site.getId();
        }
        return NOTFOUND;
    }

    @Override
    public Status getSiteStatus(String url, String name) {
        List<Site> siteList = siteRepository.findAllContains(url.toLowerCase(), name.toLowerCase());
        for (Site site : siteList) {
            return site.getStatus();
        }
        return Status.NOTFOUND;
    }

    @Override
    public Status getSiteStatusByUrl(String url) {
        List<Site> siteList = siteRepository.findAllContainsByUrl(url.toLowerCase());
        for (Site site : siteList) {
            return site.getStatus();
        }
        return Status.NOTFOUND;
    }

    @Override
    public int getSiteStatusCount(Status status) {
        return siteRepository.getStatusCount(status.toString());
    }

    @Override
    public String getSiteLastErrorBySiteId(int siteId) {
        List<Site> siteList = siteRepository.findAllContains(siteId);
        for (Site site : siteList) {
            return site.getLastError();
        }
        return "Not found";
    }

    @Override
    public LocalDateTime getSiteStatusTimeBySiteId(int siteId) {
        List<Site> siteList = siteRepository.findAllContains(siteId);
        for (Site site : siteList) {
            return site.getStatusTime();
        }
        return LocalDateTime.now();
    }

    @Override
    public HashMap<String, String> getSitesList() {
        HashMap<String, String> hM = new HashMap<>();
        List<Site> siteList = siteRepository.findAllSites();

        for (Site site : siteList) {
            hM.put(site.getName(), site.getUrl());
        }
        return hM;
    }

    @Override
    public String getPagePath(int pageId, int siteId) {
        return pageRepository.getPath(pageId, siteId);
    }

    @Override
    public String getPageContent(int pageId, int siteId) {
        return pageRepository.getContent(pageId, siteId);
    }

    @Override
    public int getPageId(String path, int siteId) {
        List<Page> pageList = pageRepository.findAllContains(path.toLowerCase(), siteId);
        for (Page page : pageList) {
            return page.getId();
        }
        return NOTFOUND;
    }

    @Override
    public int getPageCountBySiteId(int siteId) {
        return pageRepository.calcPageCountBySiteId(siteId);
    }

    @Override
    public List<Integer> getPageBySiteId(int siteId) {
        List<Page> pageList = pageRepository.findAllContains(siteId);
        List<Integer> pageIds = new ArrayList<>();
        for (Page page : pageList) {
            pageIds.add(page.getId());
        }
        return pageIds;
    }

    @Override
    public int getLemmaId(String lemma, int siteId) {
        List<Lemma> lemmaList = lemmaRepository.findAllContains(lemma.toLowerCase(), siteId);
        for (Lemma lemmaTable : lemmaList) {
            return lemmaTable.getId();
        }
        return NOTFOUND;
    }

    @Override
    public int getLemmaCountBySiteId(int siteId) {
        return lemmaRepository.calcLemmaCountBySiteId(siteId);
    }

    @Override
    public int getLemmaFrequency(int lemmaId) {
        List<Lemma> lemmaList = lemmaRepository.findAllContainsByLemmaId(lemmaId);
        for (Lemma lemma : lemmaList) {
            return lemma.getFrequency();
        }
        return NOTFOUND;
    }

    @Override
    public int getLemmaCountByLemmaSiteId(String lemma, int siteId) {
        return lemmaRepository.calcLemmaCountByLemmaSiteId(lemma.toLowerCase(Locale.ROOT), siteId);
    }

    @Override
    public int getLemmaFreqByLemmaSiteId(String lemma, int siteId) {
        if (getLemmaCountByLemmaSiteId(lemma.toLowerCase(Locale.ROOT), siteId) == 0) {
            return 0;
        }
        return lemmaRepository.getLemmaFrequency(lemma.toLowerCase(Locale.ROOT), siteId);
    }

    @Override
    public List<Integer> getLemmaIdBySiteId(int siteId) {
        List<Lemma> lemmaList = lemmaRepository.findAllContainsBySiteId(siteId);
        List<Integer> lemmaIds = new ArrayList<>();
        for (Lemma lemma : lemmaList) {
            lemmaIds.add(lemma.getId());
        }
        return lemmaIds;
    }

    @Override
    public List<Integer> getLemmaIdsByLemmasSiteId(List<String> lemmas, int sId) {
        return lemmaRepository.findAllContainsByLemmasSiteId(lemmas, sId);
    }

    @Override
    public List<Integer> getIndexLemmaId(int pageId) {
        List<Index> indexList = indexRepository.findAllContains(pageId);
        List<Integer> lemmaIds = new ArrayList<>();
        for (Index index : indexList) {
            lemmaIds.add(index.getLemmaId());
        }
        return lemmaIds;
    }

    @Override
    public float getIndexRank(int pageId, int lemmaId) {
        List<Index> indexList = indexRepository.findAllContains(pageId, lemmaId);
        for (Index index : indexList) {
            return index.getRank();
        }
        return 0;
    }

    @Override
    public List<Integer> getIndexPageIdByLemmaIdPageIds(int lemmaId, List<Integer> pageIds) {
        return indexRepository.findAllPages(lemmaId, pageIds);
    }

    @Override
    public float getIndexSumRank(int pageId, List<Integer> lemmaIds) {
        return indexRepository.getIndexSumRank(pageId, lemmaIds);
    }

    public void clearTables() {
        for (searchengine.config.Site site : sites.getSites()) {
            int sId = getSiteId(site.getUrl(), site.getName());
            if (sId != NOTFOUND) {
                deleteLemmaBySiteId(sId); //lemma clearing
                List<Integer> pageIds = getPageBySiteId(sId);
                for (int pageId : pageIds) {
                    deleteIndexByPageId(pageId); //index table clearing
                }
                deletePageBySiteId(sId); //page clearing
                deleteSiteById(sId); //site clearing
            }
        }
    }

    @Override
    public IndexingResponse startIndexing() {
        String[] errors = {
                "Индексация уже запущена",
                "Индексация не запущена",
                ""
        };

        // Если хоть у одного сайта статус INDEXING, то считаем, что полная УЖЕ индексация запущена
        if (getSiteStatusCount(Status.INDEXING) > 0) {
            IndexingResponse response = new IndexingResponse();
            response.setResult(false);
            response.setError(errors[0]); //"Индексация уже запущена"
            return response;
        }

        indexingPools.clear();

        // Удалить все имеющиеся данные по сайтам (записи из таблиц site,page,lemma,index):
        clearTables();

        // Создать в таблице site новые записи со статусом INDEXING:
        for (searchengine.config.Site site : sites.getSites()) {
            insertSite(site.getUrl(), site.getName(), Status.INDEXING);
        }

        // Обойти все страницы, начиная с главной, добавить их адреса, статусы и содержимое в базу данных в таблицу page:
        for (searchengine.config.Site site : sites.getSites()) {

            int sId = getSiteId(site.getUrl(), site.getName());
            if (sId != NOTFOUND) {
                // Обход каждого из сайтов, перечисленных в конфигурационном файле, должен запускаться в отдельном потоке !!!
                Thread thread =
                        new Thread(() -> {
                            // ForkJoinPool process. Waiting ...
                            ForkJoinParser parserFJ = new ForkJoinParser(site.getUrl(), sId, this);
                            ForkJoinPool pool = new ForkJoinPool();
                            getIndexingPools().put(sId, pool);
                            pool.invoke(parserFJ);
                            List<String> listFJ = new ArrayList<>(parserFJ.join());
                            //listFJ - pages list
                            // ForkJoinPool process: the end.
                            System.out.println("Amount indexed pages = " + listFJ.size() + ". Site name = " + site.getName());

                            // В процессе обхода постоянно обновлять дату и время в поле status_time таблицы site на текущее.
                            // По завершении обхода изменить статус (поле status) на INDEXED:
                            updateSiteCurrentStatusTime(sId);
                            updateSiteStatus(sId, Status.INDEXED);
                        });
                thread.start();
            }

        } // for

        IndexingResponse response = new IndexingResponse();
        response.setResult(true);
        response.setError(errors[2]); //""
        return response;
    }

    @Override
    public IndexingResponse stopIndexing() {
        String[] errors = {
                "Индексация уже запущена",
                "Индексация не запущена",
                "",
                "Ошибка остановки индексации"
        };

        // Если статус всех сайтов отличается от INDEXING, то считаем, что индексация НЕ запущена
        if (getSiteStatusCount(Status.INDEXING) == 0) {
            IndexingResponse response = new IndexingResponse();
            response.setResult(false);
            response.setError(errors[1]); //"Индексация не запущена"
            return response;
        }

        if (indexingPools.size() != getSiteStatusCount(Status.INDEXING)) {
            System.out.println("ForkJoinPool not synchronized with count of indexing sites. Waiting 60 seconds ...");
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            if (indexingPools.size() != getSiteStatusCount(Status.INDEXING)) {
                IndexingResponse response = new IndexingResponse();
                response.setResult(false);
                response.setError(errors[3]); //"Ошибка остановки индексации"
                return response;
            }
        }

        // Остановить все потоки и записать в базу данных для всех сайтов, страницы которых ещё не удалось обойти,
        // состояние FAILED и текст ошибки «Индексация остановлена пользователем»:
        Iterator<Map.Entry<Integer, ForkJoinPool>> iterator = indexingPools.entrySet().iterator();
        int i = indexingPools.size();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ForkJoinPool> entry = iterator.next();
            if (entry.getValue() != null && getSiteStatus(entry.getKey()) == Status.INDEXING) {
                entry.getValue().shutdown();

                try {
                    // Wait a while for existing tasks to terminate
                    if (!entry.getValue().awaitTermination(60, TimeUnit.SECONDS)) {
                        // Cancel currently executing tasks forcefully
                        entry.getValue().shutdownNow();
                        // Wait a while for tasks to respond to being cancelled
                        if (!entry.getValue().awaitTermination(60, TimeUnit.SECONDS)) {
                            System.err.println("Pool (siteId ==" + entry.getKey() + ") did not terminate");
                        } else {
                            updateSiteStatus(entry.getKey(), Status.FAILED);
                            updateSiteLastError(entry.getKey(), "Индексация остановлена пользователем");
                            iterator.remove();
                            --i;
                        }
                    } else {
                        updateSiteStatus(entry.getKey(), Status.FAILED);
                        updateSiteLastError(entry.getKey(), "Индексация остановлена пользователем");
                        iterator.remove();
                        --i;
                    }
                } catch (InterruptedException ex) {
                    // (Re-)Cancel if current thread also interrupted
                    entry.getValue().shutdownNow();
                    // Preserve interrupt status
                    Thread.currentThread().interrupt();
                }

                updateSiteCurrentStatusTime(entry.getKey());
            }
        } //while

        IndexingResponse response = new IndexingResponse();
        if (i == 0) { // all pools have been successfully terminated
            indexingPools.clear();
            response.setResult(true);
            response.setError(errors[2]); //""
        } else {
            response.setResult(false);
            response.setError(errors[3]); //"Ошибка остановки индексации"
        }

        return response;
    }

    @Override
    public IndexingResponse indexPage(String url) {
        String[] errors = {
                "Данная страница находится за пределами сайтов, указанных в конфигурационном файле",
                "",
                "Ошибка работы с леммами"
        };
        IndexingResponse response = new IndexingResponse();

        String siteFnd = "";
        int sId = NOTFOUND;
        for (searchengine.config.Site site : sites.getSites()) {
            if (url.contains(site.getUrl())) {
                siteFnd = site.getUrl();
                sId = getSiteId(site.getUrl(), site.getName());
                if (sId == NOTFOUND) {
                    //еще не индексировали сайт
                    insertSite(site.getUrl(), site.getName(), Status.INDEXED);
                    sId = getSiteId(site.getUrl(), site.getName());
                }
                break;
            }
        }
        // Is url from site list ?
        if (siteFnd.isEmpty()) {
            response.setResult(false);
            response.setError(errors[0]); //"Данная страница находится за пределами сайтов, указанных в конфигурационном файле"
            return response;
        }
        if (removeLastSymbol(url, '/').toLowerCase(Locale.ROOT).equals(removeLastSymbol(siteFnd, '/').toLowerCase(Locale.ROOT))) {
            response.setResult(false);
            response.setError(errors[0]); //"Данная страница находится за пределами сайтов, указанных в конфигурационном файле"
            return response;
        }

        // Get name of page:
        String pageUrl = url.toLowerCase(Locale.ROOT).replaceAll(siteFnd, "").trim();
        if (pageUrl.charAt(0) != '/') {
            pageUrl = "/".concat(pageUrl);
        }

        if (!indexPageTreatment(url, sId, pageUrl)) {
            //error
            response.setResult(false);
            response.setError(errors[2]); //"Ошибка работы с леммами"
            return response;
        }

        response.setResult(true);
        response.setError(errors[1]); //""
        return response;
    }

    public boolean indexPageTreatment(String url, int siteId, String pageUrl/*page.path*/) {
        Document doc;
        int pageId;

        pageId = getPageId(pageUrl, siteId);
        if (pageId != NOTFOUND) {
            //переданная страница уже была проиндексирована => перед её индексацией необходимо
            //удалить всю информацию о ней из таблиц page, lemma и index
            List<Integer> lemmaIds = getIndexLemmaId(pageId);
            deleteIndexByPageId(pageId); //index table clearing
            for (int lemmaId : lemmaIds) {
                int freq = getLemmaFrequency(lemmaId);
                if (freq == 1) {
                    // delete record from lemma table due to frequency can not be null
                    deleteLemmaById(lemmaId); //lemma table clearing
                } else {
                    // set frequency = freq - 1
                    updateLemmaFrequency(lemmaId, freq - 1); //lemma table
                }
            }
            deletePageById(pageId);
        } //page found

        url = removeLastSymbol(url, '/').toLowerCase();
        updateSiteCurrentStatusTime(siteId);

        Response response = getPageResponse(url);
        if (response == null) {
            System.out.println("Error getPageResponse() for " + url);
            return false;
        } else {
            doc = getHtmlCode(response, url, siteId, pageUrl);
            if (doc == null) {
                System.out.println("Error getHtmlCode() for " + url);
                return false;
            }
        }
        pageId = getPageId(pageUrl, siteId);
        String text = doc.outerHtml();

        Map<String, Integer> lemmas = new HashMap<>(lemmaFinderRus.collectLemmas(text, Language.RUS));
        Map<String, Integer> lemmasEng = new HashMap<>(lemmaFinderEng.collectLemmas(text, Language.ENG));
        lemmas.putAll(lemmasEng);

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            // lemma insert/update
            int lId = getLemmaId(entry.getKey(), siteId);
            if (lId == NOTFOUND) {
                insertLemma(siteId, entry.getKey(), 1);
                lId = getLemmaId(entry.getKey(), siteId);
            }
            // index insert/update
            float rank = getIndexRank(pageId, lId);
            if (rank <= EPS) {
                insertIndex(pageId, lId, entry.getValue());
            } else {
                rank += entry.getValue();
                updateIndexRank(pageId, lId, rank);
            }
        } //for

        return true;
    }

    public Document getHtmlCode(Response response, String url, int siteId, String path) {
        Document document;
        int code = response.statusCode();

        if (response.statusCode() < 400) {
            try {
                document = Jsoup.connect(url).userAgent(getOptions().getUserAgent())
                        .followRedirects(false)
                        .referrer(getOptions().getReferrer()).get();

                if (getPageId(path, siteId) == NOTFOUND) { // page not found
                    // Добавить информацию в page таблицу:
                    insertPage(siteId, path, code, document.outerHtml());
                } else {
                    // Страница path уже обрабатывалась
                    document = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
                document = null;
            }
        } else {
            document = null;
            // Обновить информацию в site таблице:
            updateSiteLastError(siteId, response.statusCode() + ' ' + response.statusMessage());
        }

        return document;
    }

    public String removeLastSymbol(String str, char charToRemove) {
        str = str.trim();
        if (!str.isEmpty()) {
            if (str.charAt(str.length() - 1) == charToRemove) {
                str = str.substring(0, str.length() - 1);
            }
        }
        return str;
    }

    public Response getPageResponse(String url) {
        try {
            return Jsoup.connect(url).userAgent(getOptions().getUserAgent())
                    .followRedirects(false)
                    .referrer(getOptions().getReferrer())
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

}
