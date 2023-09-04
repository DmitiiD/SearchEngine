package searchengine.services;

import lombok.Getter;
import org.springframework.stereotype.Service;
import searchengine.config.Messages;
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

    public void clearTables() {
        for (searchengine.config.Site site : sites.getSites()) {
            int sId = siteRepository.findAllContains(site.getUrl().toLowerCase(), site.getName().toLowerCase()).stream().findFirst().map(Site::getId).orElse(NOTFOUND);
            if (sId == NOTFOUND) {
                continue;
            }
            lemmaRepository.deleteBySiteId(sId);
            for (Page page : pageRepository.findAllContains(sId)) {
                indexRepository.deleteByPageId(page.getId());
            }
            pageRepository.deleteBySiteId(sId);
            siteRepository.deleteById(sId);
        }
    }

    public IndexingResponse setIndexingResult(boolean flag, String err) {
        IndexingResponse response = new IndexingResponse();
        response.setResult(flag);
        response.setError(err);
        return response;
    }

    public void setSuccessfulIndexStoppingStatus(int siteId) {
        siteRepository.updateStatus(siteId, Status.FAILED.toString());
        siteRepository.updateLastError(siteId, Messages.indexingStopped);
    }

    @Override
    public IndexingResponse startIndexing() {
        if (siteRepository.getStatusCount(Status.INDEXING.toString()) > 0) {
            return setIndexingResult(false, Messages.indexingInProgress);
        }

        indexingPools.clear();
        clearTables();

        for (searchengine.config.Site site : sites.getSites()) {
            siteRepository.insert(site.getUrl().toLowerCase(), site.getName().toLowerCase(), Status.INDEXING.toString(), LocalDateTime.now());
        }

        for (searchengine.config.Site site : sites.getSites()) {
            int sId = siteRepository.findAllContains(site.getUrl().toLowerCase(), site.getName().toLowerCase()).stream().findFirst().map(Site::getId).orElse(NOTFOUND);
            if (sId == NOTFOUND) {
                continue;
            }
            Thread thread =
                    new Thread(() -> {
                        ForkJoinParser parserFJ = new ForkJoinParser(site.getUrl(), sId, this,
                                indexRepository, lemmaRepository, siteRepository, pageRepository);
                        ForkJoinPool pool = new ForkJoinPool();
                        getIndexingPools().put(sId, pool);
                        pool.invoke(parserFJ);
                        List<String> listFJ = new ArrayList<>(parserFJ.join());
                        System.out.println("Amount indexed pages = " + listFJ.size() + ". Site name = " + site.getName());

                        siteRepository.updateStatusTime(sId, LocalDateTime.now());
                        siteRepository.updateStatus(sId, Status.INDEXED.toString());
                    });
            thread.start();
        }

        return setIndexingResult(true, "");
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (siteRepository.getStatusCount(Status.INDEXING.toString()) == 0) {
            return setIndexingResult(false, Messages.indexingNotInProgress);
        }

        if (indexingPools.size() != siteRepository.getStatusCount(Status.INDEXING.toString())) {
            System.out.println("ForkJoinPool not synchronized with count of indexing sites. Waiting 60 seconds ...");
            try {
                Thread.sleep(60000);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            if (indexingPools.size() != siteRepository.getStatusCount(Status.INDEXING.toString())) {
                return setIndexingResult(false, Messages.indexingStoppingError);
            }
        }

        Iterator<Map.Entry<Integer, ForkJoinPool>> iterator = indexingPools.entrySet().iterator();
        int i = indexingPools.size();
        while (iterator.hasNext()) {
            Map.Entry<Integer, ForkJoinPool> entry = iterator.next();
            Status status = Status.NOTFOUND;
            for (Site site : siteRepository.findAllContains(entry.getKey())) {
                status = site.getStatus();
                break;
            }
            if (!(entry.getValue() != null && status == Status.INDEXING)) {
                continue;
            }
            entry.getValue().shutdown();
            try {
                if (!entry.getValue().awaitTermination(60, TimeUnit.SECONDS)) {
                    entry.getValue().shutdownNow();
                    if (!entry.getValue().awaitTermination(60, TimeUnit.SECONDS)) {
                        System.err.println("Pool (siteId ==" + entry.getKey() + ") did not terminate");
                    } else {
                        setSuccessfulIndexStoppingStatus(entry.getKey());
                        iterator.remove();
                        --i;
                    }
                } else {
                    setSuccessfulIndexStoppingStatus(entry.getKey());
                    iterator.remove();
                    --i;
                }
            } catch (InterruptedException ex) {
                entry.getValue().shutdownNow();
                Thread.currentThread().interrupt();
            }
            siteRepository.updateStatusTime(entry.getKey(), LocalDateTime.now());
        }

        if (i == 0) {
            indexingPools.clear();
            return setIndexingResult(true, "");
        } else {
            return setIndexingResult(false, Messages.indexingStoppingError);
        }
    }

    @Override
    public IndexingResponse indexPage(String url) {
        String siteFnd = "";
        int sId = NOTFOUND;
        for (searchengine.config.Site site : sites.getSites()) {
            if (!url.contains(site.getUrl())) {
                continue;
            }
            siteFnd = site.getUrl();
            sId = siteRepository.findAllContains(site.getUrl().toLowerCase(), site.getName().toLowerCase()).stream().findFirst().map(Site::getId).orElse(NOTFOUND);
            if (sId == NOTFOUND) {
                siteRepository.insert(site.getUrl().toLowerCase(), site.getName().toLowerCase(), Status.INDEXED.toString(), LocalDateTime.now());
                sId = siteRepository.findAllContains(site.getUrl().toLowerCase(), site.getName().toLowerCase()).stream().findFirst().map(Site::getId).orElse(NOTFOUND);
            }
            break;
        }
        if (siteFnd.isEmpty() ||
                removeLastSymbol(url, '/').toLowerCase(Locale.ROOT).equals(removeLastSymbol(siteFnd, '/').toLowerCase(Locale.ROOT))) {
            return setIndexingResult(false, Messages.indexingPageOutsideSite);
        }

        String pageUrl = url.toLowerCase(Locale.ROOT).replaceAll(siteFnd, "").trim();
        if (pageUrl.charAt(0) != '/') {
            pageUrl = "/".concat(pageUrl);
        }

        if (!indexPageTreatment(url, sId, pageUrl)) {
            return setIndexingResult(false, Messages.workingLemmaError);
        }

        return setIndexingResult(true, "");
    }

    public void deletePage(int pageId) {
        List<Integer> lemmaIds = new ArrayList<>();
        for (Index index : indexRepository.findAllContains(pageId)) {
            lemmaIds.add(index.getLemmaId());
        }
        indexRepository.deleteByPageId(pageId);
        for (int lemmaId : lemmaIds) {
            int freq = NOTFOUND;
            for (Lemma lemma : lemmaRepository.findAllContainsByLemmaId(lemmaId)) {
                freq = lemma.getFrequency();
                break;
            }
            if (freq == 1) {
                lemmaRepository.deleteById(lemmaId);
            } else {
                if (freq != NOTFOUND) {
                    lemmaRepository.updateFrequency(lemmaId, freq - 1);
                }
            }
        }
        pageRepository.deleteById(pageId);
    }

    public boolean indexPageTreatment(String url, int siteId, String pageUrl) {
        Document doc;
        int pageId = pageRepository.findAllContains(pageUrl.toLowerCase(), siteId).stream().findFirst().map(Page::getId).orElse(NOTFOUND);

        if (pageId != NOTFOUND) {
            deletePage(pageId);
        }
        url = removeLastSymbol(url, '/').toLowerCase();

        siteRepository.updateStatusTime(siteId, LocalDateTime.now());
        Response response = getPageResponse(url);
        if (response == null) {
            System.out.println("Error getPageResponse() for " + url);
            return false;
        }
        doc = getHtmlCode(response, url, siteId, pageUrl);
        if (doc == null) {
            System.out.println("Error getHtmlCode() for " + url);
            return false;
        }

        pageId = pageRepository.findAllContains(pageUrl.toLowerCase(), siteId).stream().findFirst().map(Page::getId).orElse(NOTFOUND);
        String text = doc.outerHtml();

        Map<String, Integer> lemmas = new HashMap<>(lemmaFinderRus.collectLemmas(text, Language.RUS));
        Map<String, Integer> lemmasEng = new HashMap<>(lemmaFinderEng.collectLemmas(text, Language.ENG));
        lemmas.putAll(lemmasEng);

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            int lId = lemmaRepository.findAllContains(entry.getKey().toLowerCase(), siteId).stream().findFirst().map(Lemma::getId).orElse(NOTFOUND);
            if (lId == NOTFOUND) {
                lemmaRepository.insert(siteId, entry.getKey().toLowerCase(), 1);
                lId = lemmaRepository.findAllContains(entry.getKey().toLowerCase(), siteId).stream().findFirst().map(Lemma::getId).orElse(NOTFOUND);
            }
            float rank = indexRepository.findAllContains(pageId, lId).stream().findFirst().map(Index::getRank).orElse(0.0f);
            if (rank <= EPS) {
                indexRepository.insert(pageId, lId, entry.getValue());
            } else {
                rank += entry.getValue();
                indexRepository.updateRank(pageId, lId, rank);
            }
        }

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
                int pageId = pageRepository.findAllContains(path.toLowerCase(), siteId).stream().findFirst().map(Page::getId).orElse(NOTFOUND);
                if (pageId == NOTFOUND) {
                    pageRepository.insert(siteId, path.toLowerCase(), code, document.outerHtml());
                } else {
                    document = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
                document = null;
            }
        } else {
            document = null;
            siteRepository.updateLastError(siteId, response.statusCode() + ' ' + response.statusMessage());
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
