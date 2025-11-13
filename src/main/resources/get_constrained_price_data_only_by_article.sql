create function get_constrained_price_data_only_by_article(bas text[], v_id text) returns json
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
    v_id_arg      text;

BEGIN

    v_id_arg := v_id;

    for record in (select unnest(price_table_refs) table_name from price_sender_info
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

alter function get_constrained_price_data_only_by_article(text[], text) owner to qwep_price;