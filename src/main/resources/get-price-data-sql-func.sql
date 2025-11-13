create function get_price_data(bas text[]) returns json
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
                     and c.relname <> 'token_2_code'
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

alter function get_price_data(text[]) owner to qwep_price;