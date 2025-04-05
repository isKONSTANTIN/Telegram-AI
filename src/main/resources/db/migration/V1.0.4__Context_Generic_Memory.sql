create table if not exists ai_context_memories
(
    id           bigserial primary key,
    ai_context   bigint not null references ai_contexts(id) on delete cascade,
    last_message bigint not null references ai_messages(id) on delete cascade,
    memory       text not null
);

create index idx_ai_context_memories on ai_context_memories(id, ai_context);
