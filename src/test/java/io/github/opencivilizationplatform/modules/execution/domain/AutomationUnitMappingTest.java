package io.github.opencivilizationplatform.modules.execution.domain;

import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutomationUnitMappingTest {

    @Test
    void currentTaskMapsToTheSnakeCaseDatabaseColumn() throws NoSuchFieldException {
        Column column = AutomationUnit.class.getDeclaredField("currentTask").getAnnotation(Column.class);

        assertEquals("current_task", column.name());
    }
}