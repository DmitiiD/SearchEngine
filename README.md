# Java-разработчик с нуля. Итоговый проект курса «Поисковый движок» с использованием Java Spring Framework 

Проект с подключенными библиотеками лемматизаторами.
Содержит несколько контроллеров, сервисов и репозитории
с подключением к бд MySQL.

## Настройки для запуска

Запустить проект возможно двумя способами
* либо с использованием SearchEngine-1.0-SNAPSHOT.jar 
>**Внимание!** SearchEngine-1.0-SNAPSHOT.jar и application.yaml должны быть в одном каталоге
* либо из среды разработки

### Зависимости

Для успешного скачивания и подключения к проекту зависимостей
из GitHub необходимо настроить Maven конфигурацию в файле `settings.xml`.

А зависимостях, в файле `pom.xml` добавлен репозиторий для получения
jar файлов:

```xml
<repositories>
  <repository>
    <id>skillbox-gitlab</id>
    <url>https://gitlab.skillbox.ru/api/v4/projects/263574/packages/maven</url>
  </repository>
</repositories>
```

Так как для доступа требуется авторизации по токену для получения данных из
публичного репозитория, для указания токена, найдите файл `settings.xml`.

* В Windows он располагается в директории `C:/Users/<Имя вашего пользователя>/.m2`
* В Linux директория `/home/<Имя вашего пользователя>/.m2`
* В macOs по адресу `/Users/<Имя вашего пользователя>/.m2`

>**Внимание!** Актуальный токен, строка которую надо вставить в тег `<value>...</value>`
[находится в документе по ссылке](https://docs.google.com/document/d/1rb0ysFBLQltgLTvmh-ebaZfJSI7VwlFlEYT9V5_aPjc/edit?usp=sharing). 

и добавьте внутри тега `settings` текст конфигурации:

```xml
<servers>
  <server>
    <id>skillbox-gitlab</id>
    <configuration>
      <httpHeaders>
        <property>
          <name>Private-Token</name>
          <value>token</value>
        </property>
      </httpHeaders>
    </configuration>
  </server>
</servers>
```

**Не забудьте поменять токен на актуальный!**

❗️Если файла нет, то создайте `settings.xml` и вставьте в него:

```xml
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
 https://maven.apache.org/xsd/settings-1.0.0.xsd">

  <servers>
    <server>
      <id>skillbox-gitlab</id>
      <configuration>
        <httpHeaders>
          <property>
            <name>Private-Token</name>
            <value>token</value>
          </property>
        </httpHeaders>
      </configuration>
    </server>
  </servers>

</settings>
```

ℹ️ [Пример готового settings.xml лежит](settings.xml) в корне этого проекта.


**Не забудьте поменять токен на актуальный!**

После этого, в проекте обновите зависимости (Ctrl+Shift+O / ⌘⇧I) или
принудительно обновите данные из pom.xml. 

Для этого вызовите контекстное
у файла `pom.xml` в дереве файла проектов **Project** и выберите пункт меню **Maven -> Reload Project**.


⁉️ Если после этого у вас остается ошибка:

```text
Could not transfer artifact org.apache.lucene.morphology:morph:pom:1.5
from/to gitlab-skillbox (https://gitlab.skillbox.ru/api/v4/projects/263574/packages/maven):
authentication failed for
https://gitlab.skillbox.ru/api/v4/projects/263574/packages/maven/russianmorphology/org/apache/
    lucene/morphology/morph/1.5/morph-1.5.pom,
status: 401 Unauthorized
```

Почистите кэш Maven. Самый надежный способ, удалить директорию:

- Windows `C:\Users\<user_name>\.m2\repository`
- macOs `/Users/<user_name>/.m2/repository`
- Linux `/home/<user_name>/.m2/repository`

где `<user_name>` - имя пользователя под которым вы работаете.

После этого снова попробуйте обновить данный из `pom.xml`

### Настройки подключения к БД

В проект добавлен драйвер для подключения к БД MySQL. Для запуска проекта,
убедитесь, что у вас запущен сервер MySQL 8.x.

🐳 Если у вас установлен докер, можете запустить контейнер с готовыми настройками
под проект командой:

```bash
docker run -d --name=springLemmaExample -e="MYSQL_ROOT_PASSWORD=Kimk7FjT" -e="MYSQL_DATABASE=lemma" -p3306:3306 mysql
```

Имя пользователя по-умолчанию `root`, настройки проекта в `src/resources/application.yml`
соответствуют настройкам контейнера, менять их не требуется.

❗️ Если у вас MacBook c процессором M1, необходимо использовать специальный
образ для ARM процессоров:

```bash
docker run -d --name=springLemmaExample -e="MYSQL_ROOT_PASSWORD=Kimk7FjT" -e="MYSQL_DATABASE=lemma" -p3306:3306 arm64v8/mysql:oracle
```

Если используете MySQL без докера, то создайте бд `search_engine` и замените логин и пароль
в файле конфигурации `src/resources/application.yml`:

```yaml
spring:
  datasource:
    username: root # имя пользователя
    password: Kimk7FjT # пароль пользователя
```

После этого, можете запустить проект. Если введены правильные данные,
проект успешно запуститься. Если запуск заканчивается ошибками, изучите текст
ошибок, внесите исправления и попробуйте заново.

## Структура проекта

Cлои проекта:

* **Presentation** - Контроллеры. Слой общается с пользователями: ожидает запросы по API. Отдает ответы.
* **Business** - Бизнес логика, содержится в классах Сервисах.
* **Data Access** - Классы Репозитории. Слой отвечает за хранение данных, подключение к БД, реализацию запросов.

![img.png](docs/arch.png)

Каждый слой занимается только своими задачами и работа одного слоя не должна перетекать в другой. Например, Контроллер
должен только получать данные от пользователя и вызывать нужный сервис, не более. Все расчеты и проверки должны быть в
классах сервисах.


* Контроллеры:
ApiController

* Бизнес логика:
IndexingServiceImpl
SearchServiceImpl
StatisticsServiceImpl

* Репозитоии:
IndexTableRepository
LemmaTableRepository
PageTableRepository
SiteTableRepository


Принципы работы поискового движка

В конфигурационном файле application.yaml перед запуском приложения задаются адреса сайтов, по которым движок должен осуществлять поиск.
Поисковый движок обходит все страницы заданных сайтов и индексирует их так, чтобы потом находить наиболее релевантные страницы по любому 
поисковому запросу пользователя. 
Пользователь присылает запрос через API движка. Запрос определённым образом трансформируется в список слов, переведённых в базовую форму. 
Например, для существительных — именительный падеж, единственное число. В индексе ищутся страницы, на которых встречаются все эти слова.
Результаты поиска ранжируются, сортируются и отдаются пользователю.

## Проверка API

Для проверки API данного проекта вы можете использовать web-сайт:

* [SearchEngine](http://localhost:8080/)
