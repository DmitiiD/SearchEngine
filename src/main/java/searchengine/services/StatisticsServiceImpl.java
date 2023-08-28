package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Messages;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SitesList sites;
    private final IndexingServiceImpl indexingService;
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        HashMap<String, String> sitesUrlNameDb = new HashMap<>();
        for (searchengine.model.Site site : siteRepository.findAllSites()) {
            sitesUrlNameDb.put(site.getName(), site.getUrl());
        }

        for (Site site : sites.getSites()) {
            String name = site.getName().toLowerCase(Locale.ROOT);
            String url = site.getUrl().toLowerCase(Locale.ROOT);
            boolean isFind = false;
            for (Map.Entry<String, String> sitesDb : sitesUrlNameDb.entrySet()) {
                if ((name.compareTo(sitesDb.getKey()) == 0) && (url.compareTo(sitesDb.getValue()) == 0)) {
                    isFind = true;
                    break;
                }
            }
            if (!isFind) {
                siteRepository.insert(url.toLowerCase(), name.toLowerCase(), Status.FAILED.toString(), LocalDateTime.now());
            }
        }

        HashMap<String, String> sitesCfgDb = new HashMap<>();
        for (searchengine.model.Site site : siteRepository.findAllSites()) {
            sitesCfgDb.put(site.getName(), site.getUrl());
        }

        HashSet<Site> sitesActual = new HashSet<>();
        for (Map.Entry<String, String> sitesAct : sitesCfgDb.entrySet()) {
            Site st = new Site();
            st.setName(sitesAct.getKey());
            st.setUrl(sitesAct.getValue());
            sitesActual.add(st);
        }
        sites.setSites(sitesActual);

        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(true);

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        Set<Site> sitesList = sites.getSites();
        for (Site site : sitesList) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());

            int sId = siteRepository.findAllContains(site.getUrl().toLowerCase(), site.getName().toLowerCase()).stream().findFirst().map(searchengine.model.Site::getId).orElse(-1);
            int pages = pageRepository.calcPageCountBySiteId(sId);
            int lemmas = lemmaRepository.calcLemmaCountBySiteId(sId);
            item.setPages(pages);
            item.setLemmas(lemmas);
            Status stat = siteRepository.findAllContains(sId).stream().findFirst().map(searchengine.model.Site::getStatus).orElse(Status.NOTFOUND);
            item.setStatus(stat.toString());
            String err = siteRepository.findAllContains(sId).stream().findFirst().map(searchengine.model.Site::getLastError).orElse(null);
            if (err == null) {
                if (stat == Status.FAILED) {
                    err = Messages.noIndexedSite;
                } else {
                    err = Messages.noErrors;
                }
            }
            item.setError(err);

            LocalDateTime localDateTime = siteRepository.findAllContains(sId).stream().findFirst().map(searchengine.model.Site::getStatusTime).orElse(LocalDateTime.now());
            ZonedDateTime zdt = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
            long date = zdt.toInstant().toEpochMilli();
            item.setStatusTime(date);

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);
        return response;
    }
}
