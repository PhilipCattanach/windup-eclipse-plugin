/*******************************************************************************
 * Copyright (c) 2017 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.windup.ui.rules;

import java.rmi.RemoteException;
import java.util.List;

import javax.inject.Inject;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.workbench.modeling.ESelectionService;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.jboss.tools.windup.model.domain.ModelService;
import org.jboss.tools.windup.model.domain.WindupConstants;
import org.jboss.tools.windup.runtime.WindupRmiClient;
import org.jboss.tools.windup.ui.WindupUIPlugin;
import org.jboss.tools.windup.ui.internal.Messages;
import org.jboss.tools.windup.ui.rules.RulesNode.RulesetFileNode;
import org.jboss.tools.windup.windup.CustomRuleProvider;
import org.jboss.windup.tooling.ExecutionBuilder;
import org.jboss.windup.tooling.rules.RuleProviderRegistry;

import com.google.common.collect.Lists;

/**
 * View for displaying Windup rule repositories.
 */
public class RuleRepositoryView extends ViewPart {
	
	public static final String VIEW_ID = "org.jboss.tools.windup.ui.rules.rulesView"; //$NON-NLS-1$
	
	private TreeViewer treeViewer;
	
	@Inject private RuleRepositoryContentProvider contentProvider;
	@Inject private WindupRmiClient windupClient;
	@Inject private ESelectionService selectionService;
	
	@Inject private ModelService modelService;
	@Inject private IEclipseContext context;
	
	private MenuManager menuManager;
	
	@Override
	public void createPartControl(Composite parent) {
		GridLayoutFactory.fillDefaults().spacing(0, 0).applyTo(parent);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(parent);
		try {
			createSystemRulesTreeViewer(parent);
		} catch (RemoteException e1) {
			WindupUIPlugin.log(e1);
			return;
		}
		treeViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				ISelection selection = event.getSelection();
				if (!selection.isEmpty() && selection instanceof ITreeSelection) {
					Object element = ((ITreeSelection)selection).getFirstElement();
					if (element instanceof RulesetFileNode) {
						RulesetFileNode node = (RulesetFileNode)element;
						IFileStore fileStore = EFS.getLocalFileSystem().getStore(new Path(node.getFile().getParent()));
						fileStore = fileStore.getChild(node.getName());
						if (!fileStore.fetchInfo().isDirectory() && fileStore.fetchInfo().exists()) {
						    IWorkbenchPage page=  PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
						    try {
						        //Desktop desktop = Desktop.getDesktop();
						        //desktop.open(new File(node.getRuleProvider().getOrigin()).getParentFile());
						        IDE.openEditorOnFileStore(page, fileStore);
						    } catch (PartInitException e) {
						    	WindupUIPlugin.log(e);
						    	MessageDialog.openError(
										Display.getDefault().getActiveShell(), 
										Messages.openRuleset, 
										Messages.errorOpeningRuleset);
						    }
						}
					}
				}
			}
		});
		super.getSite().setSelectionProvider(treeViewer);
		
		menuManager = new MenuManager();
		menuManager.setRemoveAllWhenShown(true);
		menuManager.addMenuListener(new IMenuListener() {
	        public void menuAboutToShow(IMenuManager manager) {
	            RuleRepositoryView.this.fillContextMenu(manager);
	        }
	    });
	    Menu menu = menuManager.createContextMenu(treeViewer.getControl());
	    treeViewer.getControl().setMenu(menu);
	    getSite().registerContextMenu(menuManager, treeViewer);
	}
	
	private void fillContextMenu(IMenuManager manager) {
		IStructuredSelection selection = (IStructuredSelection)treeViewer.getSelection();
		List<CustomRuleProvider> providers = Lists.newArrayList();
		for (Object selected : selection.toList()) {
			if (!(selected instanceof CustomRuleProvider)) {
				return;
			}
			providers.add((CustomRuleProvider)selected);
		}
		if (!providers.isEmpty()) {
			RemoveRulesetHandler action = ContextInjectionFactory.make(RemoveRulesetHandler.class, context);
			action.setProviders(providers);
			manager.add(action);
		}
	}
	
	@Inject
	@Optional
	private void rulesetAdded(@UIEventTopic(WindupConstants.CUSTOM_RULESET_CHANGED) boolean changed) throws RemoteException {
		if (treeViewer != null && !treeViewer.getTree().isDisposed()) {
			refresh();
		}
	}
	
	private void createSystemRulesTreeViewer(Composite parent) throws RemoteException {
		treeViewer = new TreeViewer(parent, SWT.FULL_SELECTION|SWT.MULTI|SWT.V_SCROLL|SWT.H_SCROLL);
		GridDataFactory.fillDefaults().grab(true, true).applyTo(treeViewer.getControl());
		treeViewer.setContentProvider(contentProvider);
		treeViewer.setLabelProvider(new RuleRepositoryLabelProvider());
		refresh();
	}
	
	@Inject
	@Optional
	private void updateServer(@UIEventTopic(WindupRmiClient.WINDUP_SERVER_STATUS) ExecutionBuilder executionBuilder) throws RemoteException {
		if (treeViewer != null && !treeViewer.getTree().isDisposed()) {
			refresh();
		}
	}
	
	public void refresh() throws RemoteException {
		if (windupClient.getExecutionBuilder() != null) {
			Job fetchJob = new Job(Messages.refreshingRules) { //$NON-NLS-1$
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					RuleProviderRegistry systemRuleProviderRegistry = null;
					if (windupClient.getExecutionBuilder() != null) {
						try {
							systemRuleProviderRegistry = windupClient.getExecutionBuilder().getSystemRuleProviderRegistry();
						} catch (RemoteException e) {
							WindupUIPlugin.log(e);
							Display.getDefault().asyncExec(() -> {
								MessageDialog.openError(
										Display.getDefault().getActiveShell(), 
										Messages.refreshingRules, 
										Messages.refreshingRulesError);
							});
						}
					}
					refreshRulesTree(systemRuleProviderRegistry);
					return Status.OK_STATUS;
				}
			};
			fetchJob.setSystem(true);
			fetchJob.schedule();
		}
		else {
			refreshRulesTree(null);
		}
	}
	
	private void refreshRulesTree(RuleProviderRegistry systemRuleProviderRegistry) {
		Display.getDefault().asyncExec(() -> {
			treeViewer.setInput(RuleRepositoryInput.computeInput(systemRuleProviderRegistry, modelService));
		});
	}

	@Override
	public void setFocus() {
		treeViewer.getTree().setFocus();
	}
}