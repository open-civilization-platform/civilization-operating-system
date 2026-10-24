package io.github.opencivilizationplatform.config;

import io.github.opencivilizationplatform.modules.contribution.domain.Citizen;
import io.github.opencivilizationplatform.modules.contribution.domain.Project;
import io.github.opencivilizationplatform.modules.social.domain.Case;
import io.github.opencivilizationplatform.modules.social.domain.Incident;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ElementCollectionMappingTest {

    @Test
    void elementCollectionsMatchTheFlywayTableAndColumnNames() throws Exception {
        assertCollection(Citizen.class, "interests", "citizen_interests", "citizen_id", "interests");
        assertCollection(Incident.class, "participantIds", "social_incidents_participant_ids", "social_incidents_id", "participant_ids");
        assertCollection(Case.class, "panelExpertIds", "social_cases_panel_expert_ids", "social_cases_id", "panel_expert_ids");
        assertCollection(Project.class, "requiredSkillNames", "contribution_projects_required_skill_names", "contribution_projects_id", "required_skill_names");
    }

    private void assertCollection(Class<?> entity, String fieldName, String tableName,
                                  String joinColumnName, String valueColumnName) throws Exception {
        Field field = entity.getDeclaredField(fieldName);
        CollectionTable table = field.getAnnotation(CollectionTable.class);
        Column valueColumn = field.getAnnotation(Column.class);

        assertNotNull(table, entity.getSimpleName() + "." + fieldName + " needs an explicit collection table");
        assertEquals(tableName, table.name());
        assertEquals(1, table.joinColumns().length);
        assertEquals(joinColumnName, table.joinColumns()[0].name());
        assertNotNull(valueColumn, entity.getSimpleName() + "." + fieldName + " needs an explicit value column");
        assertEquals(valueColumnName, valueColumn.name());
    }
}