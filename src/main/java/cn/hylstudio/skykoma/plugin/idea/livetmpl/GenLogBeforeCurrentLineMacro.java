package cn.hylstudio.skykoma.plugin.idea.livetmpl;

import cn.hylstudio.skykoma.plugin.idea.SkykomaConstants;
import cn.hylstudio.skykoma.plugin.idea.util.PsiUtils;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.macro.MacroBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generate LOGGER before current line
 */
public class GenLogBeforeCurrentLineMacro extends MacroBase {
    private static final Logger LOGGER = Logger.getInstance(GenLogBeforeCurrentLineMacro.class);

    public GenLogBeforeCurrentLineMacro() {
        this("genLogBeforeCurrentLine", "genLogBeforeCurrentLine()");
    }

    private GenLogBeforeCurrentLineMacro(String name, String description) {
        super(name, description);
    }

    @Override
    protected Result calculateResult(Expression @NotNull [] params, ExpressionContext expressionContext,
                                     boolean useSelection) {
        Editor editor = expressionContext.getEditor();
        if (editor == null) {
            return new TextResult("//genAllSetterByParam error, editor empty");
        }
        PsiElement currentElement = expressionContext.getPsiElementAtStartOffset();
        if (currentElement == null) {
            return new TextResult("//genAllSetterByParam error, currentElement empty");
        }
        PsiMethod currentMethod = PsiTreeUtil.getParentOfType(currentElement, PsiMethod.class);
        if (currentMethod == null) {
            return new TextResult("//genAllSetterByParam error, currentMethod empty");
        }
        PsiClass currentClass = PsiTreeUtil.getParentOfType(currentElement, PsiClass.class);
        if (currentClass == null) {
            return new TextResult("//genAllSetterByParam error, currentClass empty");
        }
        String methodName = currentMethod.getName();
        List<PsiParameter> parameters = Arrays.asList(currentMethod.getParameterList().getParameters());
        List<String> logParams = parameters.stream().map(PsiParameter::getName).collect(Collectors.toList());
        //TODO 处理作用域
        List<String> localVariables = getLocalVariables(editor, currentMethod);
        boolean isController = PsiUtils.classHasAnnotation(currentClass, SkykomaConstants.CONTROLLER_ANNOTATIONS);
        mergeLocalVariables(logParams, isController, localVariables);
        String paramsPattern = logParams.stream()
                .map(v -> String.format("%s = [{}]", v))
                .collect(Collectors.joining(", "));
        String logStmtPattern = String.format("%s, %s", methodName, paramsPattern);
        String logStmtParams = String.join(", ", logParams);
        String result = String.format("LOGGER.info(\"%s\", %s);", logStmtPattern, logStmtParams);
        return new TextResult(result);
    }

    private static void mergeLocalVariables(List<String> logParams, boolean isController, List<String> localVariables) {
        Set<String> currentParamSet = new HashSet<>(logParams.size() + localVariables.size());
        currentParamSet.addAll(logParams);
        localVariables.forEach(v -> {
            if (!currentParamSet.contains(v)) {
                if ("uid".equalsIgnoreCase(v) && isController) {
                    logParams.add(0, v);
                } else {
                    logParams.add(v);
                }
            }
        });
    }

    private List<String> getLocalVariables(Editor editor, PsiMethod containingMethod) {
        List<String> result = new ArrayList<>();
        int offset = editor.getCaretModel().getOffset();
        PsiFile psiFile = containingMethod.getContainingFile();
        PsiElement element = psiFile.findElementAt(offset);
        if (element == null) {
            return result;
        }
        containingMethod.accept(new JavaRecursiveElementVisitor() {
            @Override
            public void visitLocalVariable(PsiLocalVariable variable) {
                if (variable.getTextOffset() <= offset) {
                    result.add(variable.getName());
                }
            }
        });
        return result;
    }

    @Override
    public boolean isAcceptableInContext(TemplateContextType context) {
        // Might want to be less restrictive in future
        return true;
    }

}
