-- The household chooses the order activities are shown in; existing ones keep their alphabetical order.
alter table category add column position integer;
update category set position = ordered.position
from (select id, row_number() over (order by name) as position from category) ordered
where category.id = ordered.id;
alter table category alter column position set not null;
