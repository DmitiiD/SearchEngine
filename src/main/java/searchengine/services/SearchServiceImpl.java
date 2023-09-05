package searchengine.services;

import lombok.Getter;
import org.springframework.stereotype.Service;
import searchengine.config.Constants;
import searchengine.config.Messages;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DataSearchItem;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Language;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Status;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaFinder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Getter

public class SearchServiceImpl implements SearchService {
    private final IndexingServiceImpl indexingService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sites;
    private final LemmaFinder lemmaFinderRus, lemmaFinderEng;

    public SearchServiceImpl(IndexingServiceImpl indexingService, SitesList sites,
                             SiteRepository siteRepository, PageRepository pageRepository,
                             LemmaRepository lemmaRepository, IndexRepository indexRepository) throws IOException {
        this.indexingService = indexingService;
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.indexRepository = indexRepository;
        this.sites = sites;
        lemmaFinderRus = LemmaFinder.getInstanceRus();
        lemmaFinderEng = LemmaFinder.getInstanceEng();
    }

    public String checkParamVerify(String query, String url) {
        if (query.isBlank()) {
            return Messages.emptyQuery;
        }
        if (url.isBlank() && siteRepository.getStatusCount(Status.INDEXED.toString()) == 0) {
            return Messages.noIndexingSites;
        }
        Status status = Status.NOTFOUND;
        for (searchengine.model.Site site : siteRepository.findAllContainsByUrl(url.toLowerCase())) {
            status = site.getStatus();
            break;
        }
        if (!url.isBlank() && status != Status.INDEXED) {
            return Messages.noIndexedSite;
        }
        return "";
    }

    public LinkedHashMap<String, Integer> sortedLemmaFreqMapForming(String query, int sId) {
        Map<String, Integer> lemmaFreqMap = new HashMap<>(lemmaFinderRus.collectLemmas(query, Language.RUS));
        Map<String, Integer> lemmaFreqMapEng = new HashMap<>(lemmaFinderEng.collectLemmas(query, Language.ENG));
        lemmaFreqMap.putAll(lemmaFreqMapEng);
        int cntPgs = 0;
        Iterator<Map.Entry<String, Integer>> iterator01 = lemmaFreqMap.entrySet().iterator();
        while (iterator01.hasNext()) {
            Map.Entry<String, Integer> entry = iterator01.next();
            int cntFreqLemma;
            String strTmp = entry.getKey().toLowerCase(Locale.ROOT);
            if (lemmaRepository.calcLemmaCountByLemmaSiteId(strTmp, sId) == 0) {
                cntFreqLemma = 0;
            } else {
                cntFreqLemma = lemmaRepository.getLemmaFrequency(strTmp, sId);
            }
            if (cntFreqLemma != 0) {
                cntPgs += cntFreqLemma;
            } else {
                return null;
            }
            lemmaFreqMap.put(entry.getKey(), cntFreqLemma);
        }
        if (cntPgs == 0) {
            return null;
        }

        Iterator<Map.Entry<String, Integer>> iterator02 = lemmaFreqMap.entrySet().iterator();
        while (iterator02.hasNext()) {
            Map.Entry<String, Integer> entry = iterator02.next();
            if ((lemmaFreqMap.size() > 1) && ((float) entry.getValue() / cntPgs * 100 >= Constants.PROCPAGES)) {
                iterator02.remove();
            }
        }

        return lemmaFreqMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors
                        .toMap(Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new));
    }

    public LinkedHashMap<Integer, Float> sortedDescPageIdRankRelForming(int sId, LinkedHashMap<String, Integer> sortedLemmaFreqMap) {
        List<Integer> pageIds = new ArrayList<>();
        for (Page page : pageRepository.findAllContains(sId)) {
            pageIds.add(page.getId());
        }
        for (Map.Entry<String, Integer> entry : sortedLemmaFreqMap.entrySet()) {
            int lemmaId = -1;
            for (Lemma lemmaTable : lemmaRepository.findAllContains(entry.getKey().toLowerCase(), sId)) {
                lemmaId = lemmaTable.getId();
                break;
            }
            pageIds = indexRepository.findAllPages(lemmaId, pageIds);
            if (pageIds.isEmpty()) {
                return null;
            }
        }

        List<String> lemmas = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sortedLemmaFreqMap.entrySet()) {
            lemmas.add(entry.getKey());
        }
        List<Integer> lemmaIds = lemmaRepository.findAllContainsByLemmasSiteId(lemmas, sId);
        if (lemmaIds.isEmpty()) {
            return null;
        }
        LinkedHashMap<Integer, Float> pageIdRankRel = new LinkedHashMap<>();
        float maxRel = Constants.EPS;
        for (int pageId : pageIds) {
            float rankAbs = indexRepository.getIndexSumRank(pageId, lemmaIds);
            pageIdRankRel.put(pageId, rankAbs);
            if ((maxRel - rankAbs) < Constants.EPS) {
                maxRel = rankAbs;
            }
        }
        for (Map.Entry<Integer, Float> entry : pageIdRankRel.entrySet()) {
            pageIdRankRel.put(entry.getKey(), entry.getValue() / maxRel);
        }

        return pageIdRankRel.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors
                        .toMap(Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new));
    }

    public String getTitle(String content) {
        String title = "title no found";
        String tmp = content.toLowerCase(Locale.ROOT);
        if (!content.isBlank()) {
            int begin = tmp.indexOf("<title>");
            if (begin != -1) {
                int end = tmp.indexOf("</title>");
                if (end != -1 && begin < end) {
                    title = content.substring(begin + "<title>".length(), end);
                }
            }
        }
        return title;
    }

    public String getSnippet(String content, String query) {
        //set snippet
        String noHtml = lemmaFinderRus.clearHtmlTags(content);
        String snippet = "";
        String[] words = lemmaFinderRus.arrayContainsAnyWords(query);
        for (String word : words) {
            if (word.replaceAll("([^а-я\\s])", " ").trim().isEmpty()) {
                if (lemmaFinderEng.isParticle(word)) {
                    continue;
                }
            } else {
                if (lemmaFinderRus.isParticle(word)) {
                    continue;
                }
            }
            int pos = noHtml.indexOf(word);
            if (pos != -1) {
                int b, e;
                if (pos - Constants.SNIPPETSYMBOLSCOUNT <= 0) {
                    b = pos;
                } else {
                    b = pos - Constants.SNIPPETSYMBOLSCOUNT;
                }
                if (noHtml.length() - Constants.SNIPPETSYMBOLSCOUNT <= 0) {
                    e = pos + word.length();
                } else {
                    e = pos + Constants.SNIPPETSYMBOLSCOUNT - 1;
                }
                String subNoHtml = noHtml.substring(b, e);
                if (!snippet.isEmpty()) {
                    snippet = snippet.concat("\n").concat("\r");
                }
                int bSubNoHtml, eSubNoHtml;
                bSubNoHtml = subNoHtml.indexOf(word);
                eSubNoHtml = bSubNoHtml + word.length();
                String str = subNoHtml.substring(eSubNoHtml, subNoHtml.length() - 1);
                if (bSubNoHtml == 0) {
                    snippet = snippet.concat("<b>").concat(word).concat("</b>").concat(str);
                } else {
                    snippet = snippet.concat(subNoHtml.substring(0, bSubNoHtml)).concat("<b>")
                            .concat(word).concat("</b>").concat(str);
                }
            }
        }

        return snippet;
    }

    @Override
    public SearchResponse search(String query, String url, Integer offset, Integer limit) {
        String resParamVerify = checkParamVerify(query, url);
        if (!resParamVerify.isEmpty()) {
            return setSearchResponse(false, resParamVerify, 0, null, offset, limit);
        }

        List<DataSearchItem> data = new ArrayList<>();
        int count = 0;
        url = url.toLowerCase(Locale.ROOT);
        for (Site site : sites.getSites()) {
            if (!url.isBlank() && url.compareToIgnoreCase(site.getUrl()) != 0) {
                continue;
            }
            int sId = siteRepository.findAllContains(site.getUrl().toLowerCase(), site.getName().toLowerCase()).stream().findFirst().map(searchengine.model.Site::getId).orElse(-1);
            Status status = siteRepository.findAllContains(sId).stream().findFirst().map(searchengine.model.Site::getStatus).orElse(Status.NOTFOUND);
            if (sId == -1 || status != Status.INDEXED) {
                continue;
            }

            LinkedHashMap<String, Integer> sortedLemmaFreqMap = sortedLemmaFreqMapForming(query, sId);
            if (sortedLemmaFreqMap == null) {
                continue;
            }

            LinkedHashMap<Integer, Float> sortedDescPageIdRankRel = sortedDescPageIdRankRelForming(sId, sortedLemmaFreqMap);
            if (sortedDescPageIdRankRel == null) {
                continue;
            }

            count += sortedDescPageIdRankRel.size();
            String content, snippet;
            for (Map.Entry<Integer, Float> entry : sortedDescPageIdRankRel.entrySet()) {
                content = pageRepository.getContent(entry.getKey(), sId);
                snippet = getSnippet(content, query);
                if (snippet.isEmpty()) {
                    continue;
                }
                DataSearchItem item = new DataSearchItem();
                item.setSite(site.getUrl());
                item.setSiteName(site.getName());
                item.setRelevance(entry.getValue());
                item.setUri(pageRepository.getPath(entry.getKey(), sId));
                item.setTitle(getTitle(content));
                item.setSnippet(snippet);

                data.add(item);
            }
        }

        return setSearchResponse(true, "", count, data, offset, limit);
    }

    public SearchResponse setSearchResponse(boolean bRes, String err, int count, List<DataSearchItem> data, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        response.setResult(bRes);
        response.setError(err);
        response.setCount(count);
        response.setData(data);
        response.setOffset(offset);
        response.setLimit(limit);
        return response;
    }

}
