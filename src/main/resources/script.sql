create table IF NOT EXISTS page (id integer not null auto_increment, code integer not null, content MEDIUMTEXT not null, path TEXT not null, site_id integer not null, primary key (id));
select if (
    exists(
        select distinct index_name from information_schema.statistics 
        where table_schema = DATABASE() and 
        table_name = 'page' and index_name like 'idx_path'
    )
    ,'select ''index idx_path exists'' _______;'
    ,'create index idx_path on search_engine.page(path(767));') into @a;
PREPARE stmt1 FROM @a;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;
