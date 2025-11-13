create database qwep_price_dev
    with owner qwep_price;

\c qwep_price_dev;


create sequence public.price_sender_info_id_seq
    as integer;

alter sequence public.price_sender_info_id_seq owner to qwep_price;



create sequence public.token_2_code_id_seq;

alter sequence public.token_2_code_id_seq owner to qwep_price;



create table public.price_sender_info
(
    id                 integer primary key not null default nextval('price_sender_info_id_seq'::regclass),
    name               text,
    email              text                not null,
    admin_code         text,
    classification     text                not null,
    vendor_id          text                not null,
    price_table_refs   text[], -- values of each array are uuid strings
    last_updated       timestamp without time zone,
    file_path          text,
    view_code          text,
    current_state      text,
    price_currency     text,
    userapi_account_id text
);
create index price_sender_info_vendor_id_index on price_sender_info using btree (vendor_id);
create index price_sender_info_admin_code_index on price_sender_info using btree (admin_code);
comment on column public.price_sender_info.price_table_refs is 'values of each array are uuid strings';
alter sequence public.price_sender_info_id_seq owned by public.price_sender_info.id;

create table public.token_2_code
(
    id         bigint primary key not null default nextval('token_2_code_id_seq'::regclass),
    web_token  text               not null,
    admin_code text               not null
);
create index token_2_code_web_token_index on token_2_code using btree (web_token);



create function public.get_price_data(bas text[]) returns json
    language plpgsql
as
$$
DECLARE

    record    record;
    query     text;
    ba        text[];
    ret       json;
    ret_array json[];
    result    json;

BEGIN

    for record in (select c.relname   as table_name,
                          c.reltuples as rows
                   from pg_class c
                            join pg_namespace n on n.oid = c.relnamespace
                   where c.relkind = 'r'
                     and n.nspname not in ('information_schema', 'pg_catalog')
                     and c.relname <> 'price_sender_info'
                     and c.reltuples > 0
                   order by c.reltuples desc)
        loop
            foreach ba slice 1 in array bas
                loop

                    query := '
                  select array_to_json(array_agg(col))
                  from
                    (
                    select row_to_json(t) as col
                    from
                      (
                      select *
                      from "' || record.table_name || '" p
                      where p.brand = ''' || ba[1] || '''
                      and p.article = ''' || ba[2] || '''
                      )t
                    )t;';
                    execute query into ret;

                    if ret is not null then
                        ret_array := array_append(ret_array, ret);
                    end if;
                end loop;
        end loop;

    if ret_array is null then
        select '{}'::json[] into ret_array;
    end if;

    select array_to_json(array_agg(col)) atj
    from unnest(ret_array) col
    into result;

    if result is null then
        select '[]'::json into result;
    end if;

    RETURN result;

END;
$$;

alter function public.get_price_data(text[]) owner to qwep_price;

create function public.get_constrained_price_data(bas text[], v_id text) returns json
    language plpgsql
as
$$
DECLARE

    record    record;
    query     text;
    ba        text[];
    ret       json;
    ret_array json[];
    result    json;
    v_id_arg  text;

BEGIN

    v_id_arg := v_id;

    for record in (select unnest(price_table_refs) table_name
                   from price_sender_info
                   where vendor_id = v_id_arg)
        loop
            foreach ba slice 1 in array bas
                loop

                    query := '
                  select array_to_json(array_agg(col))
                  from
                    (
                    select row_to_json(t) as col
                    from
                      (
                      select *
                      from "' || record.table_name || '" p
                      where p.brand = ''' || ba[1] || '''
                      and p.article = ''' || ba[2] || '''
                      )t
                    )t;';
                    execute query into ret;

                    if ret is not null then
                        ret_array := array_append(ret_array, ret);
                    end if;
                end loop;
        end loop;

    if ret_array is null then
        select '{}'::json[] into ret_array;
    end if;

    select array_to_json(array_agg(col)) atj
    from unnest(ret_array) col
    into result;

    if result is null then
        select '[]'::json into result;
    end if;

    RETURN result;

END;
$$;

alter function public.get_constrained_price_data(text[], text) owner to qwep_price;

create function public.get_constrained_price_data_only_by_article(bas text[], v_id text) returns json
    language plpgsql
as
$$
DECLARE

    record    record;
    query     text;
    ba        text[];
    ret       json;
    ret_array json[];
    result    json;
    v_id_arg  text;

BEGIN

    v_id_arg := v_id;

    for record in (select unnest(price_table_refs) table_name
                   from price_sender_info
                   where vendor_id = v_id_arg)
        loop
            foreach ba slice 1 in array bas
                loop

                    query := '
                  select array_to_json(array_agg(col))
                  from
                    (
                    select row_to_json(t) as col
                    from
                      (
                      select *
                      from "' || record.table_name || '" p
                      where p.article = ''' || ba[2] || '''
                      )t
                    )t;';
                    execute query into ret;

                    if ret is not null then
                        ret_array := array_append(ret_array, ret);
                    end if;
                end loop;
        end loop;

    if ret_array is null then
        select '{}'::json[] into ret_array;
    end if;

    select array_to_json(array_agg(col)) atj
    from unnest(ret_array) col
    into result;

    if result is null then
        select '[]'::json into result;
    end if;

    RETURN result;

END;
$$;

alter function public.get_constrained_price_data_only_by_article(text[], text) owner to qwep_price;

create function public.get_price_item(id bigint, tablename text) returns json
    language plpgsql
as
$$
DECLARE

    query         text;
    id_arg        bigint;
    ret           json;
    tableName_arg text;

BEGIN

    id_arg := id;
    tableName_arg := tableName;

    query := '
                  select array_to_json(array_agg(col))
                  from
                    (
                    select row_to_json(t) as col
                    from
                      (
                      select *
                      from "' || tableName_arg || '" p
                      where p.id = ''' || id_arg || '''
                      )t
                    )t;';

    execute query into ret;

    if ret is null then
        select '[]'::json into ret;
    end if;

    RETURN ret;

END;
$$;

alter function public.get_price_item(bigint, text) owner to qwep_price;

alter table price_sender_info
    add column configurations text;


CREATE TABLE price_file
(
    id        SERIAL PRIMARY KEY,
    sender_id integer,
    name      text,
    file      bytea,
    UNIQUE (sender_id),
    FOREIGN KEY (sender_id) REFERENCES price_sender_info (id)
);

alter table price_sender_info
    add column email_identification bool;