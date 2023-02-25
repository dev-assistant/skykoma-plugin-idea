package cn.hylstudio.skykoma.plugin.idea.livetmpl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.macro.MacroBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static cn.hylstudio.skykoma.plugin.idea.SkykomaConstants.END_LINE;

/**
 * Generate all setters by current method first params
 */
public class GenAllSetterByParamMacro extends MacroBase {
    private static final Logger LOGGER = Logger.getInstance(GenAllSetterByParamMacro.class);

    public GenAllSetterByParamMacro() {
        this("genAllSetterByParam", "genAllSetterByParam()");
    }

    private GenAllSetterByParamMacro(String name, String description) {
        super(name, description);
    }

    @Override
    protected Result calculateResult(Expression @NotNull [] params, ExpressionContext expressionContext, boolean useSelection) {
        PsiElement currentElement = expressionContext.getPsiElementAtStartOffset();
        PsiMethod currentMethod = PsiTreeUtil.getParentOfType(currentElement, PsiMethod.class);
        if (currentMethod == null) {
            return new TextResult("//genAllSetterByParam error, currentMethod empty");
        }
        PsiClass currentClass = PsiTreeUtil.getParentOfType(currentElement, PsiClass.class);
        if (currentClass == null) {
            return new TextResult("//genAllSetterByParam error, currentClass empty");
        }
        boolean isConstructor = currentMethod.isConstructor();
        PsiParameter[] parameters = currentMethod.getParameterList().getParameters();
        if (parameters.length == 0) {
            return new TextResult("//genAllSetterByParam error, parameters empty");
        }
        // find first params
        PsiParameter firstParam = parameters[0];
        PsiClass paramTypeClass = PsiTypesUtil.getPsiClass(firstParam.getType());
        if (paramTypeClass == null) {
            return new TextResult("//genAllSetterByParam error, paramTypeClass resole failed");
        }
        String paramName = firstParam.getName();
        List<PsiField> paramFields = Arrays.asList(paramTypeClass.getFields());
        String currentClassName = currentClass.getName();
        if (StringUtils.isEmpty(currentClassName)) {
            return new TextResult("//genAllSetterByParam error, currentClassName empty");
        }
        String variableName = StringUtil.decapitalize(currentClassName);
        StringBuilder sb = new StringBuilder();
        if (!isConstructor) {
            String variableDeclare = PsiUtils.declare(currentClassName, variableName, "this");
            sb.append(variableDeclare).append(END_LINE).append("\n");
        }
        List<PsiField> currentFields = Arrays.asList(currentClass.getFields());
        List<String> unionFieldNames = unionFieldNames(currentFields, paramFields);
        String setters = unionFieldNames.stream().map(v -> genSetterStmt(paramTypeClass, currentClass, isConstructor, paramName, variableName, v)).filter(v -> !StringUtils.isEmpty(v)).collect(Collectors.joining("\n"));
        sb.append(setters);
        String wrapTemplate = String.format(SkykomaConstants.OUTPUT_TEMPLATE, sb);
        return new TextResult(wrapTemplate);
    }

    private List<String> unionFieldNames(List<PsiField> targetFields, List<PsiField> srcFields) {
        List<String> targetFieldNames = targetFields.stream().map(PsiField::getName).collect(Collectors.toList());
        List<String> srcFieldNames = srcFields.stream().map(PsiField::getName).collect(Collectors.toList());
        HashSet<String> targetFieldNamesSet = new HashSet<>(targetFieldNames);
        HashSet<String> srcFieldNamesSet = new HashSet<>(srcFieldNames);
        Sets.SetView<String> union = Sets.union(targetFieldNamesSet, srcFieldNamesSet);
//        Sets.SetView<String> bothIn = Sets.intersection(targetFieldNamesSet, srcFieldNamesSet);
//        Sets.SetView<String> onlyInTarget = Sets.difference(targetFieldNamesSet, srcFieldNamesSet);
        Sets.SetView<String> onlyInSrc = Sets.difference(srcFieldNamesSet, targetFieldNamesSet);
        List<String> result = new ArrayList<>(union.size());
//        List<String> bothInFields = targetFieldNames.stream().filter(bothIn::contains).collect(Collectors.toList());
//        List<String> onlyInTargetFields = targetFieldNames.stream().filter(onlyInTarget::contains).collect(Collectors.toList());
        List<String> onlyInSrcFields = srcFieldNamesSet.stream().filter(onlyInSrc::contains).collect(Collectors.toList());
        result.addAll(targetFieldNames);
        result.addAll(onlyInSrcFields);
        return result;
    }

    private String genSetterStmt(PsiClass srcClass, PsiClass targetClass, boolean isConstructor, String paramName, String variableName, String fieldName) {
        String getter = PsiUtils.getter(fieldName);
        String valExp = PsiUtils.invokeStmt(paramName, getter, "");//a.getXXX()
        PsiField targetClassField = targetClass.findFieldByName(fieldName, false);// String name, boolean checkBases
        PsiField srcClassField = srcClass.findFieldByName(fieldName, false);// String name, boolean checkBases
        if (targetClassField != null && srcClassField != null) {
            PsiType srcType = srcClassField.getType();
            PsiType targetType = targetClassField.getType();
            valExp = tryFillWrapper(srcType, targetType, valExp);
        }
        String stmt;
        if (isConstructor) {
            String assignedExp = PsiUtils.fieldAccessor(fieldName);
            stmt = PsiUtils.assignStmt(assignedExp, valExp);
        } else {
            String setter = PsiUtils.setter(fieldName);
            stmt = PsiUtils.invokeStmt(variableName, setter, valExp);
        }
        return stmt + END_LINE;
    }

    private String tryFillWrapper(PsiType srcPsiType, PsiType targetPsiType, String valExp) {
        PsiClass srcClass = PsiTypesUtil.getPsiClass(srcPsiType);
        PsiClass targetClass = PsiTypesUtil.getPsiClass(targetPsiType);
        String srcType = SkykomaConstants.UNKNOWN_TYPE;
        String srcTypeName = SkykomaConstants.UNKNOWN_TYPE;
        if (srcClass != null && !StringUtils.isEmpty(srcClass.getQualifiedName())) {
            srcType = srcClass.getQualifiedName();
            srcTypeName = srcClass.getName();
        }
        String targetType = SkykomaConstants.UNKNOWN_TYPE;
        String targetTypeName = SkykomaConstants.UNKNOWN_TYPE;
        if (targetClass != null && !StringUtils.isEmpty(targetClass.getQualifiedName())) {
            targetType = targetClass.getQualifiedName();
            targetTypeName = targetClass.getName();
        }
        if (targetType.equals(srcType)) {
            return valExp;
        }
        String wrapperExp;
        if (SkykomaConstants.TYPE_TIMESTAMP.equals(srcType) && SkykomaConstants.TYPE_LONG.equals(targetType)
            //TODO 自动寻找适合的方法做类型转换
        ) {
            wrapperExp = PsiUtils.invokeStmt(valExp, "getTime", "");
        } else {
            String methodName = String.format("convert%sTo%s", srcTypeName, targetTypeName);
            wrapperExp = PsiUtils.invokeStmt(methodName, valExp);
        }
        return wrapperExp;
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        // Might want to be less restrictive in future
        return true;
    }

}
