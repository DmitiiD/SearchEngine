package searchengine.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

//page — проиндексированные страницы сайта
@Setter
@Getter
@Entity
@Table(name = "page")
public class Page {
    /*id INT NOT NULL AUTO_INCREMENT;
    site_id INT NOT NULL — ID веб-сайта из таблицы site;
    path TEXT NOT NULL — адрес страницы от корня сайта (должен начинаться со слэша, например: /news/372189/);
    code INT NOT NULL — код HTTP-ответа, полученный при запросе страницы (например, 200, 404, 500 или другие);
    content MEDIUMTEXT NOT NULL — контент страницы (HTML-код).

    По полю path должен быть установлен индекс*/

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "path", nullable = false, columnDefinition = "TEXT")
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    @Column(name = "content", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String content;

}
