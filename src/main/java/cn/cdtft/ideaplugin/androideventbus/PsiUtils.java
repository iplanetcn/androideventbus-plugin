package cn.cdtft.ideaplugin.androideventbus;

import com.intellij.psi.*;

import java.util.Objects;

/**
 * PsiUtils
 *
 * @author john
 * @since 2019-04-13
 */
class PsiUtils {

    static PsiClass getClass(PsiType psiType) {
        if (psiType instanceof PsiClassType) {
            return ((PsiClassType) psiType).resolve();
        }
        return null;
    }

    /**
     * 判断是否为EventBus接收器
     */
    static boolean isEventBusReceiver(PsiElement psiElement) {
        if (psiElement instanceof PsiMethod) {
            PsiMethod method = (PsiMethod) psiElement;
            PsiModifierList modifierList = method.getModifierList();
            for (PsiAnnotation psiAnnotation : modifierList.getAnnotations()) {
                if (Objects.equals(psiAnnotation.getQualifiedName(), "org.simple.eventbus.Subscriber")) {
                    final PsiAnnotationMemberValue tag = psiAnnotation.findAttributeValue("tag");
                    if (tag!=null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断是否为EventBus发送器
     */
    static boolean isEventBusPost(PsiElement psiElement) {
        if (psiElement instanceof PsiCallExpression) {
            PsiCallExpression callExpression = (PsiCallExpression) psiElement;
            PsiMethod method = callExpression.resolveMethod();
            if (method != null) {
                String name = method.getName();
                PsiElement parent = method.getParent();
                if ("post".equals(name) && parent instanceof PsiClass) {
                    final PsiParameter[] postParameters = method.getParameterList().getParameters();
                    for (PsiParameter param : postParameters) {
                        if (Objects.equals(param.getName(), "tag")) {
                            PsiClass implClass = (PsiClass) parent;
                            return isEventBusClass(implClass) || isSuperClassEventBus(implClass);
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 判断是否为EventBus类
     */
    private static boolean isEventBusClass(PsiClass psiClass) {
        try {
            return "EventBus".equals(Objects.requireNonNull(psiClass.getName()));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断是否为EventBus的超类
     */
    private static boolean isSuperClassEventBus(PsiClass psiClass) {
        PsiClass[] supers = psiClass.getSupers();
        if (supers.length == 0) {
            return false;
        }
        for (PsiClass superClass : supers) {
            try {
                if ("EventBus".equals(superClass.getName())) {
                    return true;
                }
            } catch (Exception e) {
                System.out.println(e.toString());
            }
        }
        return false;
    }

}
