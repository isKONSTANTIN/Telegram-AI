alter table ai_models add column completion_tokens_cost numeric not null default 0;
alter table ai_models add column prompt_tokens_cost numeric not null default 0;