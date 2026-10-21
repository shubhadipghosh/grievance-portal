package com.shubha.grievanceportal.organization.internal;

import com.shubha.grievanceportal.TestcontainersConfiguration;
import com.shubha.grievanceportal.organization.Category;
import com.shubha.grievanceportal.organization.Department;
import com.shubha.grievanceportal.organization.Ward;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(TestcontainersConfiguration.class)
class OrganizationRepositoriesTest {

    @Autowired
    TestEntityManager em;

    @Autowired
    WardRepository wards;

    @Autowired
    DepartmentRepository departments;

    @Autowired
    CategoryRepository categories;

    @Autowired
    CategoryTranslationRepository translations;

    @Autowired
    RoutingRuleRepository routingRules;

    @Test
    void databaseGeneratesIdsAndDefaults() {
        Ward ward = wards.saveAndFlush(new Ward("W12", "Ward 12"));
        Department dept = departments.saveAndFlush(new Department("TEST_SANITATION", "Sanitation"));

        assertThat(ward.getId()).isNotNull();
        assertThat(ward.getId().version()).isEqualTo(7);
        assertThat(dept.getId().version()).isEqualTo(7);

        em.clear();
        Ward reloaded = wards.findByCode("W12").orElseThrow();
        assertThat(reloaded).isEqualTo(ward);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.isActive()).isTrue();
    }

    @Test
    void categoryHierarchyIsLazy() {
        Department dept = departments.save(new Department("TEST_ROADS", "Roads"));
        Category parent = categories.save(new Category("TEST_ROADS", null, dept, 72));
        Category child = categories.saveAndFlush(new Category("POTHOLE", parent, dept, 48));
        em.clear();

        Category loaded = categories.findById(child.getId()).orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getParent())).isFalse();
        assertThat(Hibernate.isInitialized(loaded.getDefaultDepartment())).isFalse();
        // Proxy compares equal to the real entity without being initialized.
        assertThat(loaded.getParent()).isEqualTo(parent);
        assertThat(parent).isEqualTo(loaded.getParent());
        assertThat(loaded.getParent().getId()).isEqualTo(parent.getId());
    }

    @Test
    void translationsUseCompositeKey() {
        Department dept = departments.save(new Department("WATER", "Water Supply"));
        Category category = categories.save(new Category("NO_WATER", null, dept, 24));
        translations.save(new CategoryTranslation(category, "en", "No water supply", null));
        translations.save(new CategoryTranslation(category, "bn", "জল সরবরাহ নেই", "বিবরণ"));
        em.flush();
        em.clear();

        assertThat(translations.findByIdCategoryId(category.getId()))
                .extracting(CategoryTranslation::getLocale)
                .containsExactlyInAnyOrder("en", "bn");
        CategoryTranslation bn = translations.findById(new CategoryTranslationId(category.getId(), "bn")).orElseThrow();
        assertThat(bn.getName()).isEqualTo("জল সরবরাহ নেই");
    }

    @Test
    void routingRuleUsesCompositeKey() {
        Ward ward = wards.save(new Ward("W3", "Ward 3"));
        Department fallback = departments.save(new Department("ELEC", "Electrical"));
        Department override = departments.save(new Department("PWD", "Public Works"));
        Category category = categories.save(new Category("STREETLIGHT", null, fallback, 48));
        routingRules.save(new RoutingRule(category, ward, override));
        em.flush();
        em.clear();

        RoutingRule rule = routingRules.findById(new RoutingRuleId(category.getId(), ward.getId())).orElseThrow();
        assertThat(rule.getDepartment().getId()).isEqualTo(override.getId());
    }

    @Test
    void equalityIsIdBasedAndNullSafe() {
        Ward a = new Ward("X1", "X");
        Ward b = new Ward("X1", "X");
        assertThat(a).isNotEqualTo(b);
        assertThat(a).isEqualTo(a);

        Set<Ward> set = new HashSet<>();
        set.add(a);
        wards.saveAndFlush(a);
        // hashCode must survive the id being assigned.
        assertThat(set).contains(a);
    }
}
