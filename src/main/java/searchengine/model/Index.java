package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

//index — поисковый индекс
@Setter
@Getter
@Entity
@Table(name = "`index`")

public class Index {
    /*id INT NOT NULL AUTO_INCREMENT;
    page_id INT NOT NULL — идентификатор страницы;
    lemma_id INT NOT NULL — идентификатор леммы;
    rank FLOAT NOT NULL — количество данной леммы для данной страницы.*/

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @Column(name = "page_id", nullable = false)
    private int pageId;

    @Column(name = "lemma_id", nullable = false)
    private int lemmaId;

    @Column(name = "`rank`", nullable = false)
    private float rank;

}
