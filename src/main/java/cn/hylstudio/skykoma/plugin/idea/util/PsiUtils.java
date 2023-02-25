package cn.hylstudio.skykoma.plugin.idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.apache.commons.collections.CollectionUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class PsiUtils {
    private static final Logger LOGGER = Logger.getInstance(PsiUtils.class);

    public static String getter(String fieldName) {
        return "get" + StringUtil.capitalize(fieldName);
    }

    public static String setter(String fieldName) {
        return "set" + StringUtil.capitalize(fieldName);
    }

    public static String declare(String className, String variableName, String val) {
        return String.format("%s %s = %s", className, variableName, val);
    }

    public static String fieldAccessor(String fieldName) {
        return String.format("this.%s", fieldName);
    }

    public static String invokeStmt(String exp, String methodName, String paramsList) {
        return String.format("%s.%s(%s)", exp, methodName, paramsList);
    }

    public static String invokeStmt(String methodName, String paramsList) {
        return String.format("%s(%s)", methodName, paramsList);
    }

    public static String assignStmt(String assignedExpression, String valueExpression) {
        return String.format("%s = %s", assignedExpression, valueExpression);
    }
    public static String getAnnotationValue(PsiModifierList modifierList, String annotationClassName,
                                            String attributeName) {
        return getAnnotationValue(modifierList, annotationClassName, attributeName, "");
    }

    public static String getAnnotationValue(PsiModifierList modifierList, String annotationClassName,
                                            String attributeName, String defaultResult) {
        if (modifierList == null) {
            return defaultResult;
        }
        PsiAnnotation annotation = modifierList.findAnnotation(annotationClassName);
        if (annotation == null) {
            return defaultResult;
        }
        PsiAnnotationMemberValue valueProperty = annotation.findAttributeValue(attributeName);
        List<String> tableNameValue = PsiUtils.parseAnnotationValue(valueProperty);
        if (CollectionUtils.isEmpty(tableNameValue)) {
            return defaultResult;
        }
        return tableNameValue.get(0);
    }

    public static List<String> parseAnnotationValue(PsiAnnotationMemberValue value) {
        if (value == null) {
            return new ArrayList<>();
        }
        if (value instanceof PsiExpression) {
            PsiExpression v = (PsiExpression) value;
            return parseExpression(v);
        } else if (value instanceof PsiArrayInitializerMemberValue) {
            PsiArrayInitializerMemberValue v = (PsiArrayInitializerMemberValue) value;
            PsiAnnotationMemberValue[] initializers = v.getInitializers();
            return Arrays.stream(initializers).map(PsiUtils::parseAnnotationValue).reduce((o1, o2) -> {
                List<String> tmp = new ArrayList<>(o1.size() + o2.size());
                tmp.addAll(o1);
                tmp.addAll(o2);
                return tmp;
            }).orElse(new ArrayList<>());
        } else {
            PsiFile containingFile = value.getContainingFile();
            LOGGER.info("parseAnnotationValue, psiClass = " + containingFile.getName() + ", valueType = " + value.getClass());
        }
        return new ArrayList<>(0);
    }

    private static List<String> parseExpression(PsiExpression expression) {
        if (expression instanceof PsiLiteralExpression) {
            PsiLiteralExpression v = (PsiLiteralExpression) expression;// @RequestMapping(value = "/a/b/c")
            return Collections.singletonList((String) v.getValue());
        } else if (expression instanceof PsiReferenceExpression) {// @RequestMapping(method = RequestMethod.POST)
            PsiReferenceExpression v = (PsiReferenceExpression) expression;
            return Collections.singletonList(v.getText());
        } else if (expression instanceof PsiArrayInitializerExpression) {// @RequestMapping(value = {"/", ""})
            PsiArrayInitializerExpression v = (PsiArrayInitializerExpression) expression;
            PsiExpression[] initializers = v.getInitializers();
            return Arrays.stream(initializers).map(PsiUtils::parseExpression).reduce((o1, o2) -> {
                assert o2 != null;
                o1.addAll(o2);
                return o1;
            }).orElse(new ArrayList<>());
        } else {
            PsiFile containingFile = expression.getContainingFile();
            LOGGER.info("parseExpression, psiClass = " + containingFile.getName() + ", expressionType = " + expression.getClass());
        }
        return new ArrayList<>();
    }

    public static boolean classHasAnnotation(PsiClass psiClass, Set<String> annotations) {
        PsiModifierList modifierList = psiClass.getModifierList();
        if (modifierList == null) {
            return false;
        }
        for (String annotation : annotations) {
//            boolean hasAnnotation = modifierList.hasAnnotation(annotation);
//            not sure available for old version
            PsiAnnotation psiAnnotation = modifierList.findAnnotation(annotation);
            if (psiAnnotation != null) {
                return true;
            }
        }
        return false;
    }

    public static int getLineNumber(PsiElement v) {
        PsiFile containingFile = v.getContainingFile();
        FileViewProvider fileViewProvider = containingFile.getViewProvider();
        Document document = fileViewProvider.getDocument();
        int textOffset = v.getTextOffset();
        if (textOffset < 0) {
            return 1;
        }
        int lineNumber = document.getLineNumber(textOffset);
        return lineNumber + 1;
    }

    public static String getCallSignature(PsiMethod v) {
        String methodName = v.getName();
        HierarchicalMethodSignature hierarchicalMethodSignature = v.getHierarchicalMethodSignature();
        PsiType[] parameterTypes = hierarchicalMethodSignature.getParameterTypes();
        String paramTypes = Arrays.stream(parameterTypes).map(PsiType::getCanonicalText).collect(Collectors.joining(","));
        return String.format("%s(%s)", methodName, paramTypes);
    }

    public static Project getProjectByPath(String projectBasePath) {
        try {
            ProjectLocator projectLocator = ProjectLocator.getInstance();
            VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
            assert projectBasePath != null;
            VirtualFile projectRootDir = virtualFileManager.findFileByNioPath(new File(projectBasePath).toPath());
            Project project = projectLocator.guessProjectForFile(projectRootDir);
            return project;
        } catch (Exception e) {
            LOGGER.info(String.format("getProjectByPath error, e = %s", e.getMessage()), e);
        }
        return null;
    }

    /**
     * 获取普通参数类型的类名
     *
     * @param callExpr
     * @param argIndex
     * @return
     */
    public static String getArgType(PsiMethodCallExpression callExpr, int argIndex) {
        PsiExpressionList argumentList = callExpr.getArgumentList();
        PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length <= 0) {
            return "";
        }
        PsiType type = expressions[argIndex].getType();
        if (type == null) {
            return "";
        }
        return getTypeQualifiedName(type);
    }

    /**
     * 获取形如Abc.class的参数类型Abc的类名
     *
     * @param callExpr
     * @param argIndex
     * @return
     */
    public static String getClassObjectAccessType(PsiMethodCallExpression callExpr, int argIndex) {
        PsiExpressionList argumentList = callExpr.getArgumentList();
        PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length <= 0) {
            return "";
        }

        PsiExpression expression = expressions[argIndex];
        boolean isPsiClassObjectAccessExpression = expression instanceof PsiClassObjectAccessExpression;
        if (!isPsiClassObjectAccessExpression) {
            return "";
        }
        PsiClassObjectAccessExpression exp = (PsiClassObjectAccessExpression) expression;
        PsiTypeElement operand = exp.getOperand();
        PsiType type = operand.getType();
        return getTypeQualifiedName(type);
    }

    /**
     * 获取形如List<T>的参数类型T的类名
     *
     * @param callExpr
     * @param argIndex
     * @return
     */
    public static String getGenericType(PsiMethodCallExpression callExpr, int argIndex) {
        PsiExpressionList argumentList = callExpr.getArgumentList();
        PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length <= 0) {
            return "";
        }
        PsiExpression expression = expressions[argIndex];
        PsiType type = expression.getType();
        boolean isClassType = type instanceof PsiClassType;
        if (!isClassType) {
            return "";
        }
        PsiClassType classType = (PsiClassType) type;
        PsiType[] parameters = classType.getParameters();
        if (parameters.length <= 0) {
            return "";
        }
        return getTypeQualifiedName(parameters[0]);
    }

    //TODO 改写法支持任意层的泛型
    public static String getGenericTypeDeep2(PsiMethodCallExpression callExpr, int argIndex) {
        PsiExpressionList argumentList = callExpr.getArgumentList();
        PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length == 0) {
            return "";
        }
        PsiExpression expression = expressions[argIndex];
        PsiType type = expression.getType();
        boolean isClassType = type instanceof PsiClassType;
        if (!isClassType) {
            return "";
        }
        PsiClassType classType = (PsiClassType) type;
        PsiType[] parameters = classType.getParameters();
        if (parameters.length == 0) {
            return "";
        }
        PsiType parameter = parameters[0];
        isClassType = parameter instanceof PsiClassType;
        if (!isClassType) {
            return "";
        }
        classType = (PsiClassType) parameter;
        parameters = classType.getParameters();
        if (parameters.length == 0) {
            return "";
        }
        return getTypeQualifiedName(parameters[0]);
    }

    @Nullable
    private static String getTypeQualifiedName(PsiType type) {
        PsiClass entityTypeClass = PsiTypesUtil.getPsiClass(type);
        if (entityTypeClass == null) {
            return "";
        }
        return entityTypeClass.getQualifiedName();
    }

}
