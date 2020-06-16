/*******************************************************************************
 * Copyright (c) 2016-2020 by G. Weirich
 *
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *
 * Contributors:
 * G. Weirich - initial implementation
 *********************************************************************************/

package ch.elexis.ungrad.lucinda.view;

import static ch.elexis.ungrad.lucinda.Preferences.COLUMN_WIDTHS;
import static ch.elexis.ungrad.lucinda.Preferences.RESTRICT_CURRENT;
import static ch.elexis.ungrad.lucinda.Preferences.SHOW_CONS;
import static ch.elexis.ungrad.lucinda.Preferences.SHOW_INBOX;
import static ch.elexis.ungrad.lucinda.Preferences.SHOW_OMNIVORE;

import java.io.File;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.core.data.events.ElexisEvent;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.ui.actions.GlobalEventDispatcher;
import ch.elexis.core.ui.actions.IActivationListener;
import ch.elexis.core.ui.actions.RestrictedAction;
import ch.elexis.core.ui.events.ElexisUiEventListenerImpl;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.data.Patient;
import ch.elexis.ungrad.lucinda.Activator;
import ch.elexis.ungrad.lucinda.IDocumentHandler;
import ch.elexis.ungrad.lucinda.Preferences;
import ch.elexis.ungrad.lucinda.controller.Controller;
import ch.rgw.tools.StringTool;

public class GlobalView extends ViewPart implements IActivationListener {

	private Controller controller;
	private Action doubleClickAction, filterCurrentPatAction, showInboxAction, aquireAction, rescanAction;

	private final ElexisUiEventListenerImpl eeli_pat = new ElexisUiEventListenerImpl(Patient.class,
			ElexisEvent.EVENT_SELECTED) {

		@Override
		public void runInUi(ElexisEvent ev) {
			controller.changePatient((Patient) ev.getObject());
		}

	};

	public GlobalView() {
		controller = new Controller();

	}

	@Override
	public void createPartControl(Composite parent) {
		/*
		 * If the view is set to show nothing at all (which is the case e.g. on
		 * first launch), set it to show all results.
		 */
		if ((is(SHOW_CONS) || is(SHOW_INBOX) || is(SHOW_OMNIVORE)) == false) {
			save(SHOW_CONS, true);
			save(SHOW_OMNIVORE, true);
			save(SHOW_INBOX, true);
		}

		makeActions();
		controller.createView(parent);
		contributeToActionBars();
		String colWidths = load(COLUMN_WIDTHS);
		controller.setColumnWidths(colWidths);
		GlobalEventDispatcher.addActivationListener(this, this);
	}

	public void visible(final boolean mode) {
		controller.reload();
		if (mode) {
			ElexisEventDispatcher.getInstance().addListeners(eeli_pat);
		} else {
			ElexisEventDispatcher.getInstance().removeListeners(eeli_pat);
			save(COLUMN_WIDTHS, controller.getColumnWidths());
		}
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

	private void contributeToActionBars() {
		IActionBars bars = getViewSite().getActionBars();
		IMenuManager menu = bars.getMenuManager();
		IToolBarManager toolbar = bars.getToolBarManager();
		toolbar.add(filterCurrentPatAction);
		toolbar.add(showInboxAction);

		for (IDocumentHandler dh : Activator.getDefault().getAddons()) {
			IAction toolbarAction = dh.getFilterAction(controller);
			if (toolbarAction != null) {
				toolbar.add(toolbarAction);
			}
			IAction menuAction = dh.getSyncAction(controller);
			if (menuAction != null) {
				menu.add(menuAction);
			}
		}
		toolbar.add(new Separator());
		toolbar.add(aquireAction);
		menu.add(rescanAction);

	}
	
	public void createViewerContextMenu(StructuredViewer viewer,
			final List<IContributionItem> contributionItems){
			MenuManager menuMgr = new MenuManager();
			menuMgr.setRemoveAllWhenShown(true);
			menuMgr.addMenuListener(new IMenuListener() {
				public void menuAboutToShow(IMenuManager manager){
					fillContextMenu(manager, contributionItems);
				}
			});
			Menu menu = menuMgr.createContextMenu(viewer.getControl());
			viewer.getControl().setMenu(menu);
			
			getViewSite().registerContextMenu(menuMgr, viewer);
		}

	private void fillContextMenu(IMenuManager manager, List<IContributionItem> contributionItems){
		manager.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
		for (IContributionItem contributionItem : contributionItems) {
			if (contributionItem == null) {
				manager.add(new Separator());
				continue;
			} else if (contributionItem instanceof ActionContributionItem) {
				ActionContributionItem ac = (ActionContributionItem) contributionItem;
				if (ac.getAction() instanceof RestrictedAction) {
					((RestrictedAction) ac.getAction()).reflectRight();
				}
			}
			contributionItem.update();
			manager.add(contributionItem);
		}
	}
	private void makeActions() {
		rescanAction = new Action("Rescan") {
			{
				setToolTipText("Lucinda Dokumente neu einlesen");
				setImageDescriptor(Images.IMG_REFRESH.getImageDescriptor());
			}
			@Override
			public void run() {
				controller.doRescan();
			}
			
		};

		filterCurrentPatAction = new Action(Messages.GlobalView_actPatient_name, Action.AS_CHECK_BOX) {
			{
				setToolTipText(Messages.GlobalView_actPatient_tooltip);
				setImageDescriptor(Images.IMG_PERSON.getImageDescriptor());
			}

			@Override
			public void run() {
				controller.restrictToCurrentPatient(isChecked());
				save(RESTRICT_CURRENT, isChecked());
			}
		};
		filterCurrentPatAction.setChecked(is(RESTRICT_CURRENT));

		/*
		 * Show results from Lucinda Inbox
		 */
		showInboxAction = new Action(Preferences.INBOX_NAME, Action.AS_CHECK_BOX) {
			{
				setToolTipText(Messages.GlobalView_filterInbox_name);
				setImageDescriptor(Images.IMG_DOCUMENT_TEXT.getImageDescriptor());
			}

			@Override
			public void run() {
				controller.toggleDoctypeFilter(isChecked(), Preferences.INBOX_NAME);
				save(SHOW_INBOX, isChecked());
			}

		};
		showInboxAction.setChecked(is(SHOW_INBOX));

		aquireAction = new Action(Preferences.AQUIRE_ACTION_NAME) {
			{
				setToolTipText("Document von externer Quelle einlesen");
				setImageDescriptor(Images.IMG_IMPORT.getImageDescriptor());
			}

			@Override
			public void run() {
				controller.launchAquireScript(getSite().getShell());
			}
		};
		aquireAction.setEnabled(false);
		String actionScript = Preferences.get(Preferences.AQUIRE_ACTION_SCRIPT, null);
		if (!StringTool.isNothing(actionScript)) {
			File ac = new File(actionScript);
			if (ac.exists() && ac.canExecute()) {
				aquireAction.setEnabled(true);
			}
		}
	}

	@Override
	public void activation(boolean mode) {
	}

	private void save(String name, boolean value) {
		save(name, Boolean.toString(value));
	}

	private void save(String name, String value) {
		Preferences.set(name, value);
	}

	private String load(String name) {
		return Preferences.get(name, Messages.GlobalView_11);
	}

	private boolean is(String name) {
		return Boolean.parseBoolean(Preferences.get(name, Boolean.toString(false)));
	}

}
