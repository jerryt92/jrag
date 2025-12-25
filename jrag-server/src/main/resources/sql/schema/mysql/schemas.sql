DROP DATABASE IF EXISTS jrag;
CREATE DATABASE IF NOT EXISTS jrag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE jrag;

DROP TABLE IF EXISTS ai_properties;
create table ai_properties
(
    property_name  varchar(128) not null
        primary key,
    property_value varchar(128) null,
    description    varchar(512) null
);

DROP TABLE IF EXISTS chat_context_item;
create table chat_context_item
(
    context_id        varchar(64) not null,
    message_index     int         not null,
    chat_role         int         not null comment '0-system, 1-user, 2-assistant',
    content           text        not null,
    feedback          int         null comment '1-good, 2-bad',
    tool_call_process text        null comment '工具调用过程',
    rag_infos         text        null comment 'RAG 信息',
    add_time          bigint      not null,
    primary key (context_id, message_index)
) comment '聊天消息';

DROP TABLE IF EXISTS chat_context_record;
create table chat_context_record
(
    context_id  varchar(64) not null
        primary key,
    title       varchar(64) not null,
    user_id     varchar(64) null,
    update_time bigint      not null
) comment '聊天会话上下文';

DROP TABLE IF EXISTS embeddings_item;
create table embeddings_item
(
    hash                 char(40)     not null comment '嵌入text的哈希值（SHA-1），同时也作为唯一标识符' primary key,
    embedding_model      varchar(256)  not null comment '嵌入模型名称',
    embedding_provider   varchar(256)  not null comment '嵌入模型提供商名称',
    check_embedding_hash varchar(64)  not null comment '用于标记数据的嵌入模型是否一致，不一致则需要进行重新向量化',
    text                 text         not null comment '嵌入文本',
    embedding            text         not null comment '嵌入向量',
    text_chunk_id        char(40)     not null comment '文本块ID',
    description          varchar(512) null comment '描述',
    create_time          bigint       null comment '创建时间',
    update_time          bigint       null comment '更新时间',
    create_user_id       varchar(32)  null comment '创建者ID'
);

DROP TABLE IF EXISTS file;
create table file
(
    id             int auto_increment comment '主键' primary key,
    full_file_name varchar(255)  not null comment '全文件名（含后缀）',
    suffix         varchar(32)   null comment '文件后缀',
    path           varchar(2048) not null comment '路径',
    size           bigint        not null comment '文件大小（字节）',
    md5            varchar(128)  not null comment '文件md5（32字节）',
    sha1           char(40)      not null comment '文件SHA-1',
    upload_time    bigint        not null comment '上传时间',
    create_user_id varchar(32)   null comment '创建者ID'
);

DROP TABLE IF EXISTS text_chunk;
create table text_chunk
(
    id             char(40)     not null comment '主键（文本块的SHA-1）' primary key,
    text_chunk     text         null comment '文本块',
    src_file_id    int          null comment '文本块文件ID',
    description    varchar(512) null comment '描述',
    create_time    bigint       null comment '创建时间',
    update_time    bigint       null comment '更新时间',
    create_user_id varchar(32)  null comment '创建者ID'
);

DROP TABLE IF EXISTS user;
create table user
(
    id            char(32)    not null
        primary key,
    username      varchar(64) not null,
    password_hash char(64)    not null comment '密码哈希值',
    create_time   bigint      null,
    role          int         null comment '0-普通用户, 1-管理员'
);