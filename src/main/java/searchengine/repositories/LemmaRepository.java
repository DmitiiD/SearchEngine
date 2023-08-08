package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import searchengine.model.Lemma;

import javax.transaction.Transactional;
import java.util.List;

@Repository
public interface LemmaRepository extends JpaRepository<Lemma, Long> {

    @Query(value = "SELECT frequency from lemma where lemma = :lemma and site_id = :siteId", nativeQuery = true)
    int getLemmaFrequency(@Param("lemma") String lemma, @Param("siteId") int siteId);

    @Query(value = "SELECT * from lemma where id = :lemmaId", nativeQuery = true)
    List<Lemma> findAllContainsByLemmaId(@Param("lemmaId") int lemmaId);

    @Query(value = "SELECT * from lemma where site_id = :siteId", nativeQuery = true)
    List<Lemma> findAllContainsBySiteId(@Param("siteId") int siteId);

    //    @Query(value = "SELECT * from lemma where site_id = :siteId and lower(lemma) LIKE %:lemma%", nativeQuery = true)
    @Query(value = "SELECT * from lemma where site_id = :siteId and lower(lemma) = :lemma", nativeQuery = true)
    List<Lemma> findAllContains(@Param("lemma") String lemma, @Param("siteId") int siteId);

    @Query(value = "SELECT count(*) from lemma where site_id = :siteId", nativeQuery = true)
    int calcLemmaCountBySiteId(@Param("siteId") int siteId);

    @Query(value = "SELECT count(*) from lemma where lemma = :lemma and site_id = :siteId", nativeQuery = true)
    int calcLemmaCountByLemmaSiteId(@Param("lemma") String lemma, @Param("siteId") int siteId);

    @Modifying
    @Query(value = "DELETE from lemma where id = :Id", nativeQuery = true)
    @Transactional
    void deleteById(@Param("Id") int Id);

    @Modifying
    @Query(value = "DELETE from lemma where site_id = :siteId", nativeQuery = true)
    @Transactional
    void deleteBySiteId(@Param("siteId") int siteId);

    @Modifying
    @Query(value = "UPDATE lemma SET frequency = :frequency WHERE id = :lemmaId", nativeQuery = true)
    @Transactional
    void updateFrequency(@Param("lemmaId") int lemmaId, @Param("frequency") int frequency);

    @Modifying
    @Query(value = "INSERT INTO lemma (site_id, lemma, frequency) VALUES(:siteId, :lemma, :frequency)", nativeQuery = true)
    @Transactional
    void insert(@Param("siteId") int siteId, @Param("lemma") String lemma, @Param("frequency") int frequency);

}
