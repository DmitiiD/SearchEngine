package searchengine.utils;

import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.services.IndexingServiceImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;

public class ForkJoinParser extends RecursiveTask<Set<String>> {
    private static final int HANDSHAKE_TIMEOUT = 150;
    private final float EPS = 0.00001f;
    private String url;
    private Document doc;
    private String host, parentLink, protocol;
    private int siteId;
    private IndexingServiceImpl idxService;

    public ForkJoinParser(String url, int siteId, IndexingServiceImpl idxService) {
        for (Map.Entry<Integer, ForkJoinPool> entry : idxService.getIdxingPools().entrySet()) {
            if (entry.getValue().isTerminating()) {
                System.out.println("Shutdown flag has been found for " + url);
                return;
            }
        }

        this.siteId = siteId;
        this.idxService = idxService;
        doc = null;

        url = removeLastSymbol(url, '/').toLowerCase();
        this.url = url;

        URL homeURL = null;
        try {
            homeURL = new URL(url);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        protocol = homeURL.getProtocol().toLowerCase();
        host = homeURL.getHost().toLowerCase();
        int idx = protocol.length() + "://".length();
        parentLink = url.substring(idx, url.length());

        System.out.println(url); // To console for showing process progress
        // В процессе обхода постоянно обновлять дату и время в поле status_time таблицы site на текущее:
        idxService.updateSiteCurrentStatusTime(siteId);

        Response response = getPageResponse();
        if (response == null) {
            System.out.println("Error getPageResponse() for " + url);
        } else {
            String strTmp = removeLastSymbol(idxService.getSiteUrl(siteId), '/');
            String path = url.substring(strTmp.length(), url.length());
            if (path.isEmpty()) {
                path = "/"; // адрес страницы от корня сайта (должен начинаться со слэша, например: /news/372189/)
            }
            doc = getHtmlCode(response, path);
            if (doc == null) {
                System.out.println("Error getHtmlCode() for " + url);
            } else { // lemma, _index tables fulling
                int pageId = idxService.getPageId(path, this.siteId);
                String text = doc.outerHtml();

                LemmaFinder lemmaFinder = null;
                try {
                    lemmaFinder = LemmaFinder.getInstance();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                for (Map.Entry<String, Integer> entry : lemmaFinder.collectLemmas(text).entrySet()) {
                    System.out.println(entry.getKey() + "     " + entry.getValue());
                    // lemma insert/update
                    int lId = idxService.getLemmaId(entry.getKey(), this.siteId);
                    if (lId == -1) {
                        idxService.insertLemma(this.siteId, entry.getKey(), 1);
                        lId = idxService.getLemmaId(entry.getKey(), this.siteId);
                    } else {
                        int freq = idxService.getLemmaFrequency(lId);
                        ++freq;
                        idxService.updateLemmaFrequency(lId, freq); //lemma table
                    }
                    // index insert/update
                    float rank = idxService.getIndexRank(pageId, lId);
                    if (rank <= EPS) {
                        idxService.insertIndex(pageId, lId, entry.getValue());
                    } else {
                        rank += entry.getValue();
                        idxService.updateIndexRank(pageId, lId, rank);
                    }
                } //for
            } // lemma, _index tables fulling
        }
    } // constructor

    public Document getHtmlCode(Response response, String path) {
        Document document;
        int code = response.statusCode();

        if (response.statusCode() < 400) {
            try {
                document = Jsoup.connect(url).userAgent(idxService.getOptions().getUserAgent())
                        .followRedirects(false)
                        .referrer(idxService.getOptions().getReferrer()).get();
            } catch (IOException e) {
                e.printStackTrace();
                document = null;
            }
        } else {
            document = null;
            // Обновить информацию в site таблице:
            idxService.updateSiteLastError(siteId, response.statusCode() + ' ' + response.statusMessage());
        }

        if (idxService.getPageId(path, siteId) == -1) { // page not found
            // Добавить информацию в page таблицу:
            idxService.insertPage(siteId, path, code, document.outerHtml());
        } else {
            // Страница path уже обрабатывалась
            document = null;
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
        for (Map.Entry<Integer, ForkJoinPool> entry : idxService.getIdxingPools().entrySet()) {
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
            if (!stringWithLink.contains(protocol + "://" + parentLink)) { // dismiss if not domestic host
                continue;
            }

            linksSet.add(removeLastSymbol(stringWithLink, '/'));
            if (!removeLastSymbol(stringWithLink, '/').equals(url)) {
                ForkJoinParser fParser = new ForkJoinParser(removeLastSymbol(stringWithLink, '/'), siteId, idxService);
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

