package searchengine.services;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DataSearchItem;
import searchengine.dto.statistics.SearchResponse;
import searchengine.model.Status;
import searchengine.utils.LemmaFinder;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Getter

public class SearchServiceImpl implements SearchService {
    //For Search.
    //Исключать из полученного списка леммы, которые встречаются на слишком большом количестве страниц.
    //Процент от общего кол-ва страниц конкретного сайта:
    private final int PROCPAGES = 70;
    private final int SNIPPETSYMBOLSCOUNT = 50;
    private final float EPS = 0.00001f;
    private final IndexingServiceImpl indexingService;
    private final SitesList sites;

    public SearchResponse setSearchError(String err) {
        SearchResponse response = new SearchResponse();
        response.setResult(false);
        response.setError(err);
        response.setCount(0);
        response.setData(null);
        return response;
    }

    public String checkParamVerify(String query, String url) {
        if (query.isBlank()) {
            return "Задан пустой поисковый запрос";
        }
        if (url.isBlank() && indexingService.getSiteStatusCount(Status.INDEXED) == 0) {
            return "Нет проиндексированных сайтов";
        }
        if (!url.isBlank() && indexingService.getSiteStatusByUrl(url) != Status.INDEXED) {
            return "Выбранный сайт не проиндексирован";
        }
        return "";
    }

    public LinkedHashMap<String, Integer> sortedLemmaFreqMapForming(LemmaFinder lemmaFinder, String query, int sId) {
        //Разбивать поисковый запрос на отдельные слова и формировать из этих слов список уникальных лемм,
        //исключая междометия, союзы, предлоги и частицы.
        Map<String, Integer> lemmaFreqMap = new HashMap<>(lemmaFinder.collectLemmas(query));
        int cntPgs = 0; //количество страниц, на которых леммы встречаются хотя бы один раз
        Iterator<Map.Entry<String, Integer>> iterator01 = lemmaFreqMap.entrySet().iterator();
        while (iterator01.hasNext()) {
            Map.Entry<String, Integer> entry = iterator01.next();
            System.out.println(entry.getKey() + "     " + entry.getValue());
            int cntFreqLemma = indexingService.getLemmaFreqByLemmaSiteId(entry.getKey(), sId);
            if (cntFreqLemma != 0) {
                cntPgs += cntFreqLemma;
            } else { //в поисковом запросе найдена лемма, которой нет на сайте
                return null; //на странице должны быть все леммы из поискового запроса
            }
            lemmaFreqMap.put(entry.getKey(), cntFreqLemma);
        } //for Map
        if (cntPgs == 0) {
            return null;
        }

        //Исключать из полученного списка леммы, которые встречаются на слишком большом количестве страниц.
        Iterator<Map.Entry<String, Integer>> iterator02 = lemmaFreqMap.entrySet().iterator();
        while (iterator02.hasNext()) {
            Map.Entry<String, Integer> entry = iterator02.next();
            if ((lemmaFreqMap.size() > 1) && ((float) entry.getValue() / cntPgs * 100 >= PROCPAGES)) {
                iterator02.remove();
            }
        }

        //Сортировать леммы в порядке увеличения частоты встречаемости (по возрастанию значения поля frequency)
        // — от самых редких до самых частых.
        //Sorting hashmap by value
        LinkedHashMap<String, Integer> sortedLemmaFreqMap = lemmaFreqMap.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors
                        .toMap(Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new));

        return sortedLemmaFreqMap;
    }

    public LinkedHashMap<Integer, Float> sortedDescPageIdRankRelForming(int sId, LinkedHashMap<String, Integer> sortedLemmaFreqMap) {
        //По первой, самой редкой лемме из списка, находить все страницы, на которых она встречается.
        //Далее искать соответствия следующей леммы из этого списка страниц,
        //а затем повторять операцию по каждой следующей лемме.
        //Список страниц при этом на каждой итерации должен уменьшаться.
        List<Integer> pageIds = indexingService.getPageBySiteId(sId);
        for (Map.Entry<String, Integer> entry : sortedLemmaFreqMap.entrySet()) {
            System.out.println(entry.getKey() + "     " + entry.getValue());
            int lemmaId = indexingService.getLemmaId(entry.getKey(), sId);
            Iterator<Integer> itr = pageIds.iterator();
            while (itr.hasNext()) {
                int pageId = itr.next();
                if (indexingService.getIndexRank(pageId, lemmaId) <= EPS) {
                    itr.remove();
                }
            }
        }
        //pageIds все страницы с леммами поиска

        if (pageIds.isEmpty()) {
            return null;
        }

        //Для каждой страницы рассчитывать абсолютную релевантность —
        //сумму всех rank всех найденных на странице лемм (из таблицы index),
        //которая делится на максимальное значение этой абсолютной релевантности
        //для всех найденных страниц.
        LinkedHashMap<Integer, Float> pageIdRankRel = new LinkedHashMap<>();
        float maxRel = EPS;
        for (int pageId : pageIds) {
            float rankAbs = 0;
            for (Map.Entry<String, Integer> entry : sortedLemmaFreqMap.entrySet()) {
                System.out.println(entry.getKey() + "     " + entry.getValue());
                int lemmaId = indexingService.getLemmaId(entry.getKey(), sId);
                rankAbs += indexingService.getIndexRank(pageId, lemmaId);
            } //for map
            pageIdRankRel.put(pageId, rankAbs);
            if ((maxRel - rankAbs) < EPS) {
                maxRel = rankAbs;
            }
        } //for pageId
        for (Map.Entry<Integer, Float> entry : pageIdRankRel.entrySet()) {
            System.out.println(entry.getKey() + "     " + entry.getValue());
            pageIdRankRel.put(entry.getKey(), entry.getValue() / maxRel);
        }
        //pageIdRankRel - pageId,Относительная релевантность

        //Сортировать страницы по убыванию релевантности (от большей к меньшей).
        LinkedHashMap<Integer, Float> sortedDescPageIdRankRel = pageIdRankRel.entrySet()
                .stream()
                .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                .collect(Collectors
                        .toMap(Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new));

        return sortedDescPageIdRankRel;
    }

    public String getTitle(String content) {
        //get <title>...</title>
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

    public String getSnippet(LemmaFinder lemmaFinder, String content, String query) {
        //set snippet
        String noHtml = lemmaFinder.clearHtmlTags(content).toLowerCase(Locale.ROOT);
        String snippet = "";
        String[] words = lemmaFinder.arrayContainsRussianWords(query);
        for (String word : words) {
            if (lemmaFinder.isParticle(word)) {
                continue; //Это предлог
            }
            int pos = noHtml.indexOf(word);
            if (pos != -1) { //found
                int b = 0, e = 0;
                if (pos - SNIPPETSYMBOLSCOUNT <= 0) {
                    b = pos;
                } else {
                    b = pos - SNIPPETSYMBOLSCOUNT;
                }
                if (noHtml.length() - SNIPPETSYMBOLSCOUNT <= 0) {
                    e = pos + word.length();
                } else {
                    e = pos + SNIPPETSYMBOLSCOUNT - 1;
                }
                String subNoHtml = noHtml.substring(b, e);
                if (!snippet.isEmpty()) {
                    snippet = snippet.concat("\n").concat("\r");
                }
                int bSubNoHtml, eSubNoHtml;
                bSubNoHtml = subNoHtml.indexOf(word);
                eSubNoHtml = bSubNoHtml + word.length();
                if (bSubNoHtml == 0) {
                    snippet = snippet.concat("<b>").concat(word).concat("</b>")
                            .concat(subNoHtml.substring(eSubNoHtml, subNoHtml.length() - 1));
                } else {
                    snippet = snippet.concat(subNoHtml.substring(0, bSubNoHtml)).concat("<b>")
                            .concat(word).concat("</b>")
                            .concat(subNoHtml.substring(eSubNoHtml, subNoHtml.length() - 1));
                }
            } //found
        } //for

        return snippet;
    }

    @Override
    public SearchResponse search(String query, String url, Integer offset, Integer limit) {
        String resParamVerify = checkParamVerify(query, url);
        if (!resParamVerify.isEmpty()) {
            return setSearchError(resParamVerify);
        }

        LemmaFinder lemmaFinder = null;
        try {
            lemmaFinder = LemmaFinder.getInstance();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<DataSearchItem> data = new ArrayList<>();
        int count = 0;
        url = url.toLowerCase(Locale.ROOT);
        for (Site site : sites.getSites()) {
            if (!url.isBlank() && url.compareToIgnoreCase(site.getUrl()) != 0) {
                continue;
            }
            int sId = indexingService.getSiteId(site.getUrl(), site.getName());
            if (sId == -1 || indexingService.getSiteStatus(sId) != Status.INDEXED) { //no found
                continue;
            }

            LinkedHashMap<String, Integer> sortedLemmaFreqMap = sortedLemmaFreqMapForming(lemmaFinder, query, sId);
            if (sortedLemmaFreqMap == null) {
                continue;
            }

            LinkedHashMap<Integer, Float> sortedDescPageIdRankRel = sortedDescPageIdRankRelForming(sId, sortedLemmaFreqMap);
            if (sortedDescPageIdRankRel == null) {
                continue;
            }

            count += sortedDescPageIdRankRel.size();
            //Data forming
            for (Map.Entry<Integer, Float> entry : sortedDescPageIdRankRel.entrySet()) {
                System.out.println(entry.getKey() + "     " + entry.getValue());
                DataSearchItem item = new DataSearchItem();
                item.setSite(site.getUrl());
                item.setSiteName(site.getName());
                item.setRelevance(entry.getValue());
                item.setUri(indexingService.getPagePath(entry.getKey(), sId));

                String content = indexingService.getPageContent(entry.getKey(), sId);
                item.setTitle(getTitle(content));
                item.setSnippet(getSnippet(lemmaFinder, content, query));

                data.add(item);
            } //for data
        } //for site

        SearchResponse response = new SearchResponse();
        response.setResult(true);
        response.setError("");
        response.setCount(count);
        response.setData(data);
        return response;
    }

}
