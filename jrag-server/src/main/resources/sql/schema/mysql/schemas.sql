DROP DATABASE IF EXISTS jrag;
CREATE DATABASE IF NOT EXISTS jrag DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE jrag;

DROP TABLE IF EXISTS embeddings_item;
CREATE TABLE embeddings_item
(
    hash               char(40)    NOT NULL COMMENT '嵌入text的哈希值（SHA-1），同时也作为唯一标识符',
    embedding_model    varchar(32) NOT NULL COMMENT '嵌入模型名称',
    embedding_provider varchar(32) NOT NULL COMMENT '嵌入模型提供商名称',
    text               text        NOT NULL COMMENT '嵌入文本',
    embedding          text        NOT NULL COMMENT '嵌入向量',
    text_chunk_id      char(40)    NOT NULL COMMENT '文本块ID',
    description        varchar(128) DEFAULT NULL COMMENT '描述',
    create_time        bigint       DEFAULT NULL COMMENT '创建时间',
    update_time        bigint       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (hash)
);

DROP TABLE IF EXISTS text_chunk;
CREATE TABLE text_chunk
(
    id          char(40) NOT NULL COMMENT '主键（文本块的SHA-1）',
    text_chunk  text         DEFAULT NULL COMMENT '文本块文本Chunk',
    src_file_id char(32)     DEFAULT NULL COMMENT '文本块文件ID',
    description varchar(128) DEFAULT NULL COMMENT '描述',
    create_time bigint       DEFAULT NULL COMMENT '创建时间',
    update_time bigint       DEFAULT NULL COMMENT '更新时间',
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS text_chunk_rel_static_file;
CREATE TABLE text_chunk_rel_static_file
(
    text_chunk_id char(40) NOT NULL COMMENT '文本块ID',
    file_id       char(32) NOT NULL COMMENT '文件ID'
);

DROP TABLE IF EXISTS file;
CREATE TABLE file
(
    id             char(32)      NOT NULL COMMENT '主键（文件的SHA-1）',
    full_file_name varchar(255)  not null comment '全文件名（含后缀）',
    suffix         varchar(32) comment '文件后缀',
    path           varchar(2048) not null comment '路径',
    size           bigint        not null comment '文件大小（字节）',
    md5            varchar(128)  not null comment '文件md5（32字节）',
    sha1           char(40)      not null comment '文件SHA-1',
    is_static_file int default 0 comment '是否静态文件（0-否，1-是）',
    upload_time    bigint        not null comment '上传时间',
    primary key (id)
);

DROP TABLE IF EXISTS chat_context_record;
CREATE TABLE chat_context_record
(
    context_id  char(32)    NOT NULL,
    title       varchar(64) NOT NULL,
    user_id     varchar(64),
    update_time bigint      NOT NULL,
    PRIMARY KEY (context_id)
) COMMENT ='聊天会话上下文';

DROP TABLE IF EXISTS chat_context_item;
CREATE TABLE chat_context_item
(
    context_id        char(32) NOT NULL,
    message_index     int      NOT NULL,
    chat_role         int      NOT NULL COMMENT '0-system, 1-user, 2-assistant',
    content           text     NOT NULL,
    feedback          int COMMENT '1-good, 2-bad',
    tool_call_process text COMMENT '工具调用过程',
    rag_infos         text COMMENT 'RAG 信息',
    add_time          bigint   NOT NULL,
    PRIMARY KEY (context_id, message_index)
) COMMENT ='聊天消息';

DROP TABLE IF EXISTS user;
CREATE TABLE user
(
    id            char(32)    NOT NULL,
    username      varchar(64) NOT NULL,
    password_hash char(64)    NOT NULL COMMENT '密码哈希值',
    create_time   bigint,
    PRIMARY KEY (id)
);