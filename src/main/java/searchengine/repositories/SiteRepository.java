package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Site;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SiteRepository extends JpaRepository<Site, Long> {

    @Query(value = "SELECT * from site", nativeQuery = true)
    List<Site> findAllSites();

    @Query(value = "SELECT * from site where lower(url) LIKE %:url% and lower(name) LIKE %:name%", nativeQuery = true)
    List<Site> findAllContains(@Param("url") String url, @Param("name") String name);

    @Query(value = "SELECT * from site where id = :siteId", nativeQuery = true)
    List<Site> findAllContains(@Param("siteId") int siteId);

    @Query(value = "SELECT * from site where url = :url", nativeQuery = true)
    List<Site> findAllContainsByUrl(@Param("url") String url);

    @Query(value = "SELECT count(*) from site where status = :status", nativeQuery = true)
    int getStatusCount(@Param("status") String status);

    @Modifying
    @Query(value = "DELETE from site where id = :id", nativeQuery = true)
    @Transactional
    void deleteById(@Param("id") int id);

    @Modifying
    @Query(value = "INSERT INTO site (url, name, status, status_time) VALUES(:url, :name, :status, :statusTime)", nativeQuery = true)
    @Transactional
    void insert(@Param("url") String url, @Param("name") String name, @Param("status") String status, @Param("statusTime") LocalDateTime statusTime);

    @Modifying
    @Query(value = "UPDATE site SET status_time = :statusTime WHERE id = :id", nativeQuery = true)
    @Transactional
    void updateStatusTime(@Param("id") int id, @Param("statusTime") LocalDateTime statusTime);

    @Modifying
    @Query(value = "UPDATE site SET status = :status WHERE id = :id", nativeQuery = true)
    @Transactional
    void updateStatus(@Param("id") int id, @Param("status") String status);

    @Modifying
    @Query(value = "UPDATE site SET last_error = :lastError WHERE id = :id", nativeQuery = true)
    @Transactional
    void updateLastError(@Param("id") int id, @Param("lastError") String lastError);

}
