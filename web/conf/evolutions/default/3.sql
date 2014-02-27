# --- Sample dataset

# --- !Ups

insert into tag (id,name,added,enabled,tuid) values (1,'Unknown',now(),false,'0123456780');

# --- !Downs

delete from entry;
delete from tag;
