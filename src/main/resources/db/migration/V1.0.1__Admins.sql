drop table if exists admins;

alter table chats_whitelist rename to chats;
alter table chats add column is_admin boolean not null default false;
