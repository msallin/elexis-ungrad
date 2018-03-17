/*******************************************************************************
 * Copyright (c) 2016-2018 by G. Weirich
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

package ch.elexis.ungrad.lucinda.controller;

import java.io.File;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.text.model.Samdas;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.data.Konsultation;
import ch.elexis.data.Patient;
import ch.elexis.ungrad.lucinda.Activator;
import ch.elexis.ungrad.lucinda.Lucinda;
import ch.elexis.ungrad.lucinda.Preferences;
import ch.elexis.ungrad.lucinda.view.GlobalViewPane;
import ch.elexis.ungrad.lucinda.view.Master;
import ch.rgw.io.FileTool;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.TimeTool;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import ch.elexis.omnivore.data.DocHandle;;

/**
 * Controlle for the Lucinda View
 * 
 * @author gerry
 *
 */
public class Controller implements IProgressController {
	GlobalViewPane view;
	ContentProvider cnt;
	TableViewer viewer;
	boolean bRestrictCurrentPatient = false;
	Map<Long, Integer> visibleProcesses = new HashMap<Long, Integer>();
	long actMax;
	int div;
	int actValue;
	private Lucinda lucinda;
	private Set<String> allowed_doctypes = new TreeSet<>();
	private Logger log=LoggerFactory.getLogger(Controller.class);

	
	public Controller() {
		lucinda = new Lucinda();
		bRestrictCurrentPatient = Boolean
				.parseBoolean(Preferences.get(Preferences.RESTRICT_CURRENT, Boolean.toString(false)));
		cnt = new ContentProvider();
		connect();
	}

	private void connect() {
		lucinda.connect(result -> {
			switch (result.getString("status")) {
			case "connected":
				view.setConnected(true);
				break;
			}
		});

	}

	public void reconnect() {
		clear();
		view.setConnected(false);
		lucinda.disconnect();
		connect();
	}

	public Composite createView(Composite parent) {
		if (Preferences.cfg.get(Preferences.SHOW_CONS, true)) {
			allowed_doctypes.add(Preferences.KONSULTATION_NAME);
		}
		if (Preferences.cfg.get(Preferences.SHOW_OMNIVORE, true)) {
			allowed_doctypes.add(Preferences.OMNIVORE_NAME);
		}
		if (Preferences.cfg.get(Preferences.SHOW_INBOX, true)) {
			allowed_doctypes.add(Preferences.INBOX_NAME);
		}

		view = new GlobalViewPane(parent, this);
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {

			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection) event.getSelection();
				if (!sel.isEmpty()) {
					JsonObject doc = (JsonObject) sel.getFirstElement();
					if (doc.getString(Preferences.FLD_LUCINDA_DOCTYPE).equals(Preferences.KONSULTATION_NAME)) {
						Konsultation kons = Konsultation.load(doc.getString(Preferences.FLD_ID));
						if (kons.exists()) {
							ElexisEventDispatcher.fireSelectionEvent(kons);
						}
					}
				}

			}
		});
		changePatient(ElexisEventDispatcher.getSelectedPatient());
		return view;
	}

	public IStructuredContentProvider getContentProvider(TableViewer tv) {
		viewer = tv;
		// tv.addFilter(docFilter);
		return cnt;

	}

	public LabelProvider getLabelProvider() {
		return new LucindaLabelProvider();
	}

	public void clear() {
		viewer.setInput(new JsonArray());
	}

	int cPatWidth = 0;

	public void restrictToCurrentPatient(boolean bRestrict) {
		bRestrictCurrentPatient = bRestrict;
		TableColumn tc = viewer.getTable().getColumn(Master.COLUMN_NAME);
		if (bRestrict) {
			cPatWidth = tc.getWidth();
			tc.setWidth(0);
		} else {
			tc.setWidth(cPatWidth > 0 ? cPatWidth : 100);
		}
		runQuery(view.getSearchField().getText());
	}

	public void reload() {
		runQuery(view.getSearchField().getText());
	}

	/**
	 * Send a query to the lucinda server.
	 * 
	 * @param input
	 *            Query String
	 */
	public void runQuery(String input) {
		lucinda.query(buildQuery(input), result -> {
			if (result.getString("status").equals("ok")) { //$NON-NLS-1$ //$NON-NLS-2$
				JsonArray queryResult = result.getJsonArray("result"); //$NON-NLS-1$

				Display.getDefault().asyncExec(new Runnable() {
					@Override
					public void run() {
						viewer.setInput(queryResult);
					}
				});
			} else {
				Activator.getDefault().addMessage(result);
			}
		});

	}

	/*
	 * Compose a query from the user's supplied query string and the query
	 * refiners, such as doctypes and restrict to Patient.
	 * 
	 * @param input The user supplied query
	 * 
	 * @return the reined query
	 */
	private String buildQuery(String input) {
		StringBuilder q = new StringBuilder();
		if (bRestrictCurrentPatient) {
			Patient pat = ElexisEventDispatcher.getSelectedPatient();
			if (pat != null) {
				q.append("+lastname:").append(pat.getName()).append(" +firstname:") //$NON-NLS-1$//$NON-NLS-2$
						.append(pat.getVorname()).append(" +birthdate:") //$NON-NLS-1$
						.append(new TimeTool(pat.getGeburtsdatum()).toString(TimeTool.DATE_COMPACT));
			}
		}

		if (allowed_doctypes.isEmpty()) {
			q.append(" -lucinda_doctype:*"); //$NON-NLS-1$
		} else {
			q.append(" +("); //$NON-NLS-1$
			for (String doctype : allowed_doctypes) {
				q.append(" lucinda_doctype:").append(doctype); //$NON-NLS-1$
			}
			q.append(")");//$NON-NLS-1$
		}
		if (!input.trim().startsWith("+")) {
			q.append(" +");
		}
		q.append(input); // $NON-NLS-1$
		return q.toString();
	}

	public void loadDocument(final JsonObject doc) {
		String doctype = doc.getString(Preferences.FLD_LUCINDA_DOCTYPE);

		if (doctype.equalsIgnoreCase(Preferences.INBOX_NAME)) {
			lucinda.get(doc.getString(Preferences.FLD_ID), result -> {
				if (result.getString("status").equals("ok")) { //$NON-NLS-1$ //$NON-NLS-2$
					byte[] contents = (byte[]) result.getBinary("result"); //$NON-NLS-1$
					String ext = FileTool.getExtension(doc.getString("url")); //$NON-NLS-1$
					launchViewerForDocument(contents, ext);
				}
			});
		} else if (doctype.equalsIgnoreCase(Preferences.KONSULTATION_NAME)) {
			Konsultation kons = Konsultation.load(doc.getString(Preferences.FLD_ID));
			if (kons.exists()) {
				String entry = kons.getEintrag().getHead();
				if (entry.startsWith("<")) {
					Samdas samdas = new Samdas(entry);
					entry = samdas.getRecordText();
				}
				launchViewerForDocument(entry.getBytes(), "txt"); //$NON-NLS-1$
			} else {
				SWTHelper.showError(Messages.Controller_cons_not_found_caption,
						MessageFormat.format(Messages.Controller_cons_not_found_text, doc.getString("title"))); // $NON-NLS-2$
			}
		} else if (doctype.equalsIgnoreCase(Preferences.OMNIVORE_NAME)) {
			DocHandle dh = DocHandle.load(doc.getString(Preferences.FLD_ID));
			if (dh.exists()) {
				dh.execute();
			} else {
				SWTHelper.showError(Messages.Controller_omnivore_not_found_caption,
						Messages.Controller_omnivore_not_found_text, doc.getString("title")); //$NON-NLS-1$
			}
		} else {

			SWTHelper.showError(Messages.Controller_unknown_type_caption,
					MessageFormat.format(Messages.Controller_unknown_type_text, doctype));
		}

	}

	/*
	 * @Override public void signal(Map<String, Object> message) { switch
	 * ((String) message.get("status")) { //$NON-NLS-1$ case "connected":
	 * //$NON-NLS-1$ view.setConnected(true, lucinda.isBusAPI(),
	 * lucinda.isRestAPI()); break; case "disconnected": //$NON-NLS-1$
	 * view.setConnected(false, false, false); break; } }
	 */

	public void launchViewerForDocument(byte[] cnt, String ext) {
		Display.getDefault().asyncExec(new Runnable() {

			@Override
			public void run() {
				try {

					File temp = File.createTempFile("lucinda_", "." + ext); //$NON-NLS-1$ //$NON-NLS-2$
					temp.deleteOnExit();
					FileTool.writeFile(temp, cnt);
					Program proggie = Program.findProgram(ext);
					if (proggie != null) {
						proggie.execute(temp.getAbsolutePath());
					} else {
						if (Program.launch(temp.getAbsolutePath()) == false) {
							Runtime.getRuntime().exec(temp.getAbsolutePath());
						}
					}

				} catch (Exception ex) {
					ExHandler.handle(ex);
					SWTHelper.showError(Messages.Controller_could_not_launch_file, ex.getMessage());
				}
			}

		});

	}

	/**
	 * Launch a script to acquire a document from tze scanner
	 * @param shell
	 */
	public void launchAquireScript(Shell shell) {
		try {
			Patient pat = ElexisEventDispatcher.getSelectedPatient();
			if (pat == null) {
				throw new Exception("No patient selected");
			} else {
				String[] name = pat.getName().split("[ -]+");
				String[] fname = pat.getVorname().split("[ -]+");
				TimeTool bdate = new TimeTool(pat.getGeburtsdatum());
				StringBuilder sbConcern = new StringBuilder();
				String bdatec = bdate.toString(TimeTool.DATE_COMPACT);
				sbConcern.append(name[0]).append("_").append(fname[0]).append("_").append(bdatec.substring(6, 8)).append(".")
						.append(bdatec.substring(4, 6)).append(".").append(bdatec.substring(0, 4));
				InputDialog id = new InputDialog(shell, "Document title", "Please enter a title for the document", "",
						null);
				if (id.open() == Dialog.OK) {
					String title = id.getValue().replaceAll("[\\/\\:\\- ]", "_");
					Process proc=Runtime.getRuntime().exec(new String[] { Preferences.get(Preferences.AQUIRE_ACTION_SCRIPT, ""),
							sbConcern.toString(), title });
					int result=proc.waitFor();
					if(result!=0){
						log.error("could not launch aquire script");
					}

				}

			}

		} catch (Exception ex) {
			ExHandler.handle(ex);
			SWTHelper.showError("Could not launch script", ex.getMessage());
		}
	}

	/**
	 * Display a progress bar at the bottom of the lucinda view, or add a new
	 * process to an existing process bar. If more than one process wants to
	 * display, the values for all processes are added and the sum is the upper
	 * border of the progress bar.
	 * 
	 * @param maximum
	 *            the value to reach
	 * @return a Handle to use for later addProgrss Calls
	 * @see addProgress
	 */
	public Long initProgress(int maximum) {
		Long proc = System.currentTimeMillis() + new Random().nextLong();
		visibleProcesses.put(proc, maximum);
		long val = 0;
		for (Integer k : visibleProcesses.values()) {
			val += k;
		}
		if (val < Integer.MAX_VALUE) {
			div = 1;
		}
		int amount = (int) (val / div);
		view.initProgress(amount);

		return proc;
	}

	/**
	 * show progress.
	 * 
	 * @param the
	 *            Handle as received from initProgress
	 * @param amount
	 *            the amount of work done since the last call. I the accumulated
	 *            amount of all calls to addProgress is higher than the maximum
	 *            value, the progress bar is hidden.
	 * 
	 */
	public void addProgress(Long handle, int amount) {
		Integer val = visibleProcesses.get(handle);
		val -= amount;
		if (val <= 0) {
			visibleProcesses.remove(handle);
			amount += val;
			if (visibleProcesses.isEmpty()) {
				view.finishProgress();
				actValue = 0;
			}
		} else {
			visibleProcesses.put(handle, val);
			actValue += amount;
			view.showProgress(actValue / div);
		}
	}

	/**
	 * Doctype filter
	 * 
	 * @param bOn
	 *            whether the doctype should be filtered or not
	 * @param doctype
	 *            the doctype to filter (lucinda_doctype)
	 */
	public void toggleDoctypeFilter(boolean bOn, String doctype) {
		if (bOn) {
			allowed_doctypes.add(doctype);
		} else {
			allowed_doctypes.remove(doctype);
		}
		reload();
	}

	public void changePatient(Patient object) {
		if (bRestrictCurrentPatient) {
			Text text = view.getSearchField();
			String q = text.getText();
			if (q.isEmpty()) {
				text.setText("*:*"); //$NON-NLS-1$
			}
			runQuery(q);
		}
	}

	public void setColumnWidths(String widths) {
		TableColumn[] tcs = viewer.getTable().getColumns();
		String[] cw = widths.split(","); //$NON-NLS-1$
		if (cw.length == tcs.length) {
			for (int i = 0; i < cw.length; i++) {
				try {
					int w = Integer.parseInt(cw[i]);
					tcs[i].setWidth(w);
				} catch (NumberFormatException nex) {
					// do nothing
				}
			}
		}
	}

	public String getColumnWidths() {
		TableColumn[] tcs = viewer.getTable().getColumns();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < tcs.length; i++) {
			sb.append(Integer.toString(tcs[i].getWidth())).append(","); //$NON-NLS-1$
		}
		return sb.substring(0, sb.length() - 1);
	}

}
