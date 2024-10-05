create table if not exists chats_whitelist
(
    id                                 bigint not null unique,
    description                        text,
    enabled                            boolean not null
);

create index idx_chats_whitelist on chats_whitelist(id);

create table if not exists admins
(
    id                          bigint not null references chats_whitelist(id),
    enabled                     boolean not null
);

create index idx_admins on admins(id);

create table if not exists ai_models
(
    id              serial primary key,
    server          smallint not null,
    name            text not null,
    model           text not null,
    included_tools  text[] not null,
    enabled         boolean not null
);

create index idx_ai_models on ai_models(id);

create table if not exists ai_models_usage
(
    id                          serial primary key,
    model                       integer not null references ai_models(id),
    chat_id                     bigint not null references chats_whitelist(id),
    completion_tokens_used      integer not null,
    prompt_tokens_used          integer not null,
    date                        date not null
);

create index idx_ai_models_usage on ai_models_usage(chat_id, model, date);

create table if not exists ai_presets
(
    id                  bigserial primary key,
    chat_id             bigint not null references chats_whitelist(id),
    model               integer not null references ai_models(id),
    temperature         real not null,
    top_p               real not null,
    frequency_penalty   real not null,
    presence_penalty    real not null,
    max_tokens          integer not null,
    text                text not null,
    tag                 text not null
);

create index idx_ai_presets on ai_presets(chat_id, tag);

create table if not exists ai_contexts
(
    id                          bigserial primary key,
    chat_id                     bigint not null references chats_whitelist(id),
    last_preset_id              bigint not null references ai_presets(id),
    created_at                  timestamp with time zone not null
);

create index idx_ai_contexts on ai_contexts(id, chat_id);

create table if not exists ai_messages
(
    id          bigserial primary key,
    message_id  bigint not null,
    chat_id     bigint not null references chats_whitelist(id),
    ai_context  bigint not null references ai_contexts(id),
    role        text not null,
    content     json not null
);

create index idx_ai_messages on ai_messages(id, ai_context, chat_id);

create table if not exists chats_preferences
(
    chat_id                     bigint not null unique references chats_whitelist(id),
    default_preset              bigint not null references ai_presets(id),
    contexts_mode               integer not null default 0
);

create index idx_chats_preferences on chats_preferences(chat_id);
