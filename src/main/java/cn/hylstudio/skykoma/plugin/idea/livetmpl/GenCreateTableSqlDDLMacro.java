package cn.hylstudio.skykoma.plugin.idea.livetmpl;

import com.google.common.base.Joiner;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.macro.MacroBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.SkykomaConstants.JPA_COLUMN_CLASS;
import static cn.hylstudio.skykoma.plugin.idea.SkykomaConstants.JPA_TABLE_CLASS;
import static cn.hylstudio.skykoma.plugin.idea.util.PsiUtils.getAnnotationValue;

/**
 * Generate create table sql from ORM annotations
 */
public class GenCreateTableSqlDDLMacro extends MacroBase {
    private static final Logger LOGGER = Logger.getInstance(GenCreateTableSqlDDLMacro.class);

    public GenCreateTableSqlDDLMacro() {
        this("genCreateTableSql", "genCreateTableSql()");
    }

    /**
     * Strictly to uphold contract for constructors in base class.
     */
    private GenCreateTableSqlDDLMacro(String name, String description) {
        super(name, description);
    }

    @Override
    protected Result calculateResult(Expression @NotNull [] params, ExpressionContext expressionContext,
                                     boolean useSelection) {
        PsiElement currentElement = expressionContext.getPsiElementAtStartOffset();
        PsiClass currentClass = PsiTreeUtil.getParentOfType(currentElement, PsiClass.class);
        if (currentClass == null) {
            return new TextResult("//genCreateTableSql error, currentClass empty");
        }
        // parse params
        List<PsiField> fields = Arrays.asList(currentClass.getFields());
        PsiModifierList modifierList = currentClass.getModifierList();
        String tableName = getAnnotationValue(modifierList, JPA_TABLE_CLASS, "name");
        String columnDefs = fields.stream().map(this::getColumnDef)
                .filter(v -> !StringUtils.isEmpty(v))
                .collect(Collectors.joining(",\n"));
        String tableDef = "" +
                "// CREATE TABLE `%s` IF NOT EXISTS (\n" +
                "%s,\n" +
                "//   PRIMARY KEY (`id`)\n" +
                "// ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        String output = String.format(tableDef, tableName, columnDefs);
        return new TextResult(output);
    }

    private String getColumnDef(PsiField field) {
        List<String> stmt = new ArrayList<>();
        PsiModifierList modifierList = field.getModifierList();
        String columnName = getAnnotationValue(modifierList, JPA_COLUMN_CLASS, "name", "column_name");
        if (!columnName.startsWith("`")) {
            stmt.add(String.format("`%s`", columnName));
        } else {
            stmt.add(columnName);
        }
        String type = mappingToSqlType(field.getType());
        stmt.add(type);
        if ("id".equals(columnName))
            stmt.add("UNSIGNED");
        stmt.add("NOT NULL");
        if (type.startsWith("VARCHAR"))
            stmt.add("DEFAULT ''");
        if ("id".equals(columnName))
            stmt.add("AUTO_INCREMENT");
        if ("created_at".equals(columnName))
            stmt.add("DEFAULT '2000-01-01 00:00:00'");
        if ("updated_at".equals(columnName))
            stmt.add("DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
        return "//   " + Joiner.on(" ").join(stmt);

    }

    private String mappingToSqlType(PsiType type) {
        PsiClass declaringClass = PsiTypesUtil.getPsiClass(type);
        if (declaringClass == null) {
            return "VARCHAR(255)";
        }
        String name = declaringClass.getName();
        if (name == null) {
            return "VARCHAR(255)";
        }
        switch (name) {
            case "Timestamp":
                return "TIMESTAMP";
            case "Long":
                return "BIGINT(20)";
            case "Integer":
                return "INT(11)";
            case "Boolean":
                return "TINYINT(1)";
            case "String":
            default:
                return "VARCHAR(255)";
        }
    }


    private String getMarcoResult(Expression expression, ExpressionContext expressionContext, boolean useSelection) {
        String result = getTextResult(new Expression[]{expression}, expressionContext, useSelection);
        return result;
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        // Might want to be less restrictive in future
        return true;
    }

}
