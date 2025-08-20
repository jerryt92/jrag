drop table if exists chat_context_item;
create table chat_context_item
(
    context_id        varchar(64) not null,
    message_index     int         not null,
    chat_role         int         not null,
    content           text        not null,
    feedback          int,
    tool_call_process text,
    rag_infos         text,
    add_time          bigint      not null,
    primary key (context_id, message_index)
);

drop table if exists chat_context_record;
create table chat_context_record
(
    context_id  varchar(64) not null
        primary key,
    title       varchar(64) not null,
    user_id     varchar(64),
    update_time bigint      not null
);

drop table if exists embeddings_item;
create table embeddings_item
(
    hash               char(40)    not null
        primary key,
    embedding_model    varchar(32) not null,
    embedding_provider varchar(32) not null,
    text               text        not null,
    embedding          text        not null,
    text_chunk_id      char(40)    not null,
    description        varchar(128),
    create_time        bigint,
    update_time        bigint
);

drop table if exists file;
create table file
(
    id             int           not null
        primary key,
    full_file_name varchar(255)  not null,
    suffix         varchar(32),
    path           varchar(2048) not null,
    size           bigint        not null,
    md5            varchar(128)  not null,
    sha1           char(40)      not null,
    upload_time    bigint        not null
);

drop table if exists markdown_file;
create table markdown_file
(
    id             int           not null
        primary key,
    full_file_name varchar(255)  not null,
    relative_path  varchar(2048) not null,
    size           bigint        not null,
    md5            varchar(128)  not null,
    sha1           char(40)      not null
);

drop table if exists markdown_file_rel_text_chunk;
create table markdown_file_rel_text_chunk
(
    markdown_file_id int      not null,
    text_chunk_id    char(40) not null,
    primary key (markdown_file_id, text_chunk_id)
);

drop table if exists text_chunk;
create table text_chunk
(
    id          char(40) not null
        primary key,
    text_chunk  text,
    src_file_id int,
    description varchar(128),
    create_time bigint,
    update_time bigint
);

drop table if exists user;
create table user
(
    id            char(32)    not null
        primary key,
    username      varchar(64) not null,
    password_hash char(64)    not null,
    create_time   bigint
);

