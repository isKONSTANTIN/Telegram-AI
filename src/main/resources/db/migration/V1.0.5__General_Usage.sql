create table if not exists general_usage
(
    id                          serial primary key,
    chat_id                     bigint not null references chats(id),
    type                        text not null,
    cost                        numeric not null,
    date                        date not null
);

create index idx_general_usage on general_usage(chat_id, type, date);