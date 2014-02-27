# --- First database schema

# --- !Ups

create table tag (
  id                        bigserial primary key,
  name                      varchar(255) not null,
  added                		timestamp not null,
  enabled					boolean not null,
  tuid						varchar(10) not null UNIQUE
);

create table entry (
  id                        bigserial primary key,
  date               		timestamp not null,
  permitted              	boolean not null,
  tag_id               		bigint REFERENCES tag(id)
);

CREATE INDEX ix_entry_tag_1 ON entry(tag_id);
CREATE INDEX ix_tag_tuid_1 ON tag(tuid);
SELECT SETVAL('tag_id_seq', 100);

# --- !Downs


drop table if exists tag CASCADE;

drop table if exists entry CASCADE;

