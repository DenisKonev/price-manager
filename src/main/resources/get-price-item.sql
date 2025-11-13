create function get_price_item(id bigint, tableName text) returns json
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

alter function get_price_item(bigint, text) owner to qwep_price;