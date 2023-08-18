package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Page;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface PageRepository extends JpaRepository<Page, Long> {

    @Query(value = "SELECT * from page where site_id = :siteId and lower(path) LIKE %:path%", nativeQuery = true)
    List<Page> findAllContains(@Param("path") String path, @Param("siteId") int siteId);

    @Query(value = "SELECT * from page where site_id = :siteId", nativeQuery = true)
    List<Page> findAllContains(@Param("siteId") int siteId);

    @Query(value = "SELECT path from page where site_id = :siteId and id = :pageId", nativeQuery = true)
    String getPath(@Param("pageId") int pageId, @Param("siteId") int siteId);

    @Query(value = "SELECT content from page where site_id = :siteId and id = :pageId", nativeQuery = true)
    String getContent(@Param("pageId") int pageId, @Param("siteId") int siteId);

    @Query(value = "SELECT count(*) from page where site_id = :siteId", nativeQuery = true)
    int calcPageCountBySiteId(@Param("siteId") int siteId);

    @Modifying
    @Query(value = "DELETE from page where site_id = :siteId", nativeQuery = true)
    @Transactional
    void deleteBySiteId(@Param("siteId") int siteId);

    @Modifying
    @Query(value = "DELETE from page where id = :id", nativeQuery = true)
    @Transactional
    void deleteById(@Param("id") int id);

    @Modifying
    @Query(value = "INSERT INTO page (site_id, path, code, content) VALUES(:siteId, :path, :code, :content)", nativeQuery = true)
    @Transactional
    void insert(@Param("siteId") int siteId, @Param("path") String path, @Param("code") int code, @Param("content") String content);

}
