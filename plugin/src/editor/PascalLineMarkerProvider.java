package com.siberika.idea.pascal.editor;

import com.intellij.codeHighlighting.Pass;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.DefaultPsiElementCellRenderer;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ConstantFunction;
import com.intellij.util.SmartList;
import com.siberika.idea.pascal.lang.psi.PasClassMethod;
import com.siberika.idea.pascal.lang.psi.PasDeclSection;
import com.siberika.idea.pascal.lang.psi.PasEntityScope;
import com.siberika.idea.pascal.lang.psi.PasExportedRoutine;
import com.siberika.idea.pascal.lang.psi.PasMethodImplDecl;
import com.siberika.idea.pascal.lang.psi.PasModule;
import com.siberika.idea.pascal.lang.psi.PasNamedIdent;
import com.siberika.idea.pascal.lang.psi.PasRoutineImplDecl;
import com.siberika.idea.pascal.lang.psi.PascalNamedElement;
import com.siberika.idea.pascal.lang.psi.impl.PasEntityScopeImpl;
import com.siberika.idea.pascal.lang.psi.impl.PasField;
import com.siberika.idea.pascal.lang.psi.impl.PasImplDeclSectionImpl;
import com.siberika.idea.pascal.lang.psi.impl.PascalRoutineImpl;
import com.siberika.idea.pascal.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * Author: George Bakhtadze
 * Date: 05/09/2013
 */
public class PascalLineMarkerProvider implements LineMarkerProvider {

    private void collectNavigationMarkers(@NotNull PsiElement element, Collection<? super LineMarkerInfo> result) {
        if (element instanceof PascalRoutineImpl) {
            PascalRoutineImpl routineDecl = (PascalRoutineImpl) element;
            Collection<PsiElement> targets;
            if (routineDecl instanceof PasExportedRoutine) {
                targets = getImplementationRoutinesTargets(routineDecl);
                if (!targets.isEmpty()) {
                    result.add(createLineMarkerInfo(element, AllIcons.Gutter.ImplementedMethod, "Go to implementation", getHandler(targets)));
                }
            } else if (routineDecl instanceof PasClassMethod) {
                targets = getImplementationMethodTargets(routineDecl);
                if (!targets.isEmpty()) {
                    result.add(createLineMarkerInfo(element, AllIcons.Gutter.ImplementedMethod, "Go to implementation", getHandler(targets)));
                }
            } else if (routineDecl instanceof PasRoutineImplDecl) {
                targets = getInterfaceRoutinesTargets(routineDecl);
                if (!targets.isEmpty()) {
                    result.add(createLineMarkerInfo(element, AllIcons.Gutter.ImplementingMethod, "Go to interface", getHandler(targets)));
                }
            } else if (routineDecl instanceof PasMethodImplDecl) {
                targets = getInterfaceMethodTargets(routineDecl);
                if (!targets.isEmpty()) {
                    result.add(createLineMarkerInfo(element, AllIcons.Gutter.ImplementingMethod, "Go to interface", getHandler(targets)));
                }
            }

        }
    }

    public LineMarkerInfo<PsiElement> createLineMarkerInfo(@NotNull PsiElement element, Icon icon, final String tooltip,
                                                           @NotNull GutterIconNavigationHandler<PsiElement> handler) {
        LineMarkerInfo<PsiElement> info = new LineMarkerInfo<PsiElement>(element, element.getTextRange(),
                icon, Pass.UPDATE_OVERRIDEN_MARKERS,
                new ConstantFunction<PsiElement, String>(tooltip), handler,
                GutterIconRenderer.Alignment.RIGHT);
        return info;
    }

    @Nullable
    @Override
    public LineMarkerInfo getLineMarkerInfo(@NotNull PsiElement element) {
        return null;
    }

    @Override
    public void collectSlowLineMarkers(@NotNull List<PsiElement> elements, @NotNull Collection<LineMarkerInfo> result) {
        for (PsiElement psiElement : elements) {
            collectNavigationMarkers(psiElement, result);
        }
    }

    private GutterIconNavigationHandler<PsiElement> getHandler(@NotNull final Collection<PsiElement> targets) {
        return new GutterIconNavigationHandler<PsiElement>() {
            @Override
            public void navigate(MouseEvent e, PsiElement elt) {
                PsiElementListNavigator.openTargets(e,
                        targets.toArray(new NavigatablePsiElement[targets.size()]),
                        "Title", null, new DefaultPsiElementCellRenderer());
            }
        };
    }

    private Collection<PsiElement> getInterfaceRoutinesTargets(PascalRoutineImpl routineDecl) {
        PsiElement section = PsiUtil.getModuleInterfaceSection(routineDecl.getContainingFile());
        Collection<PsiElement> result = new SmartList<PsiElement>();
        if (section == null) {
            return result;
        }
        //noinspection unchecked
        for (PsiElement element : PsiUtil.findImmChildrenOfAnyType(section, PasExportedRoutine.class)) {
            PasExportedRoutine routine = (PasExportedRoutine) element;
            PasNamedIdent nameIdent = routine.getNamedIdent();
            if ((nameIdent != null) && (nameIdent.getName().equalsIgnoreCase(routineDecl.getName()))) {
                result.add(routine);
            }
        }
        return result;
    }

    private Collection<PsiElement> getInterfaceMethodTargets(@NotNull PascalRoutineImpl routineDecl) {
        Collection<PsiElement> result = new SmartList<PsiElement>();
        PasModule module = PsiUtil.getModule(routineDecl);
        if (module != null) {
            PasField typeMember = module.getField(routineDecl.getNamespace());
            if ((typeMember != null) && (typeMember.type == PasField.Type.TYPE)) {
                PasEntityScope struct = PasEntityScopeImpl.getStructByNameElement(typeMember.element);
                if (struct != null) {
                    PasField field = struct.getField(routineDecl.getNamePart());
                    if ((field != null) && (field.type == PasField.Type.ROUTINE)) {
                        result.add(field.element);
                    }
                }
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Collection<PsiElement> getImplementationRoutinesTargets(PascalRoutineImpl routineDecl) {
        Collection<PsiElement> result = new SmartList<PsiElement>();
        findImplTargets(result, routineDecl.getContainingFile(), routineDecl.getName(), PascalRoutineImpl.class);
        return result;
    }

    private Collection<PsiElement> getImplementationMethodTargets(PascalRoutineImpl routineDecl) {
        Collection<PsiElement> result = new SmartList<PsiElement>();
        PasEntityScopeImpl owner = PasEntityScopeImpl.findOwner(routineDecl);
        if (null == owner) {
            return result;
        }

        findImplTargets(result, routineDecl.getContainingFile(), owner.getName() + "." + routineDecl.getName(), PascalRoutineImpl.class);

        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T extends PascalNamedElement> void findImplTargets(Collection<PsiElement> result, PsiFile file, String name, Class<T> clazz) {
        PsiElement section = PsiUtil.getModuleImplementationSection(file);
        if ((section != null) && (section.getParent() != null)) {
            for (PsiElement child : section.getChildren()) {
                if (child.getClass() == PasImplDeclSectionImpl.class) {
                    for (PsiElement decl : PsiUtil.findImmChildrenOfAnyType(child, PasDeclSection.class)) {
                        for (PsiElement element : PsiUtil.findImmChildrenOfAnyType(decl, clazz)) {
                            PascalNamedElement routine = (PascalNamedElement) element;
                            if ((routine.getName().equalsIgnoreCase(name))) {
                                result.add(routine);
                            }
                        }
                    }
                }
            }
        }
    }

}