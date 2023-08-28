package searchengine.utils;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinParser extends RecursiveTask<Set<String>> {
    private static final int HANDSHAKE_TIMEOUT = 150;
    private String url;
    private Document doc;
    private String host, parentLink, protocol;
    private int siteId;
    private IndexingServiceImpl idxService;
    private IndexRepository indexRepository;
    private LemmaRepository lemmaRepository;
    private SiteRepository siteRepository;
    private PageRepository pageRepository;

    public ForkJoinParser(String url, int siteId, IndexingServiceImpl idxService,
                          IndexRepository indexRepository, LemmaRepository lemmaRepository,
                          SiteRepository siteRepository, PageRepository pageRepository) {
        float EPS = 0.00001f;

        for (Map.Entry<Integer, ForkJoinPool> entry : idxService.getIndexingPools().entrySet()) {
            if (entry.getValue().isTerminating()) {
                System.out.println("Shutdown flag has been found for " + url);
                return;
            }
        }

        this.siteId = siteId;
        this.idxService = idxService;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        doc = null;

        url = removeLastSymbol(url, '/').toLowerCase();
        this.url = url;

        URL homeURL;
        try {
            homeURL = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        protocol = homeURL.getProtocol().toLowerCase();
        host = homeURL.getHost().toLowerCase();
        int idx = protocol.length() + "://".length();
        parentLink = url.substring(idx);

        System.out.println(url);
        siteRepository.updateStatusTime(siteId, LocalDateTime.now());

        Response response = getPageResponse();
        if (response == null) {
            System.out.println("Error getPageResponse() for " + url);
        } else {
            String strTmp = null;
            for (Site site : siteRepository.findAllContains(siteId)) {
                strTmp = removeLastSymbol(site.getUrl(), '/');
                break;
            }
            String path = strTmp == null ? "" : url.substring(strTmp.length());
            if (path.isEmpty()) {
                path = "/";
            }
            doc = getHtmlCode(response, path);
            if (doc == null) {
                System.out.println("Error getHtmlCode() for " + url);
            } else {
                int pageId = pageRepository.findAllContains(path.toLowerCase(), this.siteId).stream().findFirst().map(Page::getId).orElse(-1);
                String text = doc.outerHtml();

                LemmaFinder lemmaFinderRus, lemmaFinderEng;
                try {
                    lemmaFinderRus = LemmaFinder.getInstanceRus();
                    lemmaFinderEng = LemmaFinder.getInstanceEng();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Map<String, Integer> lemmas = new HashMap<>(lemmaFinderRus.collectLemmas(text, Language.RUS));
                Map<String, Integer> lemmasEng = new HashMap<>(lemmaFinderEng.collectLemmas(text, Language.ENG));
                lemmas.putAll(lemmasEng);
                for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                    int lId = lemmaRepository.findAllContains(entry.getKey().toLowerCase(), this.siteId).stream().findFirst().map(Lemma::getId).orElse(-1);
                    if (lId == -1) {
                        String tmpStr = entry.getKey().toLowerCase();
                        lemmaRepository.insert(this.siteId, tmpStr, 1);
                        lId = lemmaRepository.findAllContains(tmpStr, this.siteId).stream().findFirst().map(Lemma::getId).orElse(-1);
                    } else {
                        int freq = lemmaRepository.findAllContainsByLemmaId(lId).stream().findFirst().map(Lemma::getFrequency).orElse(0);
                        ++freq;
                        lemmaRepository.updateFrequency(lId, freq);
                    }
                    float rank = indexRepository.findAllContains(pageId, lId).stream().findFirst().map(Index::getRank).orElse(0.0f);
                    if (rank <= EPS) {
                        indexRepository.insert(pageId, lId, entry.getValue());
                    } else {
                        rank += entry.getValue();
                        indexRepository.updateRank(pageId, lId, rank);
                    }
                }
            }
        }
    }

    public Document getHtmlCode(Response response, String path) {
        Document document;
        int code = response.statusCode();

        if (response.statusCode() < 400) {
            try {
                document = Jsoup.connect(url).userAgent(idxService.getOptions().getUserAgent())
                        .followRedirects(true)
                        .referrer(idxService.getOptions().getReferrer()).get();
                int pageId = pageRepository.findAllContains(path.toLowerCase(), siteId).stream().findFirst().map(Page::getId).orElse(-1);
                if (pageId == -1) {
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

    public Response getPageResponse() {
        try {
            return Jsoup.connect(url).userAgent(idxService.getOptions().getUserAgent())
                    .followRedirects(false)
                    .referrer(idxService.getOptions().getReferrer())
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
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

    @Override
    protected Set<String> compute() {
        for (Map.Entry<Integer, ForkJoinPool> entry : idxService.getIndexingPools().entrySet()) {
            if (entry.getValue().isTerminating()) {
                System.out.println("Shutdown flag has been found for " + url);
                return null;
            }
        }

        if (doc == null) {
            System.out.println("Document pointer is null for " + url);
            return null;
        }

        String stringWithLink;
        Elements aHrefs = doc.select("a[href]");
        Set<String> linksSet = new TreeSet<>();
        List<ForkJoinParser> subTasks = new LinkedList<>();

        try {
            Thread.sleep(HANDSHAKE_TIMEOUT);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        for (Element aHref : aHrefs) {
            stringWithLink = aHref.attr("href");
            stringWithLink = stringWithLink.trim();
            if (!stringWithLink.contains("/") || stringWithLink.contains("#")) {
                continue;
            }
            if (linksSet.contains(removeLastSymbol(stringWithLink, '/'))) {
                continue;
            }
            if (!stringWithLink.contains("://")) {
                stringWithLink = protocol + "://" + host + stringWithLink;
                if (linksSet.contains(removeLastSymbol(stringWithLink, '/'))) {
                    continue;
                }
            }
            if (!stringWithLink.contains(protocol + "://" + parentLink)) {
                continue;
            }

            linksSet.add(removeLastSymbol(stringWithLink, '/'));
            if (!removeLastSymbol(stringWithLink, '/').equals(url)) {
                ForkJoinParser fParser = new ForkJoinParser(removeLastSymbol(stringWithLink, '/'), siteId, idxService,
                        indexRepository, lemmaRepository, siteRepository, pageRepository);
                if (fParser.getDoc() != null) {
                    fParser.fork();
                    subTasks.add(fParser);
                }
            }
        }

        for (ForkJoinParser fJParser : subTasks) {
            linksSet.addAll(fJParser.join());
        }

        return linksSet;
    }

    public Document getDoc() {
        return doc;
    }
}

