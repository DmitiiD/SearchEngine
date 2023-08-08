package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Index;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<Index, Long> {

    @Query(value = "SELECT * from `index` where page_id = :pageId", nativeQuery = true)
    List<Index> findAllContains(@Param("pageId") int pageId);

    @Query(value = "SELECT * from `index` where page_id = :pageId and lemma_id = :lemmaId", nativeQuery = true)
    List<Index> findAllContains(@Param("pageId") int pageId, @Param("lemmaId") int lemmaId);

    @Modifying
    @Query(value = "DELETE from `index` where page_id = :pageId", nativeQuery = true)
    @Transactional
    void deleteByPageId(@Param("pageId") int pageId);

    @Modifying
    @Query(value = "UPDATE `index` SET `rank` = :rank WHERE page_id = :pageId and lemma_id = :lemmaId", nativeQuery = true)
    @Transactional
    void updateRank(@Param("pageId") int pageId, @Param("lemmaId") int lemmaId, @Param("rank") float rank);

    @Modifying
    @Query(value = "INSERT INTO `index` (page_id, lemma_id, `rank`) VALUES(:pageId, :lemmaId, :rank)", nativeQuery = true)
    @Transactional
    void insert(@Param("pageId") int pageId, @Param("lemmaId") int lemmaId, @Param("rank") float rank);

}
