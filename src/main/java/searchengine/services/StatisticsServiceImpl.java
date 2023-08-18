package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Status;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final SitesList sites;
    private final IndexingServiceImpl indexingService;

    @Override
    public StatisticsResponse getStatistics() {
        // Db actualization sites from config:
        HashMap<String, String> sitesUrlNameDb = new HashMap<>(indexingService.getSitesList());
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
                indexingService.insertSite(url, name, Status.FAILED);
            }
        }// for

        HashMap<String, String> sitesCfgDb = new HashMap<>(indexingService.getSitesList()); //name, url
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

            int sId = indexingService.getSiteId(site.getUrl(), site.getName());
            int pages = indexingService.getPageCountBySiteId(sId); //random.nextInt(1_000);
            int lemmas = indexingService.getLemmaCountBySiteId(sId); //pages * random.nextInt(1_000);
            item.setPages(pages);
            item.setLemmas(lemmas);
            Status stat = indexingService.getSiteStatus(sId);
            item.setStatus(stat.toString()); //statuses[i % 3]);
            String err = indexingService.getSiteLastErrorBySiteId(sId);
            if (err == null) {
                if (stat == Status.FAILED) {
                    err = "Страница не проиндексирована";
                } else {
                    err = "Без ошибок";
                }
            }
            item.setError(err);

            LocalDateTime localDateTime = indexingService.getSiteStatusTimeBySiteId(sId);
            ZonedDateTime zdt = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
            long date = zdt.toInstant().toEpochMilli();
            item.setStatusTime(date); //System.currentTimeMillis() - (random.nextInt(10_000)));

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
