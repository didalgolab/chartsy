/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ide.engine.actions;

import one.chartsy.base.function.ThrowingRunnable;
import one.chartsy.ide.engine.AutomaticRunner;
import one.chartsy.kernel.runner.EmbeddedLauncher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectManager;
import org.netbeans.modules.gradle.java.api.GradleJavaProject;
import org.netbeans.modules.gradle.java.api.GradleJavaSourceSet;
import org.openide.LifecycleManager;
import org.openide.awt.DropDownButtonFactory;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.RequestProcessor;
import org.openide.util.actions.Presenter;
import reactor.core.scheduler.Schedulers;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class LauncherAction extends AbstractAction implements Presenter.Toolbar, PopupMenuListener {

    private final Logger log = LogManager.getLogger(getClass());

    private final String command;
    private final Lookup lookup;

    public LauncherAction(String command, String name, Icon icon, Lookup lookup) {
        super(name, icon);
        this.command = command;

        if (lookup == null)
            lookup = LastActivatedWindowLookup.INSTANCE;
        this.lookup = lookup;

        putValue(NAME, name);
        if (icon != null)
            putValue(SMALL_ICON, icon);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        LifecycleManager.getDefault().saveAll();
        FileObject fileObject = this.lookup.lookup(FileObject.class);

        if (fileObject == null) {
            Toolkit.getDefaultToolkit().beep();
            log.warn("Can't run LauncherAction due to missing FileObject in global lookup");
            return;
        }

        var projectManager = ProjectManager.getDefault();
        var projectDir = findProjectDirectory(fileObject, projectManager);
        if (projectDir == null) return;

        try {
            Project project = projectManager.findProject(projectDir);
            String className = evaluateClassName(GradleJavaProject.get(project), fileObject);

            EmbeddedLauncher launcher = new EmbeddedLauncher(new AutomaticRunner(LastActivatedWindowLookup.INSTANCE));

            Schedulers.boundedElastic().schedule(
                    ThrowingRunnable.unchecked(
                            () -> launcher.launch(FileUtil.toFile(projectDir).toPath(), className)));

        } catch (ThrowingRunnable.UncheckedException x) {
            Exceptions.printStackTrace(x.getCause());
        } catch (Exception x) {
            Exceptions.printStackTrace(x);
        }
    }

    private FileObject findProjectDirectory(FileObject fileObject, ProjectManager projectManager) {
        var projectDir = fileObject.isFolder()? fileObject : fileObject.getParent();
        while (projectDir != null && !projectManager.isProject(projectDir))
            projectDir = projectDir.getParent();

        if (projectDir == null) {
            Toolkit.getDefaultToolkit().beep();
            log.warn("No project found containing the file `{}`", fileObject);
            return null;
        }
        return projectDir;
    }

    private String evaluateClassName(GradleJavaProject gjp, FileObject fo) {
        String ret = null;
        if ((gjp != null) && (fo != null)) {
            File f = FileUtil.toFile(fo);
            GradleJavaSourceSet sourceSet = gjp.containingSourceSet(f);
            if (sourceSet != null) {
                String relPath = sourceSet.relativePath(f);
                if (relPath != null) {
                    ret = (relPath.lastIndexOf('.') > 0 ?
                            relPath.substring(0, relPath.lastIndexOf('.')) :
                            relPath).replace('/', '.');
                    if (fo.isFolder()) {
                        ret = ret + '*';
                    }
                }
            }
        }
        return ret;
    }

    @Override
    public Component getToolbarPresenter() {
        JPopupMenu menu = new JPopupMenu();
        JButton button = DropDownButtonFactory.createDropDownButton(
                new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)), menu);
        JMenuItem item = new JMenuItem(org.openide.awt.Actions.cutAmpersand((String) getValue("menuText")));
        item.setEnabled(isEnabled());

        addPropertyChangeListener(event -> {
            String propName = event.getPropertyName();
            if ("enabled".equals(propName)) {
                item.setEnabled((Boolean) event.getNewValue());
            } else if ("menuText".equals(propName)) {
                item.setText(org.openide.awt.Actions.cutAmpersand((String) event.getNewValue()));
            }
        });

        menu.add(item);
        item.addActionListener(LauncherAction.this);

        org.openide.awt.Actions.connect(button, this);
        menu.addPopupMenuListener(this);
        return button;
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
        JPopupMenu menu = (JPopupMenu) e.getSource();
        for (Component c : menu.getComponents())
            if (c instanceof JComponent && ((JComponent)c).getClientProperty("aaa") != null)
                menu.remove(c);

        List<LauncherActionItem> actionItems = LauncherActionHistory.getHistoryFor(command);
        if (!actionItems.isEmpty()) {
            JSeparator sep = new JSeparator();
            sep.putClientProperty("aaa", "aaa");
            menu.add(sep);
            for (var actionItem : actionItems) {
                JMenuItem item = new JMenuItem(actionItem.getDisplayName());
                item.putClientProperty("aaa", "aaa");
                menu.add(item);
                item.addActionListener(e1 -> RequestProcessor.getDefault().post(actionItem::repeatExecution));
            }
        }
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) { }

    @Override
    public void popupMenuCanceled(PopupMenuEvent e) { }
}
