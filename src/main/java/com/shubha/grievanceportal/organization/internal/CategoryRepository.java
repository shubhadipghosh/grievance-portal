package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.organization.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findByCode(String code);

    /**
     * Each active category named in the first of {@code languages} it has a
     * translation for, sorted by that name. A category with none of them has
     * no name to show and is left out.
     */
    @Query(nativeQuery = true, value = """
            select id, "parentId", name from (
                select distinct on (c.id) c.id as id, c.parent_id as "parentId", t.name as name
                from category c
                join category_translation t on t.category_id = c.id
                where c.active
                  and t.locale = any(:languages)
                order by c.id, array_position(:languages, t.locale::text)
            ) best
            order by name
            """)
    List<SummaryRow> findActiveSummaries(@Param("languages") String[] languages);

    interface SummaryRow {
        UUID getId();

        UUID getParentId();

        String getName();
    }
}
